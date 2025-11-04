// ============================================================================
// PRODUCTION FIX PROTOCOL APPLICATION WITH QUICKFIX/J
// ============================================================================
// Maven Dependencies Required (pom.xml):
// <dependencies>
//     <dependency>
//         <groupId>org.quickfixj</groupId>
//         <artifactId>quickfixj-core</artifactId>
//         <version>2.3.1</version>
//     </dependency>
//     <dependency>
//         <groupId>org.quickfixj</groupId>
//         <artifactId>quickfixj-messages-fix44</artifactId>
//         <version>2.3.1</version>
//     </dependency>
//     <dependency>
//         <groupId>org.slf4j</groupId>
//         <artifactId>slf4j-simple</artifactId>
//         <version>1.7.36</version>
//     </dependency>
// </dependencies>
// ============================================================================

import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.*;
import quickfix.fix44.Message;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

// ============================================================================
// MAIN APPLICATION
// ============================================================================
public class FIXProtocolApp {
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  FIX Protocol Trading System (QuickFIX/J) ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        Scanner scanner = new Scanner(System.in);
        System.out.println("1. Start FIX Server (Acceptor)");
        System.out.println("2. Start FIX Client (Initiator)");
        System.out.println("3. Run Both (Recommended for testing)");
        System.out.print("\nSelect mode: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine();
        
        try {
            switch (choice) {
                case 1:
                    startServer();
                    break;
                case 2:
                    startClient(scanner);
                    break;
                case 3:
                    startBoth(scanner);
                    break;
                default:
                    System.out.println("Invalid choice");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void startServer() throws Exception {
        System.out.println("\n=== Starting FIX Server (Acceptor) ===\n");
        
        // Create server configuration file
        createServerConfig();
        
        SessionSettings settings = new SessionSettings("server.cfg");
        FIXServerApplication serverApp = new FIXServerApplication();
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        
        SocketAcceptor acceptor = new SocketAcceptor(
            serverApp, storeFactory, settings, logFactory, messageFactory
        );
        
        acceptor.start();
        System.out.println("✓ FIX Server started and listening...");
        System.out.println("Press Enter to stop server...");
        System.in.read();
        
        acceptor.stop();
        System.out.println("✓ Server stopped");
    }
    
    private static void startClient(Scanner scanner) throws Exception {
        System.out.println("\n=== Starting FIX Client (Initiator) ===\n");
        
        // Create client configuration file
        createClientConfig();
        
        SessionSettings settings = new SessionSettings("client.cfg");
        FIXClientApplication clientApp = new FIXClientApplication();
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        
        SocketInitiator initiator = new SocketInitiator(
            clientApp, storeFactory, settings, logFactory, messageFactory
        );
        
        initiator.start();
        System.out.println("✓ FIX Client started");
        System.out.println("Waiting for connection...\n");
        
        // Wait for logon
        clientApp.waitForLogon();
        
        // Interactive menu
        runClientMenu(scanner, clientApp);
        
        initiator.stop();
        System.out.println("✓ Client stopped");
    }
    
    private static void startBoth(Scanner scanner) throws Exception {
        // Start server in background thread
        Thread serverThread = new Thread(() -> {
            try {
                createServerConfig();
                SessionSettings settings = new SessionSettings("server.cfg");
                FIXServerApplication serverApp = new FIXServerApplication();
                MessageStoreFactory storeFactory = new FileStoreFactory(settings);
                LogFactory logFactory = new FileLogFactory(settings);
                MessageFactory messageFactory = new DefaultMessageFactory();
                
                SocketAcceptor acceptor = new SocketAcceptor(
                    serverApp, storeFactory, settings, logFactory, messageFactory
                );
                
                acceptor.start();
                System.out.println("✓ Server started on port 9878\n");
                
                // Keep server running
                synchronized (acceptor) {
                    acceptor.wait();
                }
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        
        // Wait for server to start
        Thread.sleep(2000);
        
        // Start client
        startClient(scanner);
    }
    
    private static void runClientMenu(Scanner scanner, FIXClientApplication clientApp) {
        while (true) {
            System.out.println("\n╔════════════════════════════════╗");
            System.out.println("║         Client Menu            ║");
            System.out.println("╠════════════════════════════════╣");
            System.out.println("║ 1. Send New Order              ║");
            System.out.println("║ 2. Cancel Order                ║");
            System.out.println("║ 3. Replace Order               ║");
            System.out.println("║ 4. Request Order Status        ║");
            System.out.println("║ 5. View Active Orders          ║");
            System.out.println("║ 6. View Order History          ║");
            System.out.println("║ 7. Disconnect                  ║");
            System.out.println("╚════════════════════════════════╝");
            System.out.print("\nChoice: ");
            
            int choice = scanner.nextInt();
            scanner.nextLine();
            
            try {
                switch (choice) {
                    case 1:
                        sendNewOrder(scanner, clientApp);
                        break;
                    case 2:
                        cancelOrder(scanner, clientApp);
                        break;
                    case 3:
                        replaceOrder(scanner, clientApp);
                        break;
                    case 4:
                        requestOrderStatus(scanner, clientApp);
                        break;
                    case 5:
                        clientApp.displayActiveOrders();
                        break;
                    case 6:
                        clientApp.displayOrderHistory();
                        break;
                    case 7:
                        return;
                    default:
                        System.out.println("Invalid choice");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
    
    private static void sendNewOrder(Scanner scanner, FIXClientApplication clientApp) {
        System.out.println("\n--- New Order Entry ---");
        System.out.print("Symbol (e.g., AAPL): ");
        String symbol = scanner.nextLine().toUpperCase();
        
        System.out.print("Side (1=Buy, 2=Sell): ");
        char side = scanner.nextLine().charAt(0);
        
        System.out.print("Quantity: ");
        int quantity = scanner.nextInt();
        
        System.out.print("Order Type (1=Market, 2=Limit): ");
        char ordType = scanner.next().charAt(0);
        
        double price = 0;
        if (ordType == '2') {
            System.out.print("Limit Price: ");
            price = scanner.nextDouble();
        }
        scanner.nextLine();
        
        System.out.print("Time in Force (0=Day, 1=GTC, 3=IOC, 4=FOK): ");
        char tif = scanner.nextLine().charAt(0);
        
        clientApp.sendNewOrderSingle(symbol, side, quantity, ordType, price, tif);
    }
    
    private static void cancelOrder(Scanner scanner, FIXClientApplication clientApp) {
        System.out.print("\nEnter ClOrdID to cancel: ");
        String clOrdID = scanner.nextLine();
        clientApp.sendOrderCancelRequest(clOrdID);
    }
    
    private static void replaceOrder(Scanner scanner, FIXClientApplication clientApp) {
        System.out.print("\nEnter ClOrdID to replace: ");
        String origClOrdID = scanner.nextLine();
        
        System.out.print("New Quantity: ");
        int newQty = scanner.nextInt();
        
        System.out.print("New Price: ");
        double newPrice = scanner.nextDouble();
        scanner.nextLine();
        
        clientApp.sendOrderCancelReplaceRequest(origClOrdID, newQty, newPrice);
    }
    
    private static void requestOrderStatus(Scanner scanner, FIXClientApplication clientApp) {
        System.out.print("\nEnter ClOrdID: ");
        String clOrdID = scanner.nextLine();
        clientApp.sendOrderStatusRequest(clOrdID);
    }
    
    // Configuration file creators
    private static void createServerConfig() throws IOException {
        String config = "[DEFAULT]\n" +
            "FileStorePath=data/server\n" +
            "FileLogPath=logs/server\n" +
            "ConnectionType=acceptor\n" +
            "StartTime=00:00:00\n" +
            "EndTime=23:59:59\n" +
            "HeartBtInt=30\n" +
            "ValidOrderTypes=1,2,3,4\n" +
            "SenderCompID=SERVER_EXCHANGE\n" +
            "TargetCompID=CLIENT_TRADER\n" +
            "ResetOnLogon=Y\n" +
            "ResetOnLogout=Y\n" +
            "ResetOnDisconnect=Y\n" +
            "\n" +
            "[SESSION]\n" +
            "BeginString=FIX.4.4\n" +
            "SocketAcceptPort=9878\n";
        
        writeConfigFile("server.cfg", config);
    }
    
    private static void createClientConfig() throws IOException {
        String config = "[DEFAULT]\n" +
            "FileStorePath=data/client\n" +
            "FileLogPath=logs/client\n" +
            "ConnectionType=initiator\n" +
            "StartTime=00:00:00\n" +
            "EndTime=23:59:59\n" +
            "HeartBtInt=30\n" +
            "ReconnectInterval=5\n" +
            "SenderCompID=CLIENT_TRADER\n" +
            "TargetCompID=SERVER_EXCHANGE\n" +
            "ResetOnLogon=Y\n" +
            "ResetOnLogout=Y\n" +
            "ResetOnDisconnect=Y\n" +
            "\n" +
            "[SESSION]\n" +
            "BeginString=FIX.4.4\n" +
            "SocketConnectHost=localhost\n" +
            "SocketConnectPort=9878\n";
        
        writeConfigFile("client.cfg", config);
    }
    
    private static void writeConfigFile(String filename, String content) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.print(content);
        }
        System.out.println("✓ Configuration file created: " + filename);
    }
}

// ============================================================================
// FIX SERVER APPLICATION
// ============================================================================
class FIXServerApplication extends MessageCracker implements Application {
    private final Map<String, OrderData> orderBook = new ConcurrentHashMap<>();
    private final Map<SessionID, Boolean> sessions = new ConcurrentHashMap<>();
    
    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("Server: Session created - " + sessionId);
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("✓ Server: Client logged on - " + sessionId);
        sessions.put(sessionId, true);
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("✓ Server: Client logged out - " + sessionId);
        sessions.remove(sessionId);
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // Handle admin messages
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionId) {
        // Handle admin messages from client
    }
    
    @Override
    public void toApp(Message message, SessionID sessionId) {
        System.out.println(">> Server sending: " + message.getHeader().getString(MsgType.FIELD));
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        System.out.println("<< Server received: " + message.getHeader().getString(MsgType.FIELD));
        crack(message, sessionId);
    }
    
    // Handle New Order Single
    public void onMessage(NewOrderSingle order, SessionID sessionId) 
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        
        String clOrdID = order.getClOrdID().getValue();
        Symbol symbol = order.getSymbol();
        Side side = order.getSide();
        OrderQty orderQty = order.getOrderQty();
        OrdType ordType = order.getOrdType();
        
        Price price = new Price(0);
        if (order.isSetPrice()) {
            price = order.getPrice();
        }
        
        System.out.println("\n📋 NEW ORDER RECEIVED:");
        System.out.println("   ClOrdID: " + clOrdID);
        System.out.println("   Symbol: " + symbol.getValue());
        System.out.println("   Side: " + (side.getValue() == Side.BUY ? "BUY" : "SELL"));
        System.out.println("   Quantity: " + orderQty.getValue());
        System.out.println("   Type: " + getOrderTypeDesc(ordType.getValue()));
        if (ordType.getValue() == OrdType.LIMIT) {
            System.out.println("   Price: $" + price.getValue());
        }
        
        // Generate order ID
        String orderID = "ORD" + System.currentTimeMillis();
        
        // Store order
        OrderData orderData = new OrderData(orderID, clOrdID, symbol.getValue(), 
            side.getValue(), (int)orderQty.getValue(), price.getValue());
        orderBook.put(clOrdID, orderData);
        
        // Send acknowledgment (NEW)
        sendExecutionReport(sessionId, orderData, ExecType.NEW, OrdStatus.NEW, 0, 0);
        
        // Simulate order processing
        processOrder(sessionId, orderData, ordType.getValue());
    }
    
    // Handle Order Cancel Request
    public void onMessage(OrderCancelRequest cancelRequest, SessionID sessionId) 
            throws FieldNotFound {
        
        String origClOrdID = cancelRequest.getOrigClOrdID().getValue();
        String clOrdID = cancelRequest.getClOrdID().getValue();
        
        System.out.println("\n🚫 CANCEL REQUEST:");
        System.out.println("   Original ClOrdID: " + origClOrdID);
        
        OrderData order = orderBook.get(origClOrdID);
        if (order != null && !order.isFilled()) {
            order.setCanceled(true);
            sendExecutionReport(sessionId, order, ExecType.CANCELED, OrdStatus.CANCELED, 0, 0);
            System.out.println("   ✓ Order canceled");
        } else {
            sendCancelReject(sessionId, clOrdID, origClOrdID, "Order not found or already filled");
            System.out.println("   ✗ Cancel rejected");
        }
    }
    
    // Handle Order Cancel/Replace Request
    public void onMessage(OrderCancelReplaceRequest replaceRequest, SessionID sessionId) 
            throws FieldNotFound {
        
        String origClOrdID = replaceRequest.getOrigClOrdID().getValue();
        String clOrdID = replaceRequest.getClOrdID().getValue();
        
        System.out.println("\n🔄 REPLACE REQUEST:");
        System.out.println("   Original ClOrdID: " + origClOrdID);
        
        OrderData order = orderBook.get(origClOrdID);
        if (order != null && !order.isFilled()) {
            // Update order
            if (replaceRequest.isSetOrderQty()) {
                order.setQuantity((int)replaceRequest.getOrderQty().getValue());
            }
            if (replaceRequest.isSetPrice()) {
                order.setPrice(replaceRequest.getPrice().getValue());
            }
            
            orderBook.remove(origClOrdID);
            orderBook.put(clOrdID, order);
            order.setClOrdID(clOrdID);
            
            sendExecutionReport(sessionId, order, ExecType.REPLACED, OrdStatus.NEW, 0, 0);
            System.out.println("   ✓ Order replaced");
        } else {
            sendCancelReject(sessionId, clOrdID, origClOrdID, "Order not found or already filled");
            System.out.println("   ✗ Replace rejected");
        }
    }
    
    // Handle Order Status Request
    public void onMessage(OrderStatusRequest statusRequest, SessionID sessionId) 
            throws FieldNotFound {
        
        String clOrdID = statusRequest.getClOrdID().getValue();
        
        System.out.println("\n❓ STATUS REQUEST:");
        System.out.println("   ClOrdID: " + clOrdID);
        
        OrderData order = orderBook.get(clOrdID);
        if (order != null) {
            char execType = order.isFilled() ? ExecType.ORDER_STATUS : ExecType.ORDER_STATUS;
            char ordStatus = order.isFilled() ? OrdStatus.FILLED : 
                           order.isCanceled() ? OrdStatus.CANCELED : OrdStatus.NEW;
            
            sendExecutionReport(sessionId, order, execType, ordStatus, 
                order.getFilledQty(), order.getPrice());
        }
    }
    
    private void processOrder(SessionID sessionId, OrderData order, char ordType) {
        new Thread(() -> {
            try {
                // Simulate market/limit order processing
                if (ordType == OrdType.MARKET) {
                    Thread.sleep(500);
                } else {
                    Thread.sleep(2000);
                }
                
                if (!order.isCanceled()) {
                    // Partial fill (50%)
                    int partialQty = order.getQuantity() / 2;
                    if (partialQty > 0) {
                        order.setFilledQty(partialQty);
                        sendExecutionReport(sessionId, order, ExecType.PARTIAL_FILL, 
                            OrdStatus.PARTIALLY_FILLED, partialQty, order.getPrice());
                        System.out.println("   📊 Partial fill: " + partialQty + " shares");
                        
                        Thread.sleep(1500);
                    }
                    
                    // Full fill
                    if (!order.isCanceled()) {
                        int remainingQty = order.getQuantity() - order.getFilledQty();
                        order.setFilledQty(order.getQuantity());
                        sendExecutionReport(sessionId, order, ExecType.FILL, 
                            OrdStatus.FILLED, remainingQty, order.getPrice());
                        System.out.println("   ✅ Order fully filled");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void sendExecutionReport(SessionID sessionId, OrderData order, 
            char execType, char ordStatus, int lastQty, double lastPx) {
        try {
            ExecutionReport execReport = new ExecutionReport(
                new OrderID(order.getOrderID()),
                new ExecID("EXEC" + System.currentTimeMillis()),
                new ExecType(execType),
                new OrdStatus(ordStatus),
                new Side(order.getSide()),
                new LeavesQty(order.getQuantity() - order.getFilledQty()),
                new CumQty(order.getFilledQty()),
                new AvgPx(order.getPrice())
            );
            
            execReport.set(new ClOrdID(order.getClOrdID()));
            execReport.set(new Symbol(order.getSymbol()));
            execReport.set(new OrderQty(order.getQuantity()));
            execReport.set(new Price(order.getPrice()));
            
            if (lastQty > 0) {
                execReport.set(new LastQty(lastQty));
                execReport.set(new LastPx(lastPx));
            }
            
            execReport.set(new TransactTime(new Date()));
            
            Session.sendToTarget(execReport, sessionId);
        } catch (Exception e) {
            System.err.println("Error sending execution report: " + e.getMessage());
        }
    }
    
    private void sendCancelReject(SessionID sessionId, String clOrdID, 
            String origClOrdID, String reason) {
        try {
            OrderCancelReject reject = new OrderCancelReject(
                new OrderID("UNKNOWN"),
                new ClOrdID(clOrdID),
                new OrigClOrdID(origClOrdID),
                new OrdStatus(OrdStatus.REJECTED),
                new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST)
            );
            
            reject.set(new Text(reason));
            Session.sendToTarget(reject, sessionId);
        } catch (Exception e) {
            System.err.println("Error sending cancel reject: " + e.getMessage());
        }
    }
    
    private String getOrderTypeDesc(char ordType) {
        switch (ordType) {
            case OrdType.MARKET: return "Market";
            case OrdType.LIMIT: return "Limit";
            case OrdType.STOP: return "Stop";
            case OrdType.STOP_LIMIT: return "Stop Limit";
            default: return "Unknown";
        }
    }
}

// ============================================================================
// FIX CLIENT APPLICATION
// ============================================================================
class FIXClientApplication extends MessageCracker implements Application {
    private SessionID sessionId;
    private final CountDownLatch logonLatch = new CountDownLatch(1);
    private final Map<String, ClientOrder> orders = new ConcurrentHashMap<>();
    private final List<String> orderHistory = new ArrayList<>();
    
    @Override
    public void onCreate(SessionID sessionId) {
        this.sessionId = sessionId;
        System.out.println("Client: Session created");
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("✓ Client: Logged on to server\n");
        this.sessionId = sessionId;
        logonLatch.countDown();
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("✓ Client: Logged out from server");
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // Handle admin messages
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionId) {
        // Handle admin messages from server
    }
    
    @Override
    public void toApp(Message message, SessionID sessionId) {
        try {
            System.out.println(">> Client sending: " + message.getHeader().getString(MsgType.FIELD));
        } catch (FieldNotFound e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }
    
    // Handle Execution Report
    public void onMessage(ExecutionReport execReport, SessionID sessionId) 
            throws FieldNotFound {
        
        String clOrdID = execReport.getClOrdID().getValue();
        char execType = execReport.getExecType().getValue();
        char ordStatus = execReport.getOrdStatus().getValue();
        
        System.out.println("\n📊 EXECUTION REPORT:");
        System.out.println("   ClOrdID: " + clOrdID);
        System.out.println("   Exec Type: " + getExecTypeDesc(execType));
        System.out.println("   Order Status: " + getOrdStatusDesc(ordStatus));
        
        if (execReport.isSetOrderID()) {
            System.out.println("   Order ID: " + execReport.getOrderID().getValue());
        }
        
        if (execReport.isSetLastQty() && execReport.getLastQty().getValue() > 0) {
            System.out.println("   Filled Qty: " + (int)execReport.getLastQty().getValue());
            System.out.println("   Fill Price: $" + execReport.getLastPx().getValue());
        }
        
        if (execReport.isSetCumQty()) {
            System.out.println("   Cumulative Qty: " + (int)execReport.getCumQty().getValue());
        }
        
        // Update order tracking
        ClientOrder order = orders.get(clOrdID);
        if (order != null) {
            order.setStatus(getOrdStatusDesc(ordStatus));
            if (execReport.isSetCumQty()) {
                order.setFilledQty((int)execReport.getCumQty().getValue());
            }
            
            String historyEntry = String.format("[%s] %s - %s: %s", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                clOrdID, order.getSymbol(), getOrdStatusDesc(ordStatus));
            orderHistory.add(historyEntry);
        }
    }
    
    // Handle Order Cancel Reject
    public void onMessage(OrderCancelReject reject, SessionID sessionId) 
            throws FieldNotFound {
        
        System.out.println("\n❌ CANCEL REJECTED:");
        System.out.println("   ClOrdID: " + reject.getClOrdID().getValue());
        if (reject.isSetText()) {
            System.out.println("   Reason: " + reject.getText().getValue());
        }
    }
    
    public void waitForLogon() {
        try {
            logonLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void sendNewOrderSingle(String symbol, char side, int quantity, 
            char ordType, double price, char timeInForce) {
        try {
            String clOrdID = "CLI" + System.currentTimeMillis();
            
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID(clOrdID),
                new Side(side),
                new TransactTime(new Date()),
                new OrdType(ordType)
            );
            
            order.set(new Symbol(symbol));
            order.set(new OrderQty(quantity));
            order.set(new TimeInForce(timeInForce));
            
            if (ordType == OrdType.LIMIT) {
                order.set(new Price(price));
            }
            
            Session.sendToTarget(order, sessionId);
            
            // Track order
            ClientOrder clientOrder = new ClientOrder(clOrdID, symbol, 
                side == Side.BUY ? "BUY" : "SELL", quantity, price);
            orders.put(clOrdID, clientOrder);
            
            System.out.println("\n✓ Order sent: " + clOrdID);
        } catch (Exception e) {
            System.err.println("Error sending order: " + e.getMessage());
        }
    }
    
    public void sendOrderCancelRequest(String origClOrdID) {
        try {
            String clOrdID = "CXL" + System.currentTimeMillis();
            
            OrderCancelRequest cancel = new OrderCancelRequest(
                new OrigClOrdID(origClOrdID),
                new ClOrdID(clOrdID),
                new Side(Side.BUY),
                new TransactTime(new Date())
            );
            
            ClientOrder order = orders.get(origClOrdID);
            if (order != null) {
                cancel.set(new Symbol(order.getSymbol()));
                cancel.set(new OrderQty(order.getQuantity()));
            }
            
            Session.sendToTarget(cancel, sessionId);
            System.out.println("\n✓ Cancel request sent");
        } catch (Exception e) {
            System.err.println("Error sending cancel: " + e.getMessage());
        }
    }
    
    public void sendOrderCancelReplaceRequest(String origClOrdID, int newQty, double newPrice) {
        try {
            String clOrdID = "REP" + System.currentTimeMillis();
            
            ClientOrder order = orders.get(origClOrdID);
            if (order == null) {
                System.err.println("Order not found: " + origClOrdID);
                return;
            }
            
            OrderCancelReplaceRequest replace = new OrderCancelReplaceRequest(
                new OrigClOrdID(origClOrdID),
                new ClOrdID(clOrdID),
                new Side(order.getSide().equals("BUY") ? Side.BUY : Side.SELL),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            replace.set(new Symbol(order.getSymbol()));
            replace.set(new OrderQty(newQty));
            replace.set(new Price(newPrice));
            
            Session.sendToTarget(replace, sessionId);
            System.out.println("\n✓ Replace request sent");
        } catch (Exception e) {
            System.err.println("Error sending replace: " + e.getMessage());
        }
    }
    
    public void sendOrderStatusRequest(String clOrdID) {
        try {
            ClientOrder order = orders.get(clOrdID);
            if (order == null) {
                System.err.println("Order not found: " + clOrdID);
                return;
            }
            
            OrderStatusRequest statusRequest = new OrderStatusRequest(
                new ClOrdID(clOrdID),
                new Side(order.getSide().equals("BUY") ? Side.BUY : Side.SELL)
            );
            
            statusRequest.set(new Symbol(order.getSymbol()));
            
            Session.sendToTarget(statusRequest, sessionId);
            System.out.println("\n✓ Status request sent");
        } catch (Exception e) {
            System.err.println("Error sending status request: " + e.getMessage());
        }
    }
    
    public void displayActiveOrders() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        ACTIVE ORDERS                               ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════╣");
        
        boolean hasActive = false;
        for (ClientOrder order : orders.values()) {
            if (!order.getStatus().equals("FILLED") && !order.getStatus().equals("CANCELED")) {
                hasActive = true;
                System.out.printf("║ %-15s | %-6s | %-4s | %5d @ $%-8.2f | %-12s ║%n",
                    order.getClOrdID(), order.getSymbol(), order.getSide(),
                    order.getQuantity(), order.getPrice(), order.getStatus());
            }
        }
        
        if (!hasActive) {
            System.out.println("║                       No active orders                             ║");
        }
        
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
    }
    
    public void displayOrderHistory() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        ORDER HISTORY                               ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════╣");
        
        if (orderHistory.isEmpty()) {
            System.out.println("║                       No order history                             ║");
        } else {
            for (String entry : orderHistory) {
                System.out.printf("║ %-66s ║%n", entry);
            }
        }
        
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
    }
    
    private String getExecTypeDesc(char execType) {
        switch (execType) {
            case ExecType.NEW: return "New";
            case ExecType.PARTIAL_FILL: return "Partial Fill";
            case ExecType.FILL: return "Fill";
            case ExecType.CANCELED: return "Canceled";
            case ExecType.REPLACED: return "Replaced";
            case ExecType.REJECTED: return "Rejected";
            case ExecType.ORDER_STATUS: return "Order Status";
            default: return "Unknown";
        }
    }
    
    private String getOrdStatusDesc(char ordStatus) {
        switch (ordStatus) {
            case OrdStatus.NEW: return "NEW";
            case OrdStatus.PARTIALLY_FILLED: return "PARTIAL";
            case OrdStatus.FILLED: return "FILLED";
            case OrdStatus.CANCELED: return "CANCELED";
            case OrdStatus.REJECTED: return "REJECTED";
            case OrdStatus.PENDING_CANCEL: return "PENDING_CANCEL";
            case OrdStatus.PENDING_REPLACE: return "PENDING_REPLACE";
            default: return "UNKNOWN";
        }
    }
}

// ============================================================================
// SUPPORTING CLASSES
// ============================================================================

class OrderData {
    private final String orderID;
    private String clOrdID;
    private final String symbol;
    private final char side;
    private int quantity;
    private double price;
    private int filledQty;
    private boolean canceled;
    
    public OrderData(String orderID, String clOrdID, String symbol, char side, int quantity, double price) {
        this.orderID = orderID;
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.filledQty = 0;
        this.canceled = false;
    }
    
    public String getOrderID() { return orderID; }
    public String getClOrdID() { return clOrdID; }
    public String getSymbol() { return symbol; }
    public char getSide() { return side; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public int getFilledQty() { return filledQty; }
    public boolean isCanceled() { return canceled; }
    public boolean isFilled() { return filledQty >= quantity; }
    
    public void setClOrdID(String clOrdID) { this.clOrdID = clOrdID; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(double price) { this.price = price; }
    public void setFilledQty(int filledQty) { this.filledQty = filledQty; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
}

class ClientOrder {
    private final String clOrdID;
    private final String symbol;
    private final String side;
    private final int quantity;
    private final double price;
    private String status;
    private int filledQty;
    
    public ClientOrder(String clOrdID, String symbol, String side, int quantity, double price) {
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = "PENDING";
        this.filledQty = 0;
    }
    
    public String getClOrdID() { return clOrdID; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public String getStatus() { return status; }
    public int getFilledQty() { return filledQty; }
    
    public void setStatus(String status) { this.status = status; }
    public void setFilledQty(int filledQty) { this.filledQty = filledQty; }
}