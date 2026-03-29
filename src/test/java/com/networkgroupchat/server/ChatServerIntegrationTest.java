package com.networkgroupchat.server;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.model.Message;
import com.networkgroupchat.model.MessageType;
import com.networkgroupchat.pattern.factory.MessageFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * INTEGRATION TEST — ChatServerIntegrationTest
 *
 * ── What makes this an Integration Test? ────────────────────────────────────
 * Unlike unit tests (which test ONE class in isolation), integration tests
 * verify that MULTIPLE components work correctly TOGETHER.
 *
 * Here we:
 *   1. Start a REAL ChatServer on a background thread
 *   2. Connect REAL Sockets (just like ChatClient would)
 *   3. Send real Message objects over those sockets
 *   4. Verify the server responds correctly
 *
 * ── Port Choice ──────────────────────────────────────────────────────────────
 * We use port 5100 (not 5000) to avoid conflicting with a running server.
 * @AfterEach stops the server so the port is released between tests.
 *
 * ── TestClient helper ────────────────────────────────────────────────────────
 * We define a tiny TestClient inner class that wraps a Socket connection.
 * It handles the stream setup and reading/writing — just enough for testing.
 */
@DisplayName("ChatServer Integration Tests (Real Networking)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatServerIntegrationTest {

    private static final int TEST_PORT = 5100;
    private ChatServer server;
    private Thread serverThread;

    /**
     * @BeforeEach — start a fresh server before each test.
     * We wait 100ms after start() to ensure the ServerSocket is ready.
     */
    @BeforeEach
    void startServer() throws InterruptedException {
        // Clear the singleton registry so each test starts fresh
        MemberRegistry.getInstance().clear();

        server = new ChatServer(TEST_PORT);
        serverThread = new Thread(server, "TestServer");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(100); // Give server time to bind the port
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // ── HELPER: TestClient ───────────────────────────────────────────

    /**
     * Minimal test client that connects to the server and sends/receives messages.
     * We manage it manually here rather than using the full ChatClient class,
     * so tests have precise control over timing.
     */
    static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream out;
        private final ObjectInputStream in;

        TestClient(String id, int port) throws Exception {
            socket = new Socket("localhost", port);
            // CRITICAL: OutputStream first, then InputStream
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Send JOIN
            send(MessageFactory.join(id));
        }

        void send(Message msg) throws IOException {
            out.reset();
            out.writeObject(msg);
            out.flush();
        }

        /**
         * Read ONE message — blocks until a message arrives.
         * Times out after 3 seconds to prevent tests hanging forever.
         */
        Message receive() throws Exception {
            socket.setSoTimeout(3000); // 3 second timeout
            Object obj = in.readObject();
            return (obj instanceof Message) ? (Message) obj : null;
        }

        /** Read messages until we find one with the given type, skipping others */
        Message receiveOfType(MessageType type) throws Exception {
            socket.setSoTimeout(3000);
            for (int i = 0; i < 10; i++) { // max 10 messages to scan
                Message msg = receive();
                if (msg != null && msg.getType() == type) return msg;
            }
            return null; // not found in 10 messages
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    // ── TEST 1: First member becomes coordinator ─────────────────────

    @Test
    @Order(1)
    @DisplayName("First client to connect receives COORDINATOR_NOTIFY")
    void testFirstClientBecomesCoordinator() throws Exception {
        // Arrange + Act
        try (TestClient alice = new TestClient("Alice", TEST_PORT)) {
            // Assert — Alice should receive a COORDINATOR_NOTIFY immediately
            Message coordNotify = alice.receiveOfType(MessageType.COORDINATOR_NOTIFY);

            assertNotNull(coordNotify,
                    "First client should receive COORDINATOR_NOTIFY");
            assertEquals(MessageType.COORDINATOR_NOTIFY, coordNotify.getType());

            // The payload should be Alice herself
            Member coordinator = coordNotify.getMemberPayload();
            assertNotNull(coordinator, "COORDINATOR_NOTIFY should carry a Member payload");
            assertEquals("Alice", coordinator.getId(),
                    "Alice should be identified as the coordinator");
        }
    }

    // ── TEST 2: Second member told about coordinator ──────────────────

    @Test
    @Order(2)
    @DisplayName("Second client is told who the coordinator is on join")
    void testSecondClientToldAboutCoordinator() throws Exception {
        try (TestClient alice = new TestClient("Alice", TEST_PORT);
             TestClient bob   = new TestClient("Bob",   TEST_PORT)) {

            // Skip any initial messages for Alice
            alice.receiveOfType(MessageType.COORDINATOR_NOTIFY);

            // Bob should receive COORDINATOR_NOTIFY telling him Alice is coordinator
            Message bobNotify = bob.receiveOfType(MessageType.COORDINATOR_NOTIFY);

            assertNotNull(bobNotify,
                    "Second client should receive COORDINATOR_NOTIFY about existing coordinator");
            assertEquals("Alice", bobNotify.getMemberPayload().getId(),
                    "Bob should be told that Alice is coordinator");
        }
    }

    // ── TEST 3: MEMBER_LIST response ──────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Client receives a MEMBER_LIST after joining")
    void testClientReceivesMemberListOnJoin() throws Exception {
        try (TestClient alice = new TestClient("Alice", TEST_PORT)) {
            // Drain the COORDINATOR_NOTIFY first
            alice.receiveOfType(MessageType.COORDINATOR_NOTIFY);

            // Next should be a MEMBER_LIST
            Message memberList = alice.receiveOfType(MessageType.MEMBER_LIST);

            assertNotNull(memberList, "Client should receive MEMBER_LIST on joining");
            List<Member> members = memberList.getMemberListPayload();
            assertNotNull(members, "MEMBER_LIST payload should be a List<Member>");
            assertFalse(members.isEmpty(), "Member list should not be empty");
        }
    }

    // ── TEST 4: Duplicate ID rejected ────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Server rejects duplicate member ID with system notification")
    void testDuplicateIdIsRejected() throws Exception {
        try (TestClient alice = new TestClient("Alice", TEST_PORT)) {
            // Drain Alice's initial messages
            alice.receiveOfType(MessageType.COORDINATOR_NOTIFY);

            // Try to connect with the SAME ID "Alice"
            try (TestClient duplicate = new TestClient("Alice", TEST_PORT)) {
                // The duplicate should receive a SYSTEM_NOTIFICATION saying ID is taken
                Message response = duplicate.receiveOfType(MessageType.SYSTEM_NOTIFICATION);

                assertNotNull(response,
                        "Duplicate ID connection should receive a rejection notification");
                assertTrue(response.getContent().contains("already in use") ||
                                response.getContent().contains("taken"),
                        "Rejection message should indicate the ID is taken");
            }
        }
    }

    // ── TEST 5: Broadcast message delivered ──────────────────────────

    @Test
    @Order(5)
    @DisplayName("Broadcast message is received by all connected clients")
    void testBroadcastDeliveredToAllClients() throws Exception {
        try (TestClient alice   = new TestClient("Alice",   TEST_PORT);
             TestClient bob     = new TestClient("Bob",     TEST_PORT);
             TestClient charlie = new TestClient("Charlie", TEST_PORT)) {

            // Wait for all join handshakes to complete
            Thread.sleep(300);

            // Alice sends a broadcast
            alice.send(MessageFactory.broadcast("Alice", "Hello from Alice!"));

            // Bob should receive the broadcast
            Message bobReceived = bob.receiveOfType(MessageType.BROADCAST);
            assertNotNull(bobReceived, "Bob should receive Alice's broadcast");
            assertEquals("Alice", bobReceived.getSenderId());
            assertEquals("Hello from Alice!", bobReceived.getContent());

            // Charlie should also receive it
            Message charlieReceived = charlie.receiveOfType(MessageType.BROADCAST);
            assertNotNull(charlieReceived, "Charlie should receive Alice's broadcast");
        }
    }

    // ── TEST 6: Coordinator election on disconnect ─────────────────────

    @Test
    @Order(6)
    @DisplayName("New coordinator elected when coordinator disconnects")
    void testNewCoordinatorElectedWhenCoordinatorLeaves() throws Exception {
        try (TestClient alice = new TestClient("Alice", TEST_PORT)) {
            // Alice is coordinator — drain her initial messages
            alice.receiveOfType(MessageType.COORDINATOR_NOTIFY);
            alice.receiveOfType(MessageType.MEMBER_LIST);

            try (TestClient bob = new TestClient("Bob", TEST_PORT)) {
                // Drain Bob's initial messages
                bob.receiveOfType(MessageType.COORDINATOR_NOTIFY);
                bob.receiveOfType(MessageType.MEMBER_LIST);

                // Wait for join notification
                Thread.sleep(200);

                // Act — Alice (coordinator) disconnects
                alice.send(MessageFactory.leave("Alice"));
                Thread.sleep(300); // Give server time to handle disconnect and elect

                // Assert — Bob should receive COORDINATOR_NOTIFY saying he's the new coordinator
                Message coordChange = bob.receiveOfType(MessageType.COORDINATOR_NOTIFY);

                assertNotNull(coordChange,
                        "Bob should be notified he is the new coordinator after Alice leaves");
                assertEquals("Bob", coordChange.getMemberPayload().getId(),
                        "Bob should be the new coordinator");
            }
        }
    }

    // ── TEST 7: Non-coordinator leaving doesn't break communication ───

    @Test
    @Order(7)
    @DisplayName("Remaining clients can still communicate after non-coordinator leaves")
    void testCommunicationContinuesAfterNonCoordinatorLeaves() throws Exception {
        try (TestClient alice   = new TestClient("Alice",   TEST_PORT);
             TestClient bob     = new TestClient("Bob",     TEST_PORT);
             TestClient charlie = new TestClient("Charlie", TEST_PORT)) {

            Thread.sleep(300); // let all joins settle

            // Bob leaves
            bob.send(MessageFactory.leave("Bob"));
            Thread.sleep(300);

            // Alice broadcasts — Charlie should still receive it
            alice.send(MessageFactory.broadcast("Alice", "Still here!"));

            Message received = charlie.receiveOfType(MessageType.BROADCAST);
            assertNotNull(received,
                    "Charlie should still receive messages after Bob left");
            assertEquals("Still here!", received.getContent());
        }
    }
}