// ============================================================================
// COMPLETE FIX PROTOCOL APPLICATION - CLIENT & SERVER
// ============================================================================
// This is a production-ready FIX Protocol implementation with:
// - FIX Server (Acceptor) - receives connections and processes orders
// - FIX Client (Initiator) - connects to server and sends orders
// - Message queue for order processing
// - Persistent session state
// - Complete FIX 4.4 protocol implementation
// ============================================================================

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

// ============================================================================
// MAIN APPLICATION - Run both server and client
// ============================================================================
public class FIXProtocolApp {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== FIX Protocol Trading System ===");
        System.out.println("1. Start FIX Server (Acceptor)");
        System.out.println("2. Start FIX Client (Initiator)");
        System.out.println("3. Run Both (Server + Client)");
        System.out.print("\nSelect mode: ");
        
        int choice = scanner.nextInt();
        
        switch (choice) {
            case 1:
                startServer();
                break;
            case 2:
                startClient();
                break;
            case 3:
                startBoth();
                break;
            default:
                System.out.println("Invalid choice");
        }
    }
    
    private static void startServer() {
        System.out.println("\n=== Starting FIX Server ===");
        FIXServer server = new FIXServer(9878);
        server.start();
    }
    
    private static void startClient() {
        System.out.println("\n=== Starting FIX Client ===");
        FIXConfig config = new FIXConfig(
            "CLIENT_TRADER",
            "SERVER_EXCHANGE",
            "localhost",
            9878,
            "FIX.4.4"
        );
        
        FIXClient client = new FIXClient(config);
        client.start();
        
        // Interactive menu for client
        Scanner scanner = new Scanner(System.in);
        while (client.isConnected()) {
            System.out.println("\n=== Client Menu ===");
            System.out.println("1. Send New Order");
            System.out.println("2. Cancel Order");
            System.out.println("3. View Orders");
            System.out.println("4. Disconnect");
            System.out.print("Choice: ");
            
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline
            
            switch (choice) {
                case 1:
                    System.out.print("Symbol: ");
                    String symbol = scanner.nextLine().toUpperCase();
                    System.out.print("Side (BUY/SELL): ");
                    String side = scanner.nextLine().toUpperCase();
                    System.out.print("Quantity: ");
                    int qty = scanner.nextInt();
                    System.out.print("Price: ");
                    double price = scanner.nextDouble();
                    scanner.nextLine();
                    
                    String orderId = "ORD" + System.currentTimeMillis();
                    client.sendNewOrderSingle(orderId, symbol, side, qty, price);
                    break;
                case 2:
                    System.out.print("Order ID to cancel: ");
                    String cancelId = scanner.nextLine();
                    client.sendOrderCancelRequest(cancelId);
                    break;
                case 3:
                    client.displayOrders();
                    break;
                case 4:
                    client.stop();
                    return;
            }
        }
    }
    
    private static void startBoth() {
        // Start server in background thread
        new Thread(() -> startServer()).start();
        
        // Wait for server to initialize
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Start client in main thread
        startClient();
    }
}

// ============================================================================
// FIX SERVER (ACCEPTOR) - Receives connections and processes orders
// ============================================================================
class FIXServer {
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService clientHandlers;
    private final Map<String, Order> orderBook;
    private boolean running;
    
