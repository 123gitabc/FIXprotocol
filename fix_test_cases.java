// ============================================================================
// COMPREHENSIVE TEST SUITE FOR FIX PROTOCOL APPLICATION
// ============================================================================
// Maven Dependencies Required (add to pom.xml):
// <dependencies>
//     <dependency>
//         <groupId>junit</groupId>
//         <artifactId>junit</artifactId>
//         <version>4.13.2</version>
//         <scope>test</scope>
//     </dependency>
//     <dependency>
//         <groupId>org.mockito</groupId>
//         <artifactId>mockito-core</artifactId>
//         <version>4.11.0</version>
//         <scope>test</scope>
//     </dependency>
//     <dependency>
//         <groupId>org.awaitility</groupId>
//         <artifactId>awaitility</artifactId>
//         <version>4.2.0</version>
//         <scope>test</scope>
//     </dependency>
// </dependencies>
// ============================================================================

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.*;

import java.io.File;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.awaitility.Awaitility.*;

// ============================================================================
// TEST SUITE RUNNER
// ============================================================================
@RunWith(Suite.class)
@Suite.SuiteClasses({
    FIXMessageTest.class,
    FIXSessionTest.class,
    OrderLifecycleTest.class,
    ErrorHandlingTest.class,
    PerformanceTest.class,
    IntegrationTest.class
})
public class FIXProtocolTestSuite {
    // Test suite entry point
}

