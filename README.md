Complete, fully functional end-to-end FIX Protocol application with both server and client components. Here's what makes it production-ready:

🎯 Complete Features
=====================

1. FIX Server (Acceptor)

Accepts incoming client connections on port 9878
Handles multiple clients simultaneously
Processes orders with realistic timing (acknowledgment → fill)
Maintains order book
Sends execution reports at each stage

2. FIX Client (Initiator)

Connects to FIX server
Interactive command-line menu
Send new orders
Cancel orders
View order status
Track execution reports

3. Full FIX Protocol Implementation

Logon/Logout sequences
Heartbeat mechanism (30-second intervals)
Sequence number tracking
Checksum calculation
Proper FIX message formatting
Session state management

4. Order Lifecycle
Client → NewOrderSingle → Server
Server → ExecutionReport (New) → Client
Server → ExecutionReport (Filled) → Client

🚀 How to Run
===============

Option 1: Server Only
bash javac FIXProtocolApp.java
java FIXProtocolApp
Select: 1

Option 2: Client Only (connect to existing server)
bash java FIXProtocolApp
Select: 2

Option 3: Both (recommended for testing)
bash java FIXProtocolApp
Select: 3

📝 Usage Example
==================

Once running with option 3:

Server starts and waits for connections
Client connects automatically
Use the menu to:

Send orders (Symbol: AAPL, Side: BUY, Qty: 100, Price: 150.00)
Track execution reports in real-time
Cancel orders
View order book



🔧 Production Enhancements
============================

To make this truly production-ready, you'd want to add:

Database persistence (PostgreSQL/MongoDB)
QuickFIX/J library integration (industry standard)
SSL/TLS encryption
Message replay/recovery
Admin console/monitoring
Risk management rules
Market data integration

This implementation demonstrates all core FIX concepts and can process real orders between client and server!

Thank you - Mufaddal
