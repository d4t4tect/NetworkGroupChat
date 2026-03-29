package com.networkgroupchat.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

/**
 * UNIT TESTS — MessageTest
 *
 * Tests the Message class: construction, field access, helper methods,
 * and payload casting. Since Message is immutable, we focus on verifying
 * that the right data goes in and comes back out correctly.
 */
@DisplayName("Message Model Tests")
class MessageTest {

    // ── BROADCAST CONSTRUCTION ───────────────────────────────────────

    @Test
    @DisplayName("Broadcast message has correct type, sender, and content")
    void testBroadcastMessageCreation() {
        // Arrange + Act
        Message msg = new Message(MessageType.BROADCAST, "Alice", "Hello everyone!");

        // Assert
        assertEquals(MessageType.BROADCAST, msg.getType());
        assertEquals("Alice", msg.getSenderId());
        assertEquals("Hello everyone!", msg.getContent());
        assertNull(msg.getReceiverId(), "Broadcast should have no specific receiver");
    }

    // ── PRIVATE MESSAGE CONSTRUCTION ─────────────────────────────────

    @Test
    @DisplayName("Private message has correct sender, receiver, and content")
    void testPrivateMessageCreation() {
        // Arrange + Act
        Message msg = new Message(MessageType.PRIVATE, "Alice", "Bob", "Hey Bob!");

        // Assert
        assertEquals(MessageType.PRIVATE, msg.getType());
        assertEquals("Alice", msg.getSenderId());
        assertEquals("Bob", msg.getReceiverId());
        assertEquals("Hey Bob!", msg.getContent());
    }

    // ── isBroadcast() HELPER ─────────────────────────────────────────

    @Test
    @DisplayName("isBroadcast() returns true when receiverId is null")
    void testIsBroadcastWhenReceiverNull() {
        Message msg = new Message(MessageType.BROADCAST, "Alice", "Hello!");
        assertTrue(msg.isBroadcast(),
                "Message with null receiverId should be considered a broadcast");
    }

    @Test
    @DisplayName("isBroadcast() returns false when receiverId is set")
    void testIsBroadcastWhenReceiverSet() {
        Message msg = new Message(MessageType.PRIVATE, "Alice", "Bob", "Hey");
        assertFalse(msg.isBroadcast(),
                "Private message should not be considered a broadcast");
    }

    // ── TIMESTAMP ────────────────────────────────────────────────────

    @Test
    @DisplayName("Message timestamp is set automatically on creation")
    void testTimestampSetOnCreation() {
        Message msg = new Message(MessageType.BROADCAST, "Alice", "Hello");
        assertNotNull(msg.getTimestamp(), "Timestamp should be set in constructor");
        assertNotNull(msg.getFormattedTime(), "getFormattedTime() should return a string");
    }

    // ── PAYLOAD CASTING ──────────────────────────────────────────────

    @Test
    @DisplayName("getMemberListPayload() correctly casts a List<Member> payload")
    void testGetMemberListPayload() {
        // Arrange
        List<Member> members = List.of(
                new Member("Alice", "127.0.0.1", 5001),
                new Member("Bob",   "127.0.0.1", 5002)
        );

        // Act — create a message carrying a member list as payload
        Message msg = new Message(MessageType.MEMBER_LIST, "SERVER", (Object) members);

        // Assert
        List<Member> retrieved = msg.getMemberListPayload();
        assertNotNull(retrieved);
        assertEquals(2, retrieved.size());
        assertEquals("Alice", retrieved.get(0).getId());
    }

    @Test
    @DisplayName("getMemberPayload() correctly casts a single Member payload")
    void testGetMemberPayload() {
        // Arrange
        Member coordinator = new Member("Alice", "127.0.0.1", 5001);
        coordinator.setCoordinator(true);

        // Act
        Message msg = new Message(MessageType.COORDINATOR_NOTIFY,
                "SERVER", null, "You are coordinator", coordinator);

        // Assert
        Member retrieved = msg.getMemberPayload();
        assertNotNull(retrieved);
        assertEquals("Alice", retrieved.getId());
        assertTrue(retrieved.isCoordinator());
    }

    // ── toString ─────────────────────────────────────────────────────

    @Test
    @DisplayName("toString contains type, sender, and content")
    void testToStringFormat() {
        Message msg = new Message(MessageType.BROADCAST, "Alice", "Hi there!");
        String str = msg.toString();

        assertTrue(str.contains("BROADCAST"), "toString should contain message type");
        assertTrue(str.contains("Alice"),     "toString should contain sender ID");
        assertTrue(str.contains("Hi there!"), "toString should contain message content");
    }
}