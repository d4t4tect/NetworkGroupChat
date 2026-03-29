package com.networkgroupchat.pattern.strategy;

import com.networkgroupchat.model.Message;
import com.networkgroupchat.pattern.factory.MessageFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * STRATEGY PATTERN — PrivateMessageStrategy (Concrete Strategy #2)
 *
 * Implements MessageStrategy for PRIVATE messages.
 * Sends the message ONLY to the specific intended recipient.
 *
 * ── What we do here ─────────────────────────────────────────────────────────
 * 1. Look up the receiver's ID in the streams map
 * 2. If found → write the message directly to their socket only
 * 3. Also send a copy back to the SENDER so they see their own message
 * 4. If receiver not found → send an error notification back to the sender
 *
 * ── Error Handling ───────────────────────────────────────────────────────────
 * If the receiverId doesn't exist in our streams map, the intended recipient
 * is no longer connected. We notify the sender rather than silently failing.
 * Silent failures are terrible UX — the user thinks their message was sent!
 */
public class PrivateMessageStrategy implements MessageStrategy {

    /**
     * Send the message only to the intended recipient (and a copy to the sender).
     *
     * @param message    The private Message — must have non-null receiverId
     * @param allStreams Map of memberId → ObjectOutputStream for all connected clients
     */
    @Override
    public void send(Message message, Map<String, ObjectOutputStream> allStreams) {
        String receiverId = message.getReceiverId();
        String senderId = message.getSenderId();

        System.out.println("[PrivateMessageStrategy] Private message from '"
                + senderId + "' to '" + receiverId + "'.");

        if (receiverId == null) {
            System.err.println("[PrivateMessageStrategy] ERROR: receiverId is null on a PRIVATE message!");
            return;
        }

        // ── Send to recipient ────────────────────────────────────────
        ObjectOutputStream receiverStream = allStreams.get(receiverId);
        if (receiverStream != null) {
            try {
                receiverStream.reset();
                receiverStream.writeObject(message);
                receiverStream.flush();
            } catch (IOException e) {
                System.err.println("[PrivateMessageStrategy] Failed to send to receiver '"
                        + receiverId + "': " + e.getMessage());
            }
        } else {
            // Recipient not found — they've disconnected or the ID is wrong.
            // Notify the sender so they know the message wasn't delivered.
            notifySender(senderId, receiverId, allStreams);
            return;
        }

        // ── Echo back to sender ──────────────────────────────────────
        // So the sender sees their own message in their chat window
        ObjectOutputStream senderStream = allStreams.get(senderId);
        if (senderStream != null && !senderId.equals(receiverId)) {
            try {
                senderStream.reset();
                senderStream.writeObject(message);
                senderStream.flush();
            } catch (IOException e) {
                System.err.println("[PrivateMessageStrategy] Failed to echo back to sender '"
                        + senderId + "': " + e.getMessage());
            }
        }
    }

    /**
     * Send a system notification to the sender when their target isn't found.
     *
     * @param senderId   The message sender's ID
     * @param receiverId The intended (but missing) receiver's ID
     * @param allStreams All current output streams
     */
    private void notifySender(String senderId, String receiverId,
                              Map<String, ObjectOutputStream> allStreams) {
        ObjectOutputStream senderStream = allStreams.get(senderId);
        if (senderStream == null) return;

        Message errorMsg = MessageFactory.systemNotification(
                "Could not deliver private message: '" + receiverId
                        + "' is not connected or does not exist.");
        try {
            senderStream.reset();
            senderStream.writeObject(errorMsg);
            senderStream.flush();
        } catch (IOException e) {
            System.err.println("[PrivateMessageStrategy] Could not notify sender: " + e.getMessage());
        }
    }
}