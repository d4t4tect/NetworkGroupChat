package com.networkgroupchat.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MODEL CLASS — Member
 *
 * Represents ONE connected client in the group chat.
 * This object is stored in MemberRegistry on the server, and also
 * sent to clients in MEMBER_LIST responses so they know who's in the group.
 *
 * SERIALIZABLE — Why?
 * We send Member objects inside Message payloads across the network.
 * Java's ObjectOutputStream requires Serializable on anything it transmits.
 * serialVersionUID is a version stamp — if you change this class, bump the number
 * so old/new versions don't accidentally mix.
 *
 * THREAD SAFETY NOTE:
 * Multiple ClientHandler threads may READ this object at the same time.
 * We use 'volatile' on isCoordinator because it can be changed by one thread
 * and read by another — volatile ensures the change is immediately visible
 * to all threads (no stale cached copy).
 */
public class Member implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── FIELDS ──────────────────────────────────────────────────────

    /** Unique identifier chosen by the user when joining, e.g. "Alice" */
    private final String id;

    /** IP address of this client, recorded when they connect */
    private final String ipAddress;

    /** Port number of this client's connection */
    private final int port;

    /**
     * Whether this member is currently the coordinator.
     * 'volatile' = any thread that reads this always gets the latest value.
     * Without volatile, a thread might read a stale cached value from CPU cache.
     */
    private volatile boolean isCoordinator;

    /** When this member joined the group */
    private final LocalDateTime joinedAt;

    /**
     * Tracks the last time we received a PONG from this member.
     * Used by the coordinator during ping checks.
     * 'volatile' because the ping timer thread writes it and other threads read it.
     */
    private volatile LocalDateTime lastPongReceived;

    // ── CONSTRUCTOR ─────────────────────────────────────────────────

    /**
     * Create a new Member.
     *
     * @param id        The unique string ID this member chose
     * @param ipAddress Their IP (from Socket.getInetAddress())
     * @param port      Their port (from Socket.getPort())
     */
    public Member(String id, String ipAddress, int port) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
        this.isCoordinator = false;       // default: not coordinator
        this.joinedAt = LocalDateTime.now();
        this.lastPongReceived = LocalDateTime.now(); // assume alive on creation
    }

    // ── GETTERS ─────────────────────────────────────────────────────
    // We use getters (not public fields) so we can add validation later
    // without breaking any code that calls these methods.

    public String getId() { return id; }

    public String getIpAddress() { return ipAddress; }

    public int getPort() { return port; }

    public boolean isCoordinator() { return isCoordinator; }

    public LocalDateTime getJoinedAt() { return joinedAt; }

    public LocalDateTime getLastPongReceived() { return lastPongReceived; }

    // ── SETTERS (only mutable fields) ───────────────────────────────

    /** Called when this member is promoted/demoted as coordinator */
    public void setCoordinator(boolean coordinator) {
        this.isCoordinator = coordinator;
    }

    /** Called each time we receive a PONG from this member */
    public void updateLastPong() {
        this.lastPongReceived = LocalDateTime.now();
    }

    // ── UTILITY METHODS ─────────────────────────────────────────────

    /**
     * Returns a clean, human-readable summary of this member.
     * Used in MEMBER_LIST responses and system notifications.
     *
     * Example output:  [Alice | 127.0.0.1:52341 | COORDINATOR | joined 14:32:05]
     */
    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        String role = isCoordinator ? "COORDINATOR" : "member";
        return String.format("[%s | %s:%d | %s | joined %s]",
                id, ipAddress, port, role, joinedAt.format(fmt));
    }

    /**
     * Two Members are equal if they have the same ID.
     * This lets us use .contains() and .remove() on Lists of Members.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member)) return false;
        Member other = (Member) o;
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}