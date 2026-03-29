package com.networkgroupchat.pattern.strategy;

import com.networkgroupchat.model.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * STRATEGY PATTERN — BroadcastStrategy (Concrete Strategy #1)
 *
 * Implements MessageStrategy for BROADCAST messages.
 * Sends the given message to EVERY connected client — including the sender.
 * (Showing your own message back is common in chat apps and confirms delivery.)
 *
 * ── Why send back to the sender too? ────────────────────────────────────────
 * It confirms the message was received by the server and processed.
 * The client can display it as "sent" only after this confirmation.
 * This is how most chat systems work (WhatsApp, Slack, etc.)
 *
 * ── Fault Tolerance ─────────────────────────────────────────────────────────
 * If writing to one client fails (their socket dropped), we catch the exception
 * and continue sending to the others. One broken pipe doesn't stop the broadcast.
 * The broken client will be cleaned up when ClientHandler detects the disconnect.
 */
public class BroadcastStrategy implements MessageStrategy {

    /**
     * Send the message to ALL connected clients.
     *
     * @param message    The message to broadcast
     * @param allStreams Map of memberId → their ObjectOutputStream
     */
    @Override
    public void send(Message message, Map<String, ObjectOutputStream> allStreams) {
        System.out.println("[BroadcastStrategy] Broadcasting from '"
                + message.getSenderId() + "' to " + allStreams.size() + " client(s).");

        for (Map.Entry<String, ObjectOutputStream> entry : allStreams.entrySet()) {
            try {
                ObjectOutputStream out = entry.getValue();

                // reset() clears the ObjectOutputStream's internal cache.
                // Without this, Java caches the object reference and sends the
                // SAME object data on repeated sends (a common Java serialization bug).
                out.reset();
                out.writeObject(message);
                out.flush();

            } catch (IOException e) {
                // This client's connection is broken — log and move on.
                // The ClientHandler for this client will detect the broken socket
                // independently and handle cleanup.
                System.err.println("[BroadcastStrategy] Failed to send to '"
                        + entry.getKey() + "': " + e.getMessage());
            }
        }
    }
}