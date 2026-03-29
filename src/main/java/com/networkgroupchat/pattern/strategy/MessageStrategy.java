package com.networkgroupchat.pattern.strategy;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.model.Message;

import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * STRATEGY PATTERN — MessageStrategy (the "Strategy" interface)
 *
 * ── What is the Strategy Pattern? ───────────────────────────────────────────
 * Strategy defines a FAMILY of algorithms, encapsulates each one in its own
 * class, and makes them interchangeable at runtime.
 *
 * Instead of: if (type == BROADCAST) { ...30 lines... }
 *             else if (type == PRIVATE) { ...20 lines... }
 *
 * You write:  strategy.send(message, streams)
 * And swap:   strategy = new BroadcastStrategy()   or
 *             strategy = new PrivateMessageStrategy()
 *
 * ── In our system ───────────────────────────────────────────────────────────
 * When ClientHandler receives a message, it picks a strategy based on MessageType,
 * then calls strategy.send() — the strategy handles all the routing logic.
 *
 * BroadcastStrategy      → sends the message to ALL connected clients
 * PrivateMessageStrategy → sends the message to ONE specific client
 *
 * ── Why this matters for your marks ────────────────────────────────────────
 * This is a clean, textbook Strategy pattern:
 * - MessageStrategy = the interface (the "strategy")
 * - BroadcastStrategy / PrivateMessageStrategy = concrete strategies
 * - ClientHandler = the "context" that holds and uses the strategy
 */
public interface MessageStrategy {

    /**
     * Send a message using this strategy's routing logic.
     *
     * @param message     The Message to send (contains content, sender, receiver info)
     * @param allStreams   Map of memberId → ObjectOutputStream for every connected client.
     *                    The strategy uses this to write the message to the right socket(s).
     */
    void send(Message message, Map<String, ObjectOutputStream> allStreams);
}