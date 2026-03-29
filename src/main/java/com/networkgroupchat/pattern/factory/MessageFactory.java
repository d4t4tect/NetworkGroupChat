package com.networkgroupchat.pattern.factory;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.model.Message;
import com.networkgroupchat.model.MessageType;

import java.util.List;

/**
 * FACTORY PATTERN — MessageFactory
 *
 * ── What is the Factory Pattern? ────────────────────────────────────────────
 * Factory provides STATIC methods to create objects, hiding the constructor.
 * Instead of: new Message(MessageType.BROADCAST, "Alice", null, "Hello!", null)
 * You write:  MessageFactory.broadcast("Alice", "Hello!")
 *
 * ── Why use it here? ────────────────────────────────────────────────────────
 * Message has 5 constructor parameters. When you create messages in many places
 * across the codebase, it's easy to mix up argument order or forget a field.
 * Factory methods have DESCRIPTIVE NAMES — you know exactly what you're creating:
 *   MessageFactory.ping("SERVER")           — clearly a ping
 *   MessageFactory.privateMsg(...)          — clearly a private message
 *   MessageFactory.memberListResponse(...)  — clearly a member list
 *
 * This also means: if Message's constructor ever changes, you update ONE place
 * (this factory) instead of hunting through every file that uses 'new Message()'.
 *
 * ── Design Note ─────────────────────────────────────────────────────────────
 * This is the STATIC FACTORY METHOD variant — simpler than the full Abstract
 * Factory pattern, but still a valid "Factory" pattern for your report.
 * All methods are static. This class is never instantiated (private constructor).
 */
public class MessageFactory {

    // Prevent instantiation — this class is a utility class of static methods only
    private MessageFactory() {}

    // ── CHAT MESSAGES ────────────────────────────────────────────────

    /**
     * Create a broadcast message (sent to everyone).
     *
     * @param senderId The ID of the sender
     * @param content  The message text
     * @return A Message with type BROADCAST, no specific receiver
     */
    public static Message broadcast(String senderId, String content) {
        return new Message(MessageType.BROADCAST, senderId, null, content, null);
    }

    /**
     * Create a private message (sent to one specific member).
     *
     * @param senderId   The ID of the sender
     * @param receiverId The ID of the intended recipient
     * @param content    The message text
     * @return A Message with type PRIVATE, targeted at receiverId
     */
    public static Message privateMessage(String senderId, String receiverId, String content) {
        return new Message(MessageType.PRIVATE, senderId, receiverId, content, null);
    }

    // ── CONNECTION LIFECYCLE ─────────────────────────────────────────

    /**
     * Create a JOIN message — sent by a new client upon connecting.
     *
     * @param memberId The ID the new client wants to use
     * @return A Message with type JOIN
     */
    public static Message join(String memberId) {
        return new Message(MessageType.JOIN, memberId, "Joining group...");
    }

    /**
     * Create a LEAVE message — sent by a client before disconnecting.
     *
     * @param memberId The ID of the leaving member
     * @return A Message with type LEAVE
     */
    public static Message leave(String memberId) {
        return new Message(MessageType.LEAVE, memberId, "Leaving group...");
    }

    // ── COORDINATOR PROTOCOL ─────────────────────────────────────────

    /**
     * Create a COORDINATOR_NOTIFY message.
     * Sent to a new joiner telling them who the coordinator is,
     * OR sent to a member telling them THEY are now the coordinator.
     *
     * @param senderId    The server or system sending the notification
     * @param coordinator The Member who is currently (or newly) the coordinator
     * @return A Message with type COORDINATOR_NOTIFY, payload = coordinator Member
     */
    public static Message coordinatorNotify(String senderId, Member coordinator) {
        String content = coordinator.getId().equals(senderId)
                ? "You are now the COORDINATOR."
                : "Current coordinator is: " + coordinator.getId();
        return new Message(MessageType.COORDINATOR_NOTIFY, senderId, null, content, coordinator);
    }

    /**
     * Create a MEMBER_LIST_REQUEST — sent by a client asking "who's in the group?"
     *
     * @param requesterId The ID of the client requesting the list
     * @return A Message with type MEMBER_LIST_REQUEST
     */
    public static Message memberListRequest(String requesterId) {
        return new Message(MessageType.MEMBER_LIST_REQUEST, requesterId, "Requesting member list.");
    }

    /**
     * Create a MEMBER_LIST response — sent by the server back to the requester.
     *
     * @param senderId The server ID ("SERVER")
     * @param members  The full list of currently connected Members
     * @return A Message with type MEMBER_LIST, payload = List<Member>
     */
    public static Message memberListResponse(String senderId, List<Member> members) {
        return new Message(MessageType.MEMBER_LIST, senderId, null,
                members.size() + " member(s) currently connected.", members);
    }

    // ── HEARTBEAT ────────────────────────────────────────────────────

    /**
     * Create a PING message — sent by the coordinator every 20 seconds.
     *
     * @param coordinatorId The coordinator's ID
     * @return A Message with type PING
     */
    public static Message ping(String coordinatorId) {
        return new Message(MessageType.PING, coordinatorId, "PING");
    }

    /**
     * Create a PONG message — sent by a client in response to a PING.
     *
     * @param memberId The responding member's ID
     * @return A Message with type PONG
     */
    public static Message pong(String memberId) {
        return new Message(MessageType.PONG, memberId, "PONG");
    }

    // ── SYSTEM NOTIFICATIONS ─────────────────────────────────────────

    /**
     * Create a SYSTEM_NOTIFICATION — informational messages from the server.
     * e.g. "Alice has joined the group." / "Bob has left the group."
     *
     * @param content The notification text to display to all clients
     * @return A Message with type SYSTEM_NOTIFICATION, sender = "SERVER"
     */
    public static Message systemNotification(String content) {
        return new Message(MessageType.SYSTEM_NOTIFICATION, "SERVER", content);
    }
}