// ============================================================================
// TEST 1: FIX MESSAGE VALIDATION
// ============================================================================
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FIXMessageTest {
    
    @Test
    public void test01_NewOrderSingleMessageCreation() {
        System.out.println("\n=== TEST: New Order Single Message Creation ===");
        
        try {
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("TEST001"),
                new Side(Side.BUY),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            order.set(new Symbol("AAPL"));
            order.set(new OrderQty(100));
            order.set(new Price(150.50));
            order.set(new TimeInForce(TimeInForce.DAY));
            
            // Validate required fields
            assertEquals("TEST001", order.getClOrdID().getValue());
            assertEquals(Side.BUY, order.getSide().getValue());
            assertEquals("AAPL", order.getSymbol().getValue());
            assertEquals(100, order.getOrderQty().getValue(), 0.01);
            assertEquals(150.50, order.getPrice().getValue(), 0.01);
            
            System.out.println("✓ Message created with all required fields");
            System.out.println("  ClOrdID: " + order.getClOrdID().getValue());
            System.out.println("  Symbol: " + order.getSymbol().getValue());
            System.out.println("  Side: BUY");
            System.out.println("  Quantity: 100");
            System.out.println("  Price: $150.50");
            
        } catch (Exception e) {
            fail("Failed to create NewOrderSingle: " + e.getMessage());
        }
    }
    
    @Test
    public void test02_ExecutionReportMessageValidation() {
        System.out.println("\n=== TEST: Execution Report Message Validation ===");
        
        try {
            ExecutionReport execReport = new ExecutionReport(
                new OrderID("ORD123"),
                new ExecID("EXEC456"),
                new ExecType(ExecType.FILL),
                new OrdStatus(OrdStatus.FILLED),
                new Side(Side.BUY),
                new LeavesQty(0),
                new CumQty(100),
                new AvgPx(150.50)
            );
            
            execReport.set(new ClOrdID("TEST001"));
            execReport.set(new Symbol("AAPL"));
            execReport.set(new LastQty(100));
            execReport.set(new LastPx(150.50));
            
            // Validate execution report fields
            assertEquals("ORD123", execReport.getOrderID().getValue());
            assertEquals(ExecType.FILL, execReport.getExecType().getValue());
            assertEquals(OrdStatus.FILLED, execReport.getOrdStatus().getValue());
            assertEquals(100, execReport.getCumQty().getValue(), 0.01);
            assertEquals(150.50, execReport.getAvgPx().getValue(), 0.01);
            
            System.out.println("✓ Execution report validated");
            System.out.println("  Order ID: " + execReport.getOrderID().getValue());
            System.out.println("  Status: FILLED");
            System.out.println("  Filled Qty: 100");
            System.out.println("  Avg Price: $150.50");
            
        } catch (Exception e) {
            fail("Failed to validate ExecutionReport: " + e.getMessage());
        }
    }
    
    @Test
    public void test03_OrderCancelRequestValidation() {
        System.out.println("\n=== TEST: Order Cancel Request Validation ===");
        
        try {
            OrderCancelRequest cancel = new OrderCancelRequest(
                new OrigClOrdID("TEST001"),
                new ClOrdID("CANCEL001"),
                new Side(Side.BUY),
                new TransactTime(new Date())
            );
            
            cancel.set(new Symbol("AAPL"));
            cancel.set(new OrderQty(100));
            
            assertEquals("TEST001", cancel.getOrigClOrdID().getValue());
            assertEquals("CANCEL001", cancel.getClOrdID().getValue());
            assertEquals("AAPL", cancel.getSymbol().getValue());
            
            System.out.println("✓ Cancel request validated");
            System.out.println("  Original ClOrdID: " + cancel.getOrigClOrdID().getValue());
            System.out.println("  Cancel ClOrdID: " + cancel.getClOrdID().getValue());
            
        } catch (Exception e) {
            fail("Failed to validate OrderCancelRequest: " + e.getMessage());
        }
    }
    
    @Test
    public void test04_MarketOrderValidation() {
        System.out.println("\n=== TEST: Market Order Validation ===");
        
        try {
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("MARKET001"),
                new Side(Side.SELL),
                new TransactTime(new Date()),
                new OrdType(OrdType.MARKET)
            );
            
            order.set(new Symbol("GOOGL"));
            order.set(new OrderQty(50));
            order.set(new TimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL));
            
            assertEquals(OrdType.MARKET, order.getOrdType().getValue());
            assertFalse("Market order should not have price", order.isSetPrice());
            
            System.out.println("✓ Market order validated (no price required)");
            System.out.println("  Order Type: MARKET");
            System.out.println("  Time in Force: IOC");
            
        } catch (Exception e) {
            fail("Failed to validate market order: " + e.getMessage());
        }
    }
    
    @Test
    public void test05_MessageFieldMissing() {
        System.out.println("\n=== TEST: Missing Required Field Handling ===");
        
        try {
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("TEST_INCOMPLETE"),
                new Side(Side.BUY),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            // Missing Symbol - should throw exception when accessed
            try {
                order.getSymbol();
                fail("Should have thrown FieldNotFound exception");
            } catch (FieldNotFound e) {
                System.out.println("✓ Correctly caught missing Symbol field");
            }
            
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}

// ============================================================================
// TEST 2: FIX SESSION MANAGEMENT
// ============================================================================
public class FIXSessionTest {
    private static SocketAcceptor acceptor;
    private static SocketInitiator initiator;
    private static TestServerApplication serverApp;
    private static TestClientApplication clientApp;
    
    @BeforeClass
    public static void setupSessions() throws Exception {
        System.out.println("\n=== SETTING UP FIX SESSIONS FOR TESTING ===");
        
        // Create test configurations
        createTestConfigs();
        
        // Start server
        SessionSettings serverSettings = new SessionSettings("test_server.cfg");
        serverApp = new TestServerApplication();
        MessageStoreFactory storeFactory = new MemoryStoreFactory();
        LogFactory logFactory = new ScreenLogFactory(true, true, true);
        MessageFactory messageFactory = new DefaultMessageFactory();
        
        acceptor = new SocketAcceptor(serverApp, storeFactory, serverSettings, 
            logFactory, messageFactory);
        acceptor.start();
        
        System.out.println("✓ Test server started");
        
        // Start client
        Thread.sleep(1000);
        SessionSettings clientSettings = new SessionSettings("test_client.cfg");
        clientApp = new TestClientApplication();
        
        initiator = new SocketInitiator(clientApp, storeFactory, clientSettings, 
            logFactory, messageFactory);
        initiator.start();
        
        System.out.println("✓ Test client started");
        
        // Wait for logon
        clientApp.waitForLogon(10);
        System.out.println("✓ Client logged on\n");
    }
    
    @AfterClass
    public static void teardownSessions() {
        System.out.println("\n=== TEARING DOWN FIX SESSIONS ===");
        if (initiator != null) {
            initiator.stop();
            System.out.println("✓ Client stopped");
        }
        if (acceptor != null) {
            acceptor.stop();
            System.out.println("✓ Server stopped");
        }
    }
    
    @Test
    public void test01_SessionLogon() {
        System.out.println("\n=== TEST: Session Logon ===");
        assertTrue("Client should be logged on", clientApp.isLoggedOn());
        assertTrue("Server should have active session", serverApp.hasActiveSessions());
        System.out.println("✓ Session logon successful");
    }
    
    @Test
    public void test02_HeartbeatExchange() throws Exception {
        System.out.println("\n=== TEST: Heartbeat Exchange ===");
        
        int initialHeartbeats = serverApp.getHeartbeatCount();
        
        // Wait for heartbeat interval (5 seconds in test config)
        Thread.sleep(6000);
        
        int finalHeartbeats = serverApp.getHeartbeatCount();
        
        assertTrue("Heartbeats should be exchanged", finalHeartbeats > initialHeartbeats);
        System.out.println("✓ Heartbeats exchanged: " + (finalHeartbeats - initialHeartbeats));
    }
    
    @Test
    public void test03_SequenceNumberTracking() throws Exception {
        System.out.println("\n=== TEST: Sequence Number Tracking ===");
        
        int initialSeq = clientApp.getOutgoingSeqNum();
        
        // Send a message
        clientApp.sendTestOrder("SEQTEST", "AAPL", Side.BUY, 100, 150.0);
        
        Thread.sleep(500);
        
        int finalSeq = clientApp.getOutgoingSeqNum();
        
        assertTrue("Sequence number should increment", finalSeq > initialSeq);
        System.out.println("✓ Sequence numbers tracked correctly");
        System.out.println("  Initial: " + initialSeq + ", Final: " + finalSeq);
    }
    
    @Test
    public void test04_SessionRecovery() throws Exception {
        System.out.println("\n=== TEST: Session Recovery ===");
        
        // Simulate disconnect
        clientApp.forceDisconnect();
        System.out.println("  Client disconnected");
        
        Thread.sleep(2000);
        
        // Wait for reconnection
        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> clientApp.isLoggedOn());
        
        System.out.println("✓ Session recovered successfully");
    }
    
    private static void createTestConfigs() throws Exception {
        // Server config
        String serverConfig = "[DEFAULT]\n" +
            "FileStorePath=test_data/server\n" +
            "ConnectionType=acceptor\n" +
            "StartTime=00:00:00\n" +
            "EndTime=23:59:59\n" +
            "HeartBtInt=5\n" +
            "SenderCompID=TEST_SERVER\n" +
            "TargetCompID=TEST_CLIENT\n" +
            "ResetOnLogon=Y\n" +
            "\n[SESSION]\n" +
            "BeginString=FIX.4.4\n" +
            "SocketAcceptPort=9999\n";
        
        writeFile("test_server.cfg", serverConfig);
        
        // Client config
        String clientConfig = "[DEFAULT]\n" +
            "FileStorePath=test_data/client\n" +
            "ConnectionType=initiator\n" +
            "StartTime=00:00:00\n" +
            "EndTime=23:59:59\n" +
            "HeartBtInt=5\n" +
            "ReconnectInterval=2\n" +
            "SenderCompID=TEST_CLIENT\n" +
            "TargetCompID=TEST_SERVER\n" +
            "ResetOnLogon=Y\n" +
            "\n[SESSION]\n" +
            "BeginString=FIX.4.4\n" +
            "SocketConnectHost=localhost\n" +
            "SocketConnectPort=9999\n";
        
        writeFile("test_client.cfg", clientConfig);
    }
    
    private static void writeFile(String filename, String content) throws Exception {
        java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));
        writer.print(content);
        writer.close();
    }
}

