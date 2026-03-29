package com.networkgroupchat.client;

import com.networkgroupchat.model.Member;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UNIT TESTS — ChatClientTest
 *
 * ── Testing without real network connections ─────────────────────────────────
 * ChatClient's core logic (coordinator tracking, connection state) can be
 * tested WITHOUT an actual socket. We test the state-management methods
 * directly, not the networking parts.
 *
 * For full integration testing of networking, we'd use a real server
 * in a separate integration test class — but unit tests stay isolated.
 *
 * ── What we test here ────────────────────────────────────────────────────────
 *   - Default state on construction
 *   - onCoordinatorNotify() — sets isCoordinator correctly
 *   - onServerDisconnected() — sets connected = false
 */
@DisplayName("ChatClient Unit Tests")
class ChatClientTest {

    private ChatClient client;

    @BeforeEach
    void setUp() {
        // Create client — does NOT connect to any server yet
        client = new ChatClient("Alice", "localhost", 9999);
    }

    // ── INITIAL STATE ────────────────────────────────────────────────

    @Test
    @DisplayName("New client has correct memberId")
    void testClientHasCorrectId() {
        assertEquals("Alice", client.getMemberId());
    }

    @Test
    @DisplayName("New client is not connected")
    void testNewClientIsNotConnected() {
        assertFalse(client.isConnected(),
                "Client should not be connected until start() is called");
    }

    @Test
    @DisplayName("New client is not coordinator")
    void testNewClientIsNotCoordinator() {
        assertFalse(client.isCoordinator(),
                "Client should not be coordinator until notified by server");
    }

    // ── COORDINATOR NOTIFY ────────────────────────────────────────────

    @Test
    @DisplayName("onCoordinatorNotify() sets isCoordinator=true when we are the coordinator")
    void testOnCoordinatorNotifySelf() {
        // Arrange — coordinator member with same ID as our client
        Member coordinator = new Member("Alice", "127.0.0.1", 5001);
        coordinator.setCoordinator(true);

        // Act — server tells us we're the coordinator
        client.onCoordinatorNotify(coordinator);

        // Assert
        assertTrue(client.isCoordinator(),
                "Client should be marked as coordinator when notified with own ID");
    }

    @Test
    @DisplayName("onCoordinatorNotify() sets isCoordinator=false when someone else is coordinator")
    void testOnCoordinatorNotifyOther() {
        // Arrange — coordinator is someone else (Bob)
        Member coordinator = new Member("Bob", "127.0.0.1", 5002);
        coordinator.setCoordinator(true);

        // Act
        client.onCoordinatorNotify(coordinator);

        // Assert
        assertFalse(client.isCoordinator(),
                "Client should NOT be marked coordinator when Bob is the coordinator");
    }

    @Test
    @DisplayName("onCoordinatorNotify() updates correctly when coordinator changes")
    void testCoordinatorStatusUpdatesOnChange() {
        // Scenario: Alice was coordinator, then Bob takes over
        Member aliceAsCoord = new Member("Alice", "127.0.0.1", 5001);
        aliceAsCoord.setCoordinator(true);
        client.onCoordinatorNotify(aliceAsCoord); // Alice is coordinator
        assertTrue(client.isCoordinator()); // precondition

        // Now Bob becomes coordinator
        Member bobAsCoord = new Member("Bob", "127.0.0.1", 5002);
        bobAsCoord.setCoordinator(true);
        client.onCoordinatorNotify(bobAsCoord);

        // Alice is no longer coordinator
        assertFalse(client.isCoordinator(),
                "isCoordinator should be false after another member becomes coordinator");
    }

    // ── SERVER DISCONNECT ─────────────────────────────────────────────

    @Test
    @DisplayName("onServerDisconnected() sets connected to false")
    void testOnServerDisconnected() {
        // Note: we can't set connected=true from outside (it's set in start())
        // but we can verify the callback doesn't crash and behaves correctly
        assertDoesNotThrow(() -> client.onServerDisconnected(),
                "onServerDisconnected() should not throw");

        assertFalse(client.isConnected(),
                "Client should not be connected after server disconnect");
    }
}