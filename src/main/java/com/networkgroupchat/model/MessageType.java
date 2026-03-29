package com.networkgroupchat.model;

/**
 * ENUM — MessageType
 *
 * An enum is a special Java class that holds a FIXED set of constants.
 * Instead of passing raw strings like "BROADCAST" (risky — typos compile fine!),
 * we use this enum. The compiler will catch any unknown type immediately.
 *
 * Think of MessageType as the "subject line" of every message in the system.
 * The server and clients look at this field first to decide what to DO
 * with the message.
 *
 * WHY SERIALIZABLE?
 * We send Message objects across the network via ObjectOutputStream.
 * Java requires every object sent this way to implement Serializable.
 * Since MessageType is stored INSIDE Message, it must be Serializable too.
 */
public enum MessageType {

    // ── CHAT MESSAGES ──────────────────────────────────────────────
    /**
     * Sent from one client → server → ALL clients.
     * The server uses BroadcastStrategy to forward this.
     */
    BROADCAST,

    /**
     * Sent from one client → server → ONE specific client.
     * The server uses PrivateMessageStrategy to forward this.
     * The Message will have a non-null 'receiverId' field.
     */
    PRIVATE,

    // ── CONNECTION LIFECYCLE ────────────────────────────────────────
    /**
     * Sent by a client immediately after connecting.
     * Payload: the client's chosen ID.
     * Server responds with COORDINATOR_NOTIFY or MEMBER_LIST.
     */
    JOIN,

    /**
     * Sent by a client when gracefully disconnecting (e.g. Quit button).
     * This lets the server clean up properly rather than detecting a crash.
     */
    LEAVE,

    // ── COORDINATOR PROTOCOL ────────────────────────────────────────
    /**
     * Sent by the SERVER → CLIENT to tell a client it is now the coordinator.
     * Also sent to NEW joiners to inform them WHO the coordinator currently is.
     * Payload: coordinator's Member info.
     */
    COORDINATOR_NOTIFY,

    /**
     * Sent by ANY client → server asking "who is in the group right now?"
     * Server replies with MEMBER_LIST response.
     */
    MEMBER_LIST_REQUEST,

    /**
     * Sent by SERVER → CLIENT in response to MEMBER_LIST_REQUEST.
     * Payload: a list of all active Member objects (IDs, IPs, ports).
     */
    MEMBER_LIST,

    // ── HEARTBEAT / PING-PONG ───────────────────────────────────────
    /**
     * Sent by the COORDINATOR → all members every 20 seconds.
     * Purpose: check who is still alive.
     * If no PONG is received back within a timeout, that member is removed.
     */
    PING,

    /**
     * Sent by a CLIENT → coordinator in response to PING.
     * "I'm still here!"
     */
    PONG,

    // ── SYSTEM / INFO ───────────────────────────────────────────────
    /**
     * General purpose server → client system notification.
     * e.g. "User Alice has left the group."
     * Displayed to the user but requires no action from the client.
     */
    SYSTEM_NOTIFICATION
}