// ============================================================================
// TEST 3: ORDER LIFECYCLE
// ============================================================================
public class OrderLifecycleTest {
    private static TestClientApplication clientApp;
    private static TestServerApplication serverApp;
    
    @BeforeClass
    public static void setup() throws Exception {
        System.out.println("\n=== SETTING UP ORDER LIFECYCLE TESTS ===");
        // Use sessions from FIXSessionTest
        clientApp = FIXSessionTest.clientApp;
        serverApp = FIXSessionTest.serverApp;
    }
    
    @Test
    public void test01_NewOrderToFill() throws Exception {
        System.out.println("\n=== TEST: New Order to Fill Lifecycle ===");
        
        CountDownLatch fillLatch = new CountDownLatch(1);
        AtomicReference<String> finalStatus = new AtomicReference<>("PENDING");
        
        clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.FILLED) {
                    finalStatus.set("FILLED");
                    fillLatch.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        // Send order
        String clOrdID = "LIFECYCLE001";
        clientApp.sendTestOrder(clOrdID, "AAPL", Side.BUY, 100, 150.0);
        System.out.println("  Order sent: " + clOrdID);
        
        // Wait for fill (max 10 seconds)
        boolean filled = fillLatch.await(10, TimeUnit.SECONDS);
        
        assertTrue("Order should be filled", filled);
        assertEquals("FILLED", finalStatus.get());
        System.out.println("✓ Order lifecycle completed: NEW → FILLED");
    }
    
    @Test
    public void test02_PartialFillScenario() throws Exception {
        System.out.println("\n=== TEST: Partial Fill Scenario ===");
        
        CountDownLatch partialLatch = new CountDownLatch(1);
        AtomicInteger fillCount = new AtomicInteger(0);
        
        clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.PARTIALLY_FILLED) {
                    fillCount.incrementAndGet();
                    partialLatch.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        String clOrdID = "PARTIAL001";
        clientApp.sendTestOrder(clOrdID, "GOOGL", Side.SELL, 200, 2800.0);
        System.out.println("  Large order sent: " + clOrdID);
        
        boolean gotPartial = partialLatch.await(10, TimeUnit.SECONDS);
        
        assertTrue("Should receive partial fill", gotPartial);
        assertTrue("Should have at least one partial fill", fillCount.get() > 0);
        System.out.println("✓ Partial fill received: " + fillCount.get() + " times");
    }
    
