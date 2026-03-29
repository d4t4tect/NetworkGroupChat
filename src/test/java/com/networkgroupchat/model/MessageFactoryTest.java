package com.networkgroupchat.model;

import com.networkgroupchat.pattern.factory.MessageFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

/**
 * UNIT TESTS — MessageFactoryTest
 *
 * ── What we're testing ───────────────────────────────────────────────────────
 * The Factory pattern must produce correctly configured Message objects.
 * Each factory method should:
 *   - Set the right MessageType
 *   - Set the right sender/receiver
 *   - Set meaningful content
 *   - Set the correct payload (for MEMBER_LIST, COORDINATOR_NOTIFY)
 *
 * ── Why test the factory? ────────────────────────────────────────────────────
 * The factory is used EVERYWHERE. If MessageFactory.broadcast() accidentally
 * creates a PRIVATE message, the entire broadcast system breaks.
 * Testing it early catches these mistakes immediately.
 */
@DisplayName("MessageFactory Tests (Factory Pattern)")
class MessageFactoryTest {

    // ── BROADCAST ────────────────────────────────────────────────────

    @Test
    @DisplayName("broadcast() creates a BROADCAST message with correct fields")
    void testBroadcastMessage() {
        Message msg = MessageFactory.broadcast("Alice", "Hello everyone!");

        assertEquals(MessageType.BROADCAST, msg.getType());
        assertEquals("Alice", msg.getSenderId());
        assertEquals("Hello everyone!", msg.getContent());
        assertNull(msg.getReceiverId(), "Broadcast should have no receiver");
        assertTrue(msg.isBroadcast(), "isBroadcast() should return true");
    }

    // ── PRIVATE MESSAGE ──────────────────────────────────────────────

    @Test
    @DisplayName("privateMessage() creates a PRIVATE message with correct receiver")
    void testPrivateMessage() {
        Message msg = MessageFactory.privateMessage("Alice", "Bob", "Hey Bob!");

        assertEquals(MessageType.PRIVATE, msg.getType());
        assertEquals("Alice", msg.getSenderId());
        assertEquals("Bob", msg.getReceiverId());
        assertEquals("Hey Bob!", msg.getContent());
        assertFalse(msg.isBroadcast(), "Private message should not be broadcast");
    }

    // ── JOIN ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("join() creates a JOIN message with the member's ID as sender")
    void testJoinMessage() {
        Message msg = MessageFactory.join("Alice");

        assertEquals(MessageType.JOIN, msg.getType());
        assertEquals("Alice", msg.getSenderId());
        assertNotNull(msg.getContent(), "JOIN message should have content");
    }

    // ── LEAVE ────────────────────────────────────────────────────────

    @Test
    @DisplayName("leave() creates a LEAVE message")
    void testLeaveMessage() {
        Message msg = MessageFactory.leave("Bob");

        assertEquals(MessageType.LEAVE, msg.getType());
        assertEquals("Bob", msg.getSenderId());
    }

    // ── PING / PONG ──────────────────────────────────────────────────

    @Test
    @DisplayName("ping() creates a PING message from the coordinator")
    void testPingMessage() {
        Message msg = MessageFactory.ping("Alice");

        assertEquals(MessageType.PING, msg.getType());
        assertEquals("Alice", msg.getSenderId());
    }

    @Test
    @DisplayName("pong() creates a PONG message from a member")
    void testPongMessage() {
        Message msg = MessageFactory.pong("Bob");

        assertEquals(MessageType.PONG, msg.getType());
        assertEquals("Bob", msg.getSenderId());
    }

    @Test
    @DisplayName("ping and pong have different types")
    void testPingAndPongAreDifferentTypes() {
        Message ping = MessageFactory.ping("Alice");
        Message pong = MessageFactory.pong("Bob");

        assertNotEquals(ping.getType(), pong.getType(),
                "PING and PONG must have different MessageTypes");
    }

    // ── COORDINATOR NOTIFY ────────────────────────────────────────────

    @Test
    @DisplayName("coordinatorNotify() carries the coordinator as payload")
    void testCoordinatorNotifyCarriesPayload() {
        Member coord = new Member("Alice", "127.0.0.1", 5001);
        coord.setCoordinator(true);

        Message msg = MessageFactory.coordinatorNotify("SERVER", coord);

        assertEquals(MessageType.COORDINATOR_NOTIFY, msg.getType());
        assertNotNull(msg.getMemberPayload(), "Payload should be the coordinator Member");
        assertEquals("Alice", msg.getMemberPayload().getId());
    }

    @Test
    @DisplayName("coordinatorNotify() for self says 'You are now the COORDINATOR'")
    void testCoordinatorNotifyForSelf() {
        Member coord = new Member("Alice", "127.0.0.1", 5001);
        Message msg = MessageFactory.coordinatorNotify("Alice", coord);

        // Content should inform the member they ARE the coordinator
        assertTrue(msg.getContent().contains("COORDINATOR"),
                "Self-notification should contain 'COORDINATOR'");
    }

    // ── MEMBER LIST ───────────────────────────────────────────────────

    @Test
    @DisplayName("memberListResponse() carries list of members as payload")
    void testMemberListResponsePayload() {
        List<Member> members = List.of(
                new Member("Alice", "127.0.0.1", 5001),
                new Member("Bob",   "127.0.0.1", 5002)
        );

        Message msg = MessageFactory.memberListResponse("SERVER", members);

        assertEquals(MessageType.MEMBER_LIST, msg.getType());
        assertNotNull(msg.getMemberListPayload());
        assertEquals(2, msg.getMemberListPayload().size());
    }

    @Test
    @DisplayName("memberListRequest() creates correct request message")
    void testMemberListRequest() {
        Message msg = MessageFactory.memberListRequest("Bob");

        assertEquals(MessageType.MEMBER_LIST_REQUEST, msg.getType());
        assertEquals("Bob", msg.getSenderId());
    }

    // ── SYSTEM NOTIFICATION ───────────────────────────────────────────

    @Test
    @DisplayName("systemNotification() has SERVER as sender and correct content")
    void testSystemNotification() {
        Message msg = MessageFactory.systemNotification("Alice has joined!");

        assertEquals(MessageType.SYSTEM_NOTIFICATION, msg.getType());
        assertEquals("SERVER", msg.getSenderId());
        assertEquals("Alice has joined!", msg.getContent());
    }

    // ── TIMESTAMP ────────────────────────────────────────────────────

    @Test
    @DisplayName("All factory-created messages have non-null timestamps")
    void testAllMessagesHaveTimestamps() {
        // Every message created by the factory should have a timestamp
        // (set automatically in the Message constructor)
        assertNotNull(MessageFactory.broadcast("A", "test").getTimestamp());
        assertNotNull(MessageFactory.join("A").getTimestamp());
        assertNotNull(MessageFactory.ping("A").getTimestamp());
        assertNotNull(MessageFactory.systemNotification("test").getTimestamp());
    }
}