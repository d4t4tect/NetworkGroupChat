package com.networkgroupchat.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * MODEL CLASS — Message
 *
 * This is the CORE data packet of the entire system.
 * EVERY piece of communication — chat, ping, join, leave, member lists —
 * is wrapped in a Message object and sent over the socket.
 *
 * Think of Message like an envelope:
 *  - type       = what kind of message this is (the label on the envelope)
 *  - senderId   = who sent it (return address)
 *  - receiverId = who it's for (destination — null means "everyone")
 *  - content    = the actual text content
 *  - payload    = any extra data (e.g. a List<Member> for MEMBER_LIST)
 *  - timestamp  = when it was created
 *
 * DESIGN DECISION — One class for all message types:
 * We could have separate classes (PingMessage, BroadcastMessage, etc.)
 * but using ONE class with a 'type' field keeps the network code simple —
 * ObjectInputStream just reads Message objects without needing to know the subtype.
 * The receiver switches on 'type' to handle accordingly.
 *
 * SERIALIZABLE — Required for ObjectOutputStream to send this over a socket.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── FIELDS ──────────────────────────────────────────────────────

    /** What kind of message this is — drives all handling logic */
    private final MessageType type;

    /** ID of the member who created/sent this message */
    private final String senderId;

    /**
     * ID of the intended recipient.
     * NULL means broadcast (everyone receives it).
     * Non-null means private message to that specific member ID.
     */
    private final String receiverId;

    /** Human-readable text content. May be null for non-chat messages (e.g. PING). */
    private final String content;

    /**
     * Generic payload for structured data.
     * Examples:
     *   - MEMBER_LIST response → payload is a List<Member>
     *   - COORDINATOR_NOTIFY  → payload is a single Member (the coordinator)
     * Using Object here gives us flexibility without creating many Message subclasses.
     * The receiver casts it to the expected type based on MessageType.
     */
    private final Object payload;

    /** Automatically set to the moment this Message object is created */
    private final LocalDateTime timestamp;

    // ── CONSTRUCTORS ─────────────────────────────────────────────────
    // We provide multiple constructors for convenience.
    // The most specific one (all args) is called by the others.

    /**
     * Full constructor — use this when you need ALL fields.
     */
    public Message(MessageType type, String senderId, String receiverId,
                   String content, Object payload) {
        this.type = type;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Convenience: broadcast/simple message (no specific receiver, no payload).
     * e.g. new Message(BROADCAST, "Alice", "Hello everyone!")
     */
    public Message(MessageType type, String senderId, String content) {
        this(type, senderId, null, content, null);
    }

    /**
     * Convenience: system message with payload only (no text content).
     * e.g. MEMBER_LIST response carrying a List<Member>
     */
    public Message(MessageType type, String senderId, Object payload) {
        this(type, senderId, null, null, payload);
    }

    /**
     * Convenience: private message (has a specific receiver).
     * e.g. new Message(PRIVATE, "Alice", "Bob", "Hey Bob!")
     */
    public Message(MessageType type, String senderId, String receiverId, String content) {
        this(type, senderId, receiverId, content, null);
    }

    // ── GETTERS ──────────────────────────────────────────────────────
    // Message is IMMUTABLE — no setters. Once created, it never changes.
    // Immutability is safe for multi-threaded environments.

    public MessageType getType() { return type; }

    public String getSenderId() { return senderId; }

    public String getReceiverId() { return receiverId; }

    public String getContent() { return content; }

    public Object getPayload() { return payload; }

    public LocalDateTime getTimestamp() { return timestamp; }

    /** Helper: is this a broadcast (no specific receiver)? */
    public boolean isBroadcast() {
        return receiverId == null;
    }

    /**
     * Helper: safely get payload as a List<Member>.
     * We suppress the unchecked cast warning — we know what we put in,
     * and the type field tells the receiver what to expect.
     */
    @SuppressWarnings("unchecked")
    public List<Member> getMemberListPayload() {
        return (List<Member>) payload;
    }

    /** Helper: safely get payload as a single Member (e.g. coordinator info) */
    public Member getMemberPayload() {
        return (Member) payload;
    }

    // ── DISPLAY ───────────────────────────────────────────────────────

    /**
     * Formatted timestamp string for display.
     * Example: "14:32:05"
     */
    public String getFormattedTime() {
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * Human-readable representation of this message.
     * Used for logging and printing to the console.
     *
     * Examples:
     *   [14:32:05] [BROADCAST] Alice: Hello everyone!
     *   [14:32:06] [PRIVATE → Bob] Alice: Hey Bob, privately!
     *   [14:32:10] [PING] SERVER
     */
    @Override
    public String toString() {
        String receiver = (receiverId != null) ? " → " + receiverId : "";
        String text = (content != null) ? ": " + content : "";
        return String.format("[%s] [%s%s] %s%s",
                getFormattedTime(), type, receiver, senderId, text);
    }
}