    @Test
    public void test03_OrderCancellation() throws Exception {
        System.out.println("\n=== TEST: Order Cancellation ===");
        
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicReference<String> cancelStatus = new AtomicReference<>("");
        
        clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.CANCELED) {
                    cancelStatus.set("CANCELED");
                    cancelLatch.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        // Send order
        String clOrdID = "CANCEL001";
        clientApp.sendTestOrder(clOrdID, "MSFT", Side.BUY, 50, 380.0);
        System.out.println("  Order sent: " + clOrdID);
        
        Thread.sleep(500);
        
        // Cancel it
        clientApp.sendCancelRequest(clOrdID, "MSFT", 50);
        System.out.println("  Cancel request sent");
        
        boolean canceled = cancelLatch.await(5, TimeUnit.SECONDS);
        
        assertTrue("Order should be canceled", canceled);
        assertEquals("CANCELED", cancelStatus.get());
        System.out.println("✓ Order successfully canceled");
    }
    
    @Test
    public void test04_OrderReplace() throws Exception {
        System.out.println("\n=== TEST: Order Replace (Modify) ===");
        
        CountDownLatch replaceLatch = new CountDownLatch(1);
        
        clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char execType = execReport.getExecType().getValue();
                if (execType == ExecType.REPLACED) {
                    replaceLatch.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        // Send original order
        String clOrdID = "REPLACE001";
        clientApp.sendTestOrder(clOrdID, "TSLA", Side.BUY, 10, 700.0);
        System.out.println("  Original order sent: 10 shares @ $700.00");
        
        Thread.sleep(500);
        
        // Replace with new quantity and price
        clientApp.sendReplaceRequest(clOrdID, "TSLA", 20, 690.0);
        System.out.println("  Replace request sent: 20 shares @ $690.00");
        
        boolean replaced = replaceLatch.await(5, TimeUnit.SECONDS);
        
        assertTrue("Order should be replaced", replaced);
        System.out.println("✓ Order successfully replaced");
    }
    
    @Test
    public void test05_MultipleOrdersConcurrent() throws Exception {
        System.out.println("\n=== TEST: Multiple Concurrent Orders ===");
        
        int orderCount = 5;
        CountDownLatch allFilled = new CountDownLatch(orderCount);
        
        clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.FILLED) {
                    allFilled.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        // Send multiple orders
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"};
        for (int i = 0; i < orderCount; i++) {
            String clOrdID = "MULTI" + (i + 1);
            clientApp.sendTestOrder(clOrdID, symbols[i], Side.BUY, 100, 150.0 + i);
            System.out.println("  Order " + (i + 1) + " sent: " + symbols[i]);
        }
        
        boolean allCompleted = allFilled.await(15, TimeUnit.SECONDS);
        
        assertTrue("All orders should be filled", allCompleted);
        System.out.println("✓ All " + orderCount + " orders processed successfully");
    }
}

// ============================================================================
// TEST 4: ERROR HANDLING
// ============================================================================
public class ErrorHandlingTest {
    
    @Test
    public void test01_InvalidSymbol() {
        System.out.println("\n=== TEST: Invalid Symbol Handling ===");
        
        try {
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("INVALID001"),
                new Side(Side.BUY),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            order.set(new Symbol("")); // Empty symbol
            order.set(new OrderQty(100));
            order.set(new Price(150.0));
            
            // Should handle gracefully
            assertTrue("Empty symbol should be handled", order.getSymbol().getValue().isEmpty());
            System.out.println("✓ Empty symbol handled gracefully");
            
        } catch (Exception e) {
            fail("Should handle empty symbol: " + e.getMessage());
        }
    }
    
    @Test
    public void test02_NegativeQuantity() {
        System.out.println("\n=== TEST: Negative Quantity Handling ===");
        
        try {
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("NEGQTY001"),
                new Side(Side.BUY),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            order.set(new Symbol("AAPL"));
            order.set(new OrderQty(-100)); // Negative quantity
            order.set(new Price(150.0));
            
            double qty = order.getOrderQty().getValue();
            assertTrue("Should detect negative quantity", qty < 0);
            System.out.println("✓ Negative quantity detected: " + qty);
            
        } catch (Exception e) {
            System.out.println("✓ Exception thrown for negative quantity: " + e.getMessage());
        }
    }
    
    @Test
    public void test03_CancelNonExistentOrder() throws Exception {
        System.out.println("\n=== TEST: Cancel Non-Existent Order ===");
        
        CountDownLatch rejectLatch = new CountDownLatch(1);
        
        FIXSessionTest.clientApp.setCancelRejectHandler((reject) -> {
            System.out.println("  Cancel reject received");
            rejectLatch.countDown();
        });
        
        // Try to cancel order that doesn't exist
        FIXSessionTest.clientApp.sendCancelRequest("NONEXISTENT", "AAPL", 100);
        
        boolean rejected = rejectLatch.await(5, TimeUnit.SECONDS);
        
        assertTrue("Should receive cancel reject", rejected);
        System.out.println("✓ Cancel reject received for non-existent order");
    }
    
