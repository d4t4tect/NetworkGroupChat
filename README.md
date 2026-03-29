# 🌐 NetworkGroupChat

> A real-time group chat application built with Java, demonstrating client-server networking, design patterns, fault tolerance, and concurrent programming.

**COMP1549 — Advanced Programming** | University of Greenwich | 2025/2026

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Design Patterns](#design-patterns)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running the Application](#running-the-application)
- [Commands](#commands)
- [Testing](#testing)
- [Demo Scenario](#demo-scenario)
- [Fault Tolerance](#fault-tolerance)
- [Technologies](#technologies)
- [Authors](#authors)

---

## 📖 Overview

NetworkGroupChat is a fully functional **client-server group chat system** where multiple clients communicate over TCP sockets. The system supports:

- Automatic **coordinator election** — the first member to join becomes the group coordinator
- **Broadcast** and **private** messaging between members
- **Fault tolerance** — if any member (including the coordinator) disconnects, the system recovers automatically
- A **20-second heartbeat** ping system to detect and remove inactive members
- Real-time **member list** showing IDs, IP addresses, and ports

---

## ✨ Features

| Feature | Description |
|---|---|
| 🎯 Coordinator Election | First client becomes coordinator; auto-elects replacement on disconnect |
| 📢 Broadcast Messaging | Send a message to all connected members simultaneously |
| 🔒 Private Messaging | Send a message to one specific member — others see nothing |
| 💓 Heartbeat / Ping | Coordinator pings all members every 20 seconds to detect dropouts |
| 👥 Member List | Any member can request the full list of connected members with IPs and ports |
| 🛡️ Fault Tolerance | Non-coordinator and coordinator disconnects handled gracefully |
| 🚫 Duplicate ID Check | Server rejects connections with already-taken member IDs |
| 🧵 Multi-threaded | Each client handled on its own thread — fully concurrent |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                    SERVER                        │
│                                                  │
│   ChatServer          MemberRegistry             │
│   (accepts TCP)       (Singleton)                │
│        │                    │                    │
│        └──── ClientHandler ─┘  (one per client)  │
│               │                                  │
│         EventManager  (Observer hub)             │
└─────────────────────────────────────────────────┘
              ↑ TCP Sockets ↑
   ┌──────────┐  ┌──────────┐  ┌──────────┐
   │ Client A │  │ Client B │  │ Client C │
   │(Coord.)  │  │          │  │          │
   └──────────┘  └──────────┘  └──────────┘
```

### Threading Model

```
Main Thread → ChatServer accept loop
  └── Thread per client → ClientHandler (read loop + event listener)
       └── Background thread → MessageListener (client side)
            └── Scheduled thread → Ping every 20s (coordinator only)
```

---

## 🎨 Design Patterns

| Pattern | Classes | Purpose |
|---|---|---|
| **Singleton** | `MemberRegistry` | One global registry of all connected members shared across all threads |
| **Observer** | `EventManager`, `EventListener`, `ClientHandler` | Notifies all client handlers when any group event occurs |
| **Strategy** | `MessageStrategy`, `BroadcastStrategy`, `PrivateMessageStrategy` | Swappable message routing algorithms at runtime |
| **Factory** | `MessageFactory` | Creates all Message objects with descriptive named methods |

---

## 📁 Project Structure

```
NetworkGroupChat/
├── src/
│   ├── main/java/com/networkgroupchat/
│   │   ├── Main.java                        # Entry point
│   │   ├── model/
│   │   │   ├── Member.java                  # Represents a connected client
│   │   │   ├── Message.java                 # Core data packet sent over network
│   │   │   └── MessageType.java             # Enum of all message types
│   │   ├── server/
│   │   │   ├── ChatServer.java              # TCP server, accepts connections
│   │   │   ├── ClientHandler.java           # Handles one client per thread
│   │   │   └── MemberRegistry.java          # Singleton member store
│   │   ├── client/
│   │   │   ├── ChatClient.java              # Client app with CLI input loop
│   │   │   └── MessageListener.java         # Background thread reads from server
│   │   ├── pattern/
│   │   │   ├── factory/
│   │   │   │   └── MessageFactory.java      # Factory pattern
│   │   │   ├── observer/
│   │   │   │   ├── EventListener.java       # Observer interface
│   │   │   │   └── EventManager.java        # Observer subject/publisher
│   │   │   └── strategy/
│   │   │       ├── MessageStrategy.java     # Strategy interface
│   │   │       ├── BroadcastStrategy.java   # Sends to all clients
│   │   │       └── PrivateMessageStrategy.java # Sends to one client
│   │   └── util/
│   │       └── Logger.java                  # Centralised timestamped logging
│   └── test/java/com/networkgroupchat/
│       ├── model/
│       │   ├── MemberTest.java              # 9 unit tests
│       │   ├── MessageTest.java             # 7 unit tests
│       │   └── MessageFactoryTest.java      # 11 unit tests
│       ├── server/
│       │   ├── MemberRegistryTest.java      # 14 unit tests (Singleton + election)
│       │   ├── EventManagerTest.java        # 9 unit tests (Observer pattern)
│       │   └── ChatServerIntegrationTest.java # 7 integration tests (real sockets)
│       └── client/
│           └── ChatClientTest.java          # 6 unit tests
├── pom.xml                                  # Maven build + JUnit 5 dependencies
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- **Java JDK 17+** — check with `java -version`
- **Maven 3.8+** — check with `mvn -version`
- OR just **IntelliJ IDEA** — has Maven built in

### Clone the Repository

```bash
git clone https://github.com/d4t4tect
/NetworkGroupChat.git
cd NetworkGroupChat
```

### Build

```bash
mvn clean package -DskipTests
```

Or in IntelliJ: **Maven panel → Lifecycle → double-click `package`**

This produces `target/NetworkGroupChat-1.0-SNAPSHOT.jar`

---

## ▶️ Running the Application

### Start the Server

```bash
java -cp target/classes com.networkgroupchat.Main server
# or on a custom port:
java -cp target/classes com.networkgroupchat.Main server 5001
```

### Connect a Client

```bash
# Same machine (localhost)
java -cp target/classes com.networkgroupchat.Main client Alice

# Different machine on same network (replace with server's IP)
java -cp target/classes com.networkgroupchat.Main client Alice 192.168.1.42 5000

# Custom port
java -cp target/classes com.networkgroupchat.Main client Alice localhost 5001
```

### Using the JAR (share with group members)

```bash
# Server
java -jar NetworkGroupChat-1.0-SNAPSHOT.jar server

# Client
java -jar NetworkGroupChat-1.0-SNAPSHOT.jar client Bob 192.168.1.42 5000
```

---

## 💬 Commands

Once connected as a client, use these commands:

| Command | Description | Example |
|---|---|---|
| `<message>` | Broadcast to everyone | `Hello everyone!` |
| `/msg <id> <text>` | Private message to one member | `/msg Alice Hey!` |
| `/list` | Show all connected members | `/list` |
| `/help` | Show available commands | `/help` |
| `/quit` | Leave the group gracefully | `/quit` |

---

## 🧪 Testing

### Run All Tests

```bash
mvn test
```

Or in IntelliJ: **right-click `src/test/java` → Run 'All Tests'**

### Test Results

```
75 tests passed — 0 failures — 0 errors
```

### Test Classes

| Test Class | Tests | What it covers |
|---|---|---|
| `MemberTest` | 9 | Member construction, coordinator flag, equality, pong tracking |
| `MessageTest` | 7 | Message creation, payload casting, broadcast vs private |
| `MessageFactoryTest` | 11 | All factory methods produce correct message types |
| `MemberRegistryTest` | 14 | Singleton pattern, coordinator election, fault tolerance logic |
| `EventManagerTest` | 9 | Observer subscribe/notify/unsubscribe, crash resilience |
| `ChatClientTest` | 6 | Client state management, coordinator notify callbacks |
| `ChatServerIntegrationTest` | 7 | Real TCP sockets — full end-to-end server + client testing |

---

## 🎬 Demo Scenario

Open 4 terminals and run in this order:

```bash
# Terminal 1 — Server
java -cp target/classes com.networkgroupchat.Main server

# Terminal 2 — Alice (becomes coordinator)
java -cp target/classes com.networkgroupchat.Main client Alice

# Terminal 3 — Bob
java -cp target/classes com.networkgroupchat.Main client Bob

# Terminal 4 — Charlie
java -cp target/classes com.networkgroupchat.Main client Charlie
```

**Then demonstrate:**

```
Alice:   Hello everyone!              → Bob and Charlie both receive it
Bob:     /msg Alice Private message!  → Only Alice receives it, Charlie sees nothing
Charlie: /quit                        → Bob and Alice notified, can still chat
Alice:   /quit                        → Bob automatically elected as new coordinator
Dave connects → told Bob is coordinator → election confirmed ✅
```

---

## 🛡️ Fault Tolerance

| Scenario | How it's handled |
|---|---|
| Non-coordinator disconnects | Removed from registry, remaining members notified, chat continues |
| Coordinator disconnects gracefully | Next member elected, all notified, new coordinator starts pinging |
| Coordinator crashes (no LEAVE sent) | `IOException` caught in `ClientHandler`, same election process triggered |
| Duplicate member ID | Rejected at JOIN handshake, notified, socket closed |
| Listener crashes during event | `EventManager` catches exception, continues notifying other listeners |

---

## 🔧 Technologies

| Technology | Version | Purpose |
|---|---|---|
| Java | 17+ | Core language |
| Maven | 3.8+ | Build tool and dependency management |
| JUnit Jupiter | 5.10.0 | Unit and integration testing |
| Mockito | 5.5.0 | Test doubles and mocking |
| Java Sockets | Built-in | TCP client-server networking |
| ObjectInputStream/OutputStream | Built-in | Serialized object transmission |
| ScheduledExecutorService | Built-in | 20-second ping scheduler |
| ConcurrentHashMap | Built-in | Thread-safe shared state |
| CopyOnWriteArrayList | Built-in | Thread-safe member and observer lists |

---

## 👥 Authors

| Name | Student ID | Contribution |
|---|---|---|
| Member 1 | XXXXXXXXX | XX% |
| Member 2 | XXXXXXXXX | XX% |
| Member 3 | XXXXXXXXX | XX% |
| Member 4 | XXXXXXXXX | XX% |

---

## 📄 License

This project was developed for academic purposes as part of COMP1549 Advanced Programming at the University of Greenwich. Not licensed for commercial use.

---

<div align="center">
  <sub>Built with Java · COMP1549 Advanced Programming · University of Greenwich · 2025/2026</sub>
</div>