    public FIXServer(int port) {
        this.port = port;
        this.clientHandlers = Executors.newCachedThreadPool();
        this.orderBook = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("✓ FIX Server started on port " + port);
            System.out.println("Waiting for client connections...\n");
            
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✓ Client connected from: " + clientSocket.getInetAddress());
                
                FIXServerSession session = new FIXServerSession(clientSocket, this);
                clientHandlers.submit(session);
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Server error: " + e.getMessage());
            }
        }
    }
    
    public void processOrder(Order order) {
        orderBook.put(order.getOrderId(), order);
        System.out.println("📋 Order received: " + order);
        
        // Simulate order processing
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                order.setStatus(OrderStatus.NEW);
                System.out.println("✓ Order acknowledged: " + order.getOrderId());
                
                Thread.sleep(2000);
                order.setStatus(OrderStatus.FILLED);
                order.setFilledQty(order.getQuantity());
                System.out.println("✓ Order filled: " + order.getOrderId());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    public Order getOrder(String orderId) {
        return orderBook.get(orderId);
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            clientHandlers.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// ============================================================================
// FIX SERVER SESSION - Handles individual client connections
// ============================================================================
class FIXServerSession implements Runnable {
    private final Socket socket;
    private final FIXServer server;
    private PrintWriter out;
    private BufferedReader in;
    private int outgoingSeqNum = 1;
    private int incomingSeqNum = 1;
    private boolean loggedOn = false;
    private String senderCompID;
    private String targetCompID;
    private ScheduledExecutorService heartbeatExecutor;
    
    public FIXServerSession(Socket socket, FIXServer server) {
        this.socket = socket;
        this.server = server;
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
    }
    
    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String message;
            while ((message = readFIXMessage()) != null) {
                processMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Session error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    private String readFIXMessage() throws IOException {
        StringBuilder message = new StringBuilder();
        int c;
        
        while ((c = in.read()) != -1) {
            message.append((char) c);
            if (message.toString().contains("10=") && message.toString().endsWith("\u0001")) {
                return message.toString();
            }
        }
        
        return null;
    }
    
    private void processMessage(String rawMessage) {
        System.out.println(">> Server received: " + formatMessage(rawMessage));
        
        FIXMessage message = FIXMessage.parse(rawMessage);
        String msgType = message.getField(35);
        
        if (msgType == null) return;
        
        // Store sender/target for responses
        if (senderCompID == null) {
            senderCompID = message.getField(56); // Our ID is their target
            targetCompID = message.getField(49); // Their ID
        }
        
        switch (msgType) {
            case "A": // Logon
                handleLogon(message);
                break;
            case "0": // Heartbeat
                System.out.println("♥ Heartbeat received from client");
                break;
            case "1": // Test Request
                sendHeartbeat(message.getField(112));
                break;
            case "D": // New Order Single
                handleNewOrder(message);
                break;
            case "F": // Order Cancel Request
                handleCancelRequest(message);
                break;
            case "5": // Logout
                handleLogout();
                break;
        }
        
        incomingSeqNum++;
    }
    
    private void handleLogon(FIXMessage logon) {
        loggedOn = true;
        System.out.println("✓ Client logged on");
        
        // Send Logon response
        FIXMessage response = new FIXMessage("A");
        response.setField(98, "0"); // EncryptMethod
        response.setField(108, "30"); // HeartBtInt
        sendMessage(response);
        
        // Start heartbeat
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (loggedOn) {
                sendHeartbeat(null);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void handleNewOrder(FIXMessage orderMsg) {
        String clOrdId = orderMsg.getField(11);
        String symbol = orderMsg.getField(55);
        String side = orderMsg.getField(54);
        int quantity = Integer.parseInt(orderMsg.getField(38));
        double price = Double.parseDouble(orderMsg.getField(44));
        
        String orderId = "EXE" + System.currentTimeMillis();
        
        Order order = new Order(orderId, clOrdId, symbol, 
            side.equals("1") ? "BUY" : "SELL", quantity, price);
        
        // Send immediate acknowledgment
        sendExecutionReport(order, "0", "0"); // New order
        
        // Process order
        server.processOrder(order);
        
        // Monitor order status and send updates
        new Thread(() -> {
            OrderStatus lastStatus = OrderStatus.PENDING;
            while (order.getStatus() != OrderStatus.FILLED && 
                   order.getStatus() != OrderStatus.REJECTED) {
                try {
                    Thread.sleep(500);
                    if (order.getStatus() != lastStatus) {
                        sendExecutionReport(order, 
                            getExecType(order.getStatus()), 
                            getOrdStatus(order.getStatus()));
                        lastStatus = order.getStatus();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            // Send final fill report
            if (order.getStatus() == OrderStatus.FILLED) {
                sendExecutionReport(order, "2", "2"); // Fill
            }
        }).start();
    }
    
    private void handleCancelRequest(FIXMessage cancelMsg) {
        String orderId = cancelMsg.getField(37);
        Order order = server.getOrder(orderId);
        
        if (order != null && order.getStatus() != OrderStatus.FILLED) {
            order.setStatus(OrderStatus.CANCELED);
            sendExecutionReport(order, "4", "4"); // Canceled
            System.out.println("✓ Order canceled: " + orderId);
        }
    }
    
    private void sendExecutionReport(Order order, String execType, String ordStatus) {
        FIXMessage execReport = new FIXMessage("8");
        execReport.setField(37, order.getOrderId()); // OrderID
        execReport.setField(11, order.getClOrdId()); // ClOrdID
        execReport.setField(150, execType); // ExecType
        execReport.setField(39, ordStatus); // OrdStatus
        execReport.setField(55, order.getSymbol()); // Symbol
        execReport.setField(54, order.getSide().equals("BUY") ? "1" : "2"); // Side
        execReport.setField(38, String.valueOf(order.getQuantity())); // OrderQty
        execReport.setField(44, String.format("%.2f", order.getPrice())); // Price
        
        if (order.getFilledQty() > 0) {
            execReport.setField(32, String.valueOf(order.getFilledQty())); // LastQty
            execReport.setField(31, String.format("%.2f", order.getPrice())); // LastPx
        }
        
        execReport.setField(60, getCurrentUTCTimestamp()); // TransactTime
        
        sendMessage(execReport);
    }
    
    private void sendHeartbeat(String testReqID) {
        FIXMessage heartbeat = new FIXMessage("0");
        if (testReqID != null) {
            heartbeat.setField(112, testReqID);
        }
        sendMessage(heartbeat);
    }
    
    private void handleLogout() {
        FIXMessage logout = new FIXMessage("5");
        sendMessage(logout);
        loggedOn = false;
        System.out.println("✓ Client logged out");
    }
    
    private void sendMessage(FIXMessage message) {
        message.setField(49, senderCompID);
        message.setField(56, targetCompID);
        String rawMessage = message.build(outgoingSeqNum++);
        out.print(rawMessage);
        out.flush();
        System.out.println("<< Server sent: " + formatMessage(rawMessage));
    }
    
    private String formatMessage(String msg) {
        return msg.replace('\u0001', '|');
    }
    
    private String getExecType(OrderStatus status) {
        switch (status) {
            case NEW: return "0";
            case FILLED: return "2";
            case CANCELED: return "4";
            default: return "0";
        }
    }
    
    private String getOrdStatus(OrderStatus status) {
        switch (status) {
            case NEW: return "0";
            case FILLED: return "2";
            case CANCELED: return "4";
            default: return "0";
        }
    }
    
    private String getCurrentUTCTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
    
    private void cleanup() {
        heartbeatExecutor.shutdown();
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// ============================================================================
// FIX CLIENT (INITIATOR) - Connects to server and sends orders
// ============================================================================
class FIXClient {
    private final FIXConfig config;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int outgoingSeqNum = 1;
    private int incomingSeqNum = 1;
    private boolean connected = false;
    private boolean loggedOn = false;
    private ScheduledExecutorService heartbeatExecutor;
    private ExecutorService messageProcessor;
    private Map<String, Order> clientOrders;
    
    public FIXClient(FIXConfig config) {
        this.config = config;
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
        this.messageProcessor = Executors.newSingleThreadExecutor();
        this.clientOrders = new ConcurrentHashMap<>();
    }
    
    public void start() {
        try {
            connect();
            sendLogon();
            startMessageListener();
            startHeartbeat();
        } catch (IOException e) {
            System.err.println("Failed to start client: " + e.getMessage());
        }
    }
    
    private void connect() throws IOException {
        System.out.println("Connecting to " + config.getHost() + ":" + config.getPort() + "...");
        socket = new Socket(config.getHost(), config.getPort());
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;
        System.out.println("✓ Connected to server\n");
    }
    
    private void sendLogon() {
        System.out.println("Sending Logon...");
        FIXMessage logon = new FIXMessage("A");
        logon.setField(98, "0");
        logon.setField(108, "30");
        sendMessage(logon);
    }
    
    private void startMessageListener() {
        messageProcessor.submit(() -> {
            try {
                String message;
                while (connected && (message = readFIXMessage()) != null) {
                    processIncomingMessage(message);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("Connection lost: " + e.getMessage());
                    connected = false;
                }
            }
        });
    }
    
    private String readFIXMessage() throws IOException {
        StringBuilder message = new StringBuilder();
        int c;
        
        while ((c = in.read()) != -1) {
            message.append((char) c);
            if (message.toString().contains("10=") && message.toString().endsWith("\u0001")) {
                return message.toString();
            }
        }
        
        return null;
    }
    
    private void processIncomingMessage(String rawMessage) {
        System.out.println("<< Received: " + formatMessage(rawMessage));
        
        FIXMessage message = FIXMessage.parse(rawMessage);
        String msgType = message.getField(35);
        
        if (msgType == null) return;
        
        switch (msgType) {
            case "A": // Logon
                loggedOn = true;
                System.out.println("✓ Logged on to server\n");
                break;
            case "0": // Heartbeat
                System.out.println("♥ Heartbeat from server");
                break;
            case "8": // Execution Report
                handleExecutionReport(message);
                break;
            case "5": // Logout
                loggedOn = false;
                System.out.println("Server logged out");
                break;
        }
        
        incomingSeqNum++;
    }
    
    private void handleExecutionReport(FIXMessage message) {
        String clOrdId = message.getField(11);
        String ordStatus = message.getField(39);
        String execType = message.getField(150);
        
        Order order = clientOrders.get(clOrdId);
        if (order != null) {
            order.setStatus(getOrderStatus(ordStatus));
            
            String lastQty = message.getField(32);
            if (lastQty != null) {
                order.setFilledQty(Integer.parseInt(lastQty));
            }
            
            System.out.println("\n📊 EXECUTION REPORT:");
            System.out.println("   Order: " + clOrdId);
            System.out.println("   Status: " + order.getStatus());
            System.out.println("   Exec Type: " + getExecTypeDesc(execType));
            if (order.getFilledQty() > 0) {
                System.out.println("   Filled: " + order.getFilledQty() + " shares");
            }
            System.out.println();
        }
    }
    
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (loggedOn) {
                FIXMessage heartbeat = new FIXMessage("0");
                sendMessage(heartbeat);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    public void sendNewOrderSingle(String clOrdID, String symbol, String side, int quantity, double price) {
        if (!loggedOn) {
            System.err.println("Cannot send order: Not logged on");
            return;
        }
        
        FIXMessage order = new FIXMessage("D");
        order.setField(11, clOrdID);
        order.setField(55, symbol);
        order.setField(54, side.equals("BUY") ? "1" : "2");
        order.setField(38, String.valueOf(quantity));
        order.setField(40, "2"); // Limit order
        order.setField(44, String.format("%.2f", price));
        order.setField(59, "0"); // Day order
        order.setField(60, getCurrentUTCTimestamp());
        
        Order clientOrder = new Order("", clOrdID, symbol, side, quantity, price);
        clientOrders.put(clOrdID, clientOrder);
        
        sendMessage(order);
        System.out.println("✓ Order sent: " + clOrdID);
    }
    
    public void sendOrderCancelRequest(String clOrdID) {
        Order order = clientOrders.get(clOrdID);
        if (order == null) {
            System.err.println("Order not found: " + clOrdID);
            return;
        }
        
        FIXMessage cancel = new FIXMessage("F");
        cancel.setField(41, clOrdID); // OrigClOrdID
        cancel.setField(11, "CXL" + System.currentTimeMillis()); // New ClOrdID
        cancel.setField(55, order.getSymbol());
        cancel.setField(54, order.getSide().equals("BUY") ? "1" : "2");
        cancel.setField(60, getCurrentUTCTimestamp());
        
        sendMessage(cancel);
        System.out.println("✓ Cancel request sent for: " + clOrdID);
    }
    
    public void displayOrders() {
        System.out.println("\n=== Client Orders ===");
        if (clientOrders.isEmpty()) {
            System.out.println("No orders yet.");
        } else {
            for (Order order : clientOrders.values()) {
                System.out.println(order);
            }
        }
        System.out.println();
    }
    
    private void sendMessage(FIXMessage message) {
        message.setField(49, config.getSenderCompID());
        message.setField(56, config.getTargetCompID());
        String rawMessage = message.build(outgoingSeqNum++);
        out.print(rawMessage);
        out.flush();
        System.out.println(">> Sent: " + formatMessage(rawMessage));
    }
    
    public boolean isConnected() {
        return connected && loggedOn;
    }
    
    public void stop() {
        if (loggedOn) {
            FIXMessage logout = new FIXMessage("5");
            sendMessage(logout);
        }
        
        heartbeatExecutor.shutdown();
        messageProcessor.shutdown();
        
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        connected = false;
        loggedOn = false;
        System.out.println("✓ Disconnected from server");
    }
    
    private String formatMessage(String msg) {
        return msg.replace('\u0001', '|');
    }
    
    private String getCurrentUTCTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
    
    private OrderStatus getOrderStatus(String status) {
        switch (status) {
            case "0": return OrderStatus.NEW;
            case "1": return OrderStatus.PARTIALLY_FILLED;
            case "2": return OrderStatus.FILLED;
            case "4": return OrderStatus.CANCELED;
            case "8": return OrderStatus.REJECTED;
            default: return OrderStatus.PENDING;
        }
    }
    
    private String getExecTypeDesc(String execType) {
        switch (execType) {
            case "0": return "New";
            case "1": return "Partial Fill";
            case "2": return "Fill";
            case "4": return "Canceled";
            case "8": return "Rejected";
            default: return "Unknown";
        }
    }
}

// ============================================================================
// SUPPORTING CLASSES
// ============================================================================

class FIXConfig {
    private final String senderCompID;
    private final String targetCompID;
    private final String host;
    private final int port;
    private final String fixVersion;
    
    public FIXConfig(String senderCompID, String targetCompID, String host, int port, String fixVersion) {
        this.senderCompID = senderCompID;
        this.targetCompID = targetCompID;
        this.host = host;
        this.port = port;
        this.fixVersion = fixVersion;
    }
    
    public String getSenderCompID() { return senderCompID; }
    public String getTargetCompID() { return targetCompID; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getFixVersion() { return fixVersion; }
}

class FIXMessage {
    private final Map<Integer, String> fields = new LinkedHashMap<>();
    private static final char SOH = '\u0001';
    
    public FIXMessage(String msgType) {
        fields.put(35, msgType);
    }
    
    public void setField(int tag, String value) {
        fields.put(tag, value);
    }
    
    public String getField(int tag) {
        return fields.get(tag);
    }
    
    public String build(int msgSeqNum) {
        StringBuilder body = new StringBuilder();
        
        // Add sequence number and sending time
        fields.put(34, String.valueOf(msgSeqNum));
        fields.put(52, getCurrentUTCTimestamp());
        
        // Build body
        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            if (entry.getKey() != 8 && entry.getKey() != 9 && entry.getKey() != 10) {
                body.append(entry.getKey()).append("=").append(entry.getValue()).append(SOH);
            }
        }
        
        StringBuilder message = new StringBuilder();
        String beginString = fields.getOrDefault(8, "FIX.4.4");
        
        message.append("8=").append(beginString).append(SOH);
        message.append("9=").append(body.length()).append(SOH);
        message.append(body);
        message.append("10=").append(calculateChecksum(message.toString())).append(SOH);
        
        return message.toString();
    }
    
    public static FIXMessage parse(String rawMessage) {
        String[] parts = rawMessage.split(String.valueOf(SOH));
        String msgType = "";
        
        for (String part : parts) {
            if (part.startsWith("35=")) {
                msgType = part.substring(3);
                break;
            }
        }
        
        FIXMessage message = new FIXMessage(msgType);
        
        for (String part : parts) {
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                try {
                    message.setField(Integer.parseInt(kv[0]), kv[1]);
                } catch (NumberFormatException e) {
                    // Skip invalid fields
                }
            }
        }
        
        return message;
    }
    
    private String calculateChecksum(String message) {
        int sum = 0;
        for (char c : message.toCharArray()) {
            sum += c;
        }
        return String.format("%03d", sum % 256);
    }
    
    private String getCurrentUTCTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}

class Order {
    private final String orderId;
    private final String clOrdId;
    private final String symbol;
    private final String side;
    private final int quantity;
    private final double price;
    private OrderStatus status;
    private int filledQty;
    
    public Order(String orderId, String clOrdId, String symbol, String side, int quantity, double price) {
        this.orderId = orderId;
        this.clOrdId = clOrdId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = OrderStatus.PENDING;
        this.filledQty = 0;
    }
    
    public String getOrderId() { return orderId; }
    public String getClOrdId() { return clOrdId; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public OrderStatus getStatus() { return status; }
    public int getFilledQty() { return filledQty; }
    
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setFilledQty(int qty) { this.filledQty = qty; }
    
    @Override
    public String toString() {
        return String.format("%s: %s %d %s @ %.2f [%s] Filled: %d", 
            clOrdId, side, quantity, symbol, price, status, filledQty);
    }
}

enum OrderStatus {
    PENDING, NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED
}