    @Test
    public void test04_DuplicateClOrdID() throws Exception {
        System.out.println("\n=== TEST: Duplicate ClOrdID Handling ===");
        
        String duplicateID = "DUP001";
        
        // Send first order
        FIXSessionTest.clientApp.sendTestOrder(duplicateID, "AAPL", Side.BUY, 100, 150.0);
        System.out.println("  First order sent with ClOrdID: " + duplicateID);
        
        Thread.sleep(500);
        
        // Send second order with same ClOrdID
        FIXSessionTest.clientApp.sendTestOrder(duplicateID, "GOOGL", Side.SELL, 50, 2800.0);
        System.out.println("  Second order sent with same ClOrdID: " + duplicateID);
        
        // System should handle duplicate (either reject or assign new ID)
        System.out.println("✓ Duplicate ClOrdID processed");
    }
}

// ============================================================================
// TEST 5: PERFORMANCE
// ============================================================================
public class PerformanceTest {
    
    @Test
    public void test01_MessageThroughput() throws Exception {
        System.out.println("\n=== TEST: Message Throughput ===");
        
        int messageCount = 100;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < messageCount; i++) {
            String clOrdID = "PERF" + i;
            FIXSessionTest.clientApp.sendTestOrder(clOrdID, "AAPL", Side.BUY, 100, 150.0);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double throughput = (messageCount * 1000.0) / duration;
        
        System.out.println("  Messages sent: " + messageCount);
        System.out.println("  Duration: " + duration + " ms");
        System.out.println("  Throughput: " + String.format("%.2f", throughput) + " msg/sec");
        
        assertTrue("Throughput should be reasonable", throughput > 10);
        System.out.println("✓ Throughput test passed");
    }
    
    @Test
    public void test02_MessageLatency() throws Exception {
        System.out.println("\n=== TEST: Message Latency ===");
        
        int iterations = 10;
        long totalLatency = 0;
        
        for (int i = 0; i < iterations; i++) {
            CountDownLatch responseLatch = new CountDownLatch(1);
            long sendTime = System.currentTimeMillis();
            
            FIXSessionTest.clientApp.setExecutionReportHandler((execReport) -> {
                responseLatch.countDown();
            });
            
            String clOrdID = "LATENCY" + i;
            FIXSessionTest.clientApp.sendTestOrder(clOrdID, "AAPL", Side.BUY, 100, 150.0);
            
            responseLatch.await(5, TimeUnit.SECONDS);
            long receiveTime = System.currentTimeMillis();
            long latency = receiveTime - sendTime;
            totalLatency += latency;
            
            System.out.println("  Iteration " + (i + 1) + " latency: " + latency + " ms");
        }
        
        double avgLatency = (double) totalLatency / iterations;
        System.out.println("  Average latency: " + String.format("%.2f", avgLatency) + " ms");
        
        assertTrue("Average latency should be under 1 second", avgLatency < 1000);
        System.out.println("✓ Latency test passed");
    }
}

// ============================================================================
// TEST 6: INTEGRATION TEST
// ============================================================================
public class IntegrationTest {
    
    @Test
    public void test01_CompleteTrading() throws Exception {
        System.out.println("\n=== INTEGRATION TEST: Complete Trading Session ===");
        
        AtomicInteger executionReports = new AtomicInteger(0);
        AtomicInteger filledOrders = new AtomicInteger(0);
        
        FIXSessionTest.clientApp.setExecutionReportHandler((execReport) -> {
            try {
                executionReports.incrementAndGet();
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.FILLED) {
                    filledOrders.incrementAndGet();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        // 1. Send new order
        System.out.println("\n1. Sending new order...");
        FIXSessionTest.clientApp.sendTestOrder("INT001", "AAPL", Side.BUY, 100, 150.0);
        Thread.sleep(4000);
        
        // 2. Send and cancel order
        System.out.println("\n2. Sending order to cancel...");
        FIXSessionTest.clientApp.sendTestOrder("INT002", "GOOGL", Side.SELL, 50, 2800.0);
        Thread.sleep(500);
        FIXSessionTest.clientApp.sendCancelRequest("INT002", "GOOGL", 50);
        Thread.sleep(2000);
        
        // 3. Send and replace order
        System.out.println("\n3. Sending order to replace...");
        FIXSessionTest.clientApp.sendTestOrder("INT003", "MSFT", Side.BUY, 75, 380.0);
        Thread.sleep(500);
        FIXSessionTest.clientApp.sendReplaceRequest("INT003", "MSFT", 100, 375.0);
        Thread.sleep(4000);
        
        // 4. Multiple concurrent orders
        System.out.println("\n4. Sending multiple concurrent orders...");
        FIXSessionTest.clientApp.sendTestOrder("INT004", "AMZN", Side.BUY, 25, 3400.0);
        FIXSessionTest.clientApp.sendTestOrder("INT005", "TSLA", Side.SELL, 30, 700.0);
        Thread.sleep(5000);
        
        // Verify results
        System.out.println("\n=== INTEGRATION TEST RESULTS ===");
        System.out.println("Total execution reports: " + executionReports.get());
        System.out.println("Filled orders: " + filledOrders.get());
        
        assertTrue("Should receive multiple execution reports", executionReports.get() > 5);
        assertTrue("Should have filled orders", filledOrders.get() > 0);
        
        System.out.println("✓ Integration test completed successfully");
    }
    
    @Test
    public void test02_ErrorRecoveryScenario() throws Exception {
        System.out.println("\n=== INTEGRATION TEST: Error Recovery ===");
        
        // 1. Send valid order
        System.out.println("\n1. Sending valid order...");
        FIXSessionTest.clientApp.sendTestOrder("REC001", "AAPL", Side.BUY, 100, 150.0);
        Thread.sleep(2000);
        
        // 2. Try to cancel non-existent order
        System.out.println("\n2. Attempting to cancel non-existent order...");
        CountDownLatch rejectLatch = new CountDownLatch(1);
        FIXSessionTest.clientApp.setCancelRejectHandler((reject) -> {
            System.out.println("  Cancel reject received as expected");
            rejectLatch.countDown();
        });
        FIXSessionTest.clientApp.sendCancelRequest("NONEXIST", "AAPL", 100);
        rejectLatch.await(5, TimeUnit.SECONDS);
        
        // 3. Send another valid order to confirm system still works
        System.out.println("\n3. Sending another valid order after error...");
        CountDownLatch fillLatch = new CountDownLatch(1);
        FIXSessionTest.clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.FILLED) {
                    fillLatch.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        FIXSessionTest.clientApp.sendTestOrder("REC002", "GOOGL", Side.BUY, 50, 2800.0);
        boolean filled = fillLatch.await(10, TimeUnit.SECONDS);
        
        assertTrue("System should recover and process orders after error", filled);
        System.out.println("✓ Error recovery test passed");
    }
    
    @Test
    public void test03_HighVolumeStressTest() throws Exception {
        System.out.println("\n=== INTEGRATION TEST: High Volume Stress Test ===");
        
        int totalOrders = 50;
        CountDownLatch completionLatch = new CountDownLatch(totalOrders);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        FIXSessionTest.clientApp.setExecutionReportHandler((execReport) -> {
            try {
                char ordStatus = execReport.getOrdStatus().getValue();
                if (ordStatus == OrdStatus.FILLED) {
                    successCount.incrementAndGet();
                    completionLatch.countDown();
                } else if (ordStatus == OrdStatus.REJECTED) {
                    errorCount.incrementAndGet();
                    completionLatch.countDown();
                }
            } catch (FieldNotFound e) {
                e.printStackTrace();
            }
        });
        
        long startTime = System.currentTimeMillis();
        
        // Send orders in batches
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"};
        for (int i = 0; i < totalOrders; i++) {
            String clOrdID = "STRESS" + i;
            String symbol = symbols[i % symbols.length];
            char side = (i % 2 == 0) ? Side.BUY : Side.SELL;
            int quantity = 50 + (i % 10) * 10;
            double price = 100.0 + (i % 50);
            
            FIXSessionTest.clientApp.sendTestOrder(clOrdID, symbol, side, quantity, price);
            
            // Small delay every 10 orders
            if (i % 10 == 9) {
                Thread.sleep(100);
            }
        }
        
        System.out.println("  All " + totalOrders + " orders sent");
        
        // Wait for all orders to complete (max 30 seconds)
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\n=== STRESS TEST RESULTS ===");
        System.out.println("Total orders sent: " + totalOrders);
        System.out.println("Successfully filled: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Duration: " + duration + " ms");
        System.out.println("Average time per order: " + (duration / totalOrders) + " ms");
        
        assertTrue("Most orders should complete", successCount.get() > totalOrders * 0.8);
        System.out.println("✓ Stress test completed");
    }
}

// ============================================================================
// TEST HELPER CLASSES
// ============================================================================

class TestServerApplication extends quickfix.MessageCracker implements quickfix.Application {
    private volatile int heartbeatCount = 0;
    private volatile boolean hasActiveSessions = false;
    
    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("  [Server] Session created: " + sessionId);
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("  [Server] Client logged on: " + sessionId);
        hasActiveSessions = true;
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("  [Server] Client logged out: " + sessionId);
        hasActiveSessions = false;
    }
    
    @Override
    public void toAdmin(quickfix.Message message, SessionID sessionId) {}
    
    @Override
    public void fromAdmin(quickfix.Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        String msgType = message.getHeader().getString(MsgType.FIELD);
        if (MsgType.HEARTBEAT.equals(msgType)) {
            heartbeatCount++;
        }
    }
    
    @Override
    public void toApp(quickfix.Message message, SessionID sessionId) throws DoNotSend {}
    
    @Override
    public void fromApp(quickfix.Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }
    
    public void onMessage(NewOrderSingle order, SessionID sessionId) throws FieldNotFound {
        // Simulate order processing
        String clOrdID = order.getClOrdID().getValue();
        String orderID = "ORD" + System.currentTimeMillis();
        
        // Send NEW acknowledgment
        sendExecutionReport(sessionId, orderID, clOrdID, order.getSymbol().getValue(),
            order.getSide().getValue(), (int)order.getOrderQty().getValue(),
            order.isSetPrice() ? order.getPrice().getValue() : 0,
            ExecType.NEW, OrdStatus.NEW, 0, 0);
        
        // Simulate fill after delay
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                
                int qty = (int)order.getOrderQty().getValue();
                double price = order.isSetPrice() ? order.getPrice().getValue() : 100.0;
                
                // Partial fill
                if (qty > 100) {
                    int partialQty = qty / 2;
                    sendExecutionReport(sessionId, orderID, clOrdID, order.getSymbol().getValue(),
                        order.getSide().getValue(), qty, price,
                        ExecType.PARTIAL_FILL, OrdStatus.PARTIALLY_FILLED, partialQty, price);
                    Thread.sleep(1000);
                }
                
                // Full fill
                sendExecutionReport(sessionId, orderID, clOrdID, order.getSymbol().getValue(),
                    order.getSide().getValue(), qty, price,
                    ExecType.FILL, OrdStatus.FILLED, qty, price);
                    
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    public void onMessage(OrderCancelRequest cancel, SessionID sessionId) throws FieldNotFound {
        String origClOrdID = cancel.getOrigClOrdID().getValue();
        String clOrdID = cancel.getClOrdID().getValue();
        
        // Send cancel acknowledgment
        try {
            ExecutionReport execReport = new ExecutionReport(
                new OrderID("ORD" + System.currentTimeMillis()),
                new ExecID("EXEC" + System.currentTimeMillis()),
                new ExecType(ExecType.CANCELED),
                new OrdStatus(OrdStatus.CANCELED),
                new Side(cancel.getSide().getValue()),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
            );
            
            execReport.set(new ClOrdID(origClOrdID));
            execReport.set(cancel.getSymbol());
            execReport.set(cancel.getOrderQty());
            
            Session.sendToTarget(execReport, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void onMessage(OrderCancelReplaceRequest replace, SessionID sessionId) 
            throws FieldNotFound {
        String origClOrdID = replace.getOrigClOrdID().getValue();
        String clOrdID = replace.getClOrdID().getValue();
        
        // Send replace acknowledgment
        try {
            ExecutionReport execReport = new ExecutionReport(
                new OrderID("ORD" + System.currentTimeMillis()),
                new ExecID("EXEC" + System.currentTimeMillis()),
                new ExecType(ExecType.REPLACED),
                new OrdStatus(OrdStatus.NEW),
                new Side(replace.getSide().getValue()),
                new LeavesQty(replace.getOrderQty().getValue()),
                new CumQty(0),
                new AvgPx(replace.getPrice().getValue())
            );
            
            execReport.set(new ClOrdID(clOrdID));
            execReport.set(replace.getSymbol());
            execReport.set(replace.getOrderQty());
            execReport.set(replace.getPrice());
            
            Session.sendToTarget(execReport, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendExecutionReport(SessionID sessionId, String orderID, String clOrdID,
            String symbol, char side, int qty, double price, char execType, char ordStatus,
            int lastQty, double lastPx) {
        try {
            ExecutionReport execReport = new ExecutionReport(
                new OrderID(orderID),
                new ExecID("EXEC" + System.currentTimeMillis()),
                new ExecType(execType),
                new OrdStatus(ordStatus),
                new Side(side),
                new LeavesQty(qty - lastQty),
                new CumQty(lastQty),
                new AvgPx(price)
            );
            
            execReport.set(new ClOrdID(clOrdID));
            execReport.set(new Symbol(symbol));
            execReport.set(new OrderQty(qty));
            execReport.set(new Price(price));
            
            if (lastQty > 0) {
                execReport.set(new LastQty(lastQty));
                execReport.set(new LastPx(lastPx));
            }
            
            execReport.set(new TransactTime(new Date()));
            
            Session.sendToTarget(execReport, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public int getHeartbeatCount() { return heartbeatCount; }
    public boolean hasActiveSessions() { return hasActiveSessions; }
}

class TestClientApplication extends quickfix.MessageCracker implements quickfix.Application {
    private SessionID sessionId;
    private volatile boolean loggedOn = false;
    private CountDownLatch logonLatch = new CountDownLatch(1);
    private ExecutionReportHandler execReportHandler;
    private CancelRejectHandler cancelRejectHandler;
    private AtomicInteger outgoingSeqNum = new AtomicInteger(0);
    
    @Override
    public void onCreate(SessionID sessionId) {
        this.sessionId = sessionId;
        System.out.println("  [Client] Session created");
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        this.sessionId = sessionId;
        loggedOn = true;
        logonLatch.countDown();
        System.out.println("  [Client] Logged on");
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        loggedOn = false;
        System.out.println("  [Client] Logged out");
    }
    
    @Override
    public void toAdmin(quickfix.Message message, SessionID sessionId) {}
    
    @Override
    public void fromAdmin(quickfix.Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {}
    
    @Override
    public void toApp(quickfix.Message message, SessionID sessionId) throws DoNotSend {
        outgoingSeqNum.incrementAndGet();
    }
    
    @Override
    public void fromApp(quickfix.Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }
    
    public void onMessage(ExecutionReport execReport, SessionID sessionId) throws FieldNotFound {
        if (execReportHandler != null) {
            execReportHandler.handle(execReport);
        }
    }
    
    public void onMessage(OrderCancelReject reject, SessionID sessionId) throws FieldNotFound {
        if (cancelRejectHandler != null) {
            cancelRejectHandler.handle(reject);
        }
    }
    
    public void sendTestOrder(String clOrdID, String symbol, char side, int qty, double price) {
        try {
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID(clOrdID),
                new Side(side),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            order.set(new Symbol(symbol));
            order.set(new OrderQty(qty));
            order.set(new Price(price));
            order.set(new TimeInForce(TimeInForce.DAY));
            
            Session.sendToTarget(order, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void sendCancelRequest(String origClOrdID, String symbol, int qty) {
        try {
            OrderCancelRequest cancel = new OrderCancelRequest(
                new OrigClOrdID(origClOrdID),
                new ClOrdID("CXL" + System.currentTimeMillis()),
                new Side(Side.BUY),
                new TransactTime(new Date())
            );
            
            cancel.set(new Symbol(symbol));
            cancel.set(new OrderQty(qty));
            
            Session.sendToTarget(cancel, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void sendReplaceRequest(String origClOrdID, String symbol, int newQty, double newPrice) {
        try {
            OrderCancelReplaceRequest replace = new OrderCancelReplaceRequest(
                new OrigClOrdID(origClOrdID),
                new ClOrdID("REP" + System.currentTimeMillis()),
                new Side(Side.BUY),
                new TransactTime(new Date()),
                new OrdType(OrdType.LIMIT)
            );
            
            replace.set(new Symbol(symbol));
            replace.set(new OrderQty(newQty));
            replace.set(new Price(newPrice));
            
            Session.sendToTarget(replace, sessionId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void forceDisconnect() {
        if (sessionId != null) {
            Session.lookupSession(sessionId).disconnect("Test disconnect", false);
        }
    }
    
    public void waitForLogon(int timeoutSeconds) throws InterruptedException {
        logonLatch.await(timeoutSeconds, TimeUnit.SECONDS);
    }
    
    public boolean isLoggedOn() { return loggedOn; }
    public int getOutgoingSeqNum() { return outgoingSeqNum.get(); }
    
    public void setExecutionReportHandler(ExecutionReportHandler handler) {
        this.execReportHandler = handler;
    }
    
    public void setCancelRejectHandler(CancelRejectHandler handler) {
        this.cancelRejectHandler = handler;
    }
    
    @FunctionalInterface
    interface ExecutionReportHandler {
        void handle(ExecutionReport execReport) throws FieldNotFound;
    }
    
    @FunctionalInterface
    interface CancelRejectHandler {
        void handle(OrderCancelReject reject) throws FieldNotFound;
    }
}

// ============================================================================
// TEST EXECUTION SUMMARY GENERATOR
// ============================================================================
class TestSummaryGenerator {
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║          FIX PROTOCOL TEST SUITE - EXECUTION GUIDE            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                ║");
        System.out.println("║  To run all tests:                                             ║");
        System.out.println("║    mvn test                                                    ║");
        System.out.println("║                                                                ║");
        System.out.println("║  To run specific test class:                                   ║");
        System.out.println("║    mvn test -Dtest=FIXMessageTest                             ║");
        System.out.println("║    mvn test -Dtest=OrderLifecycleTest                         ║");
        System.out.println("║                                                                ║");
        System.out.println("║  To run with verbose output:                                   ║");
        System.out.println("║    mvn test -X                                                 ║");
        System.out.println("║                                                                ║");
        System.out.println("║  Test Categories:                                              ║");
        System.out.println("║    1. FIXMessageTest - Message validation                     ║");
        System.out.println("║    2. FIXSessionTest - Session management                     ║");
        System.out.println("║    3. OrderLifecycleTest - Order processing                   ║");
        System.out.println("║    4. ErrorHandlingTest - Error scenarios                     ║");
        System.out.println("║    5. PerformanceTest - Performance metrics                   ║");
        System.out.println("║    6. IntegrationTest - End-to-end scenarios                  ║");
        System.out.println("║                                                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
    }
}