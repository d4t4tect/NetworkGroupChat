package com.networkgroupchat.client;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.model.Message;
import com.networkgroupchat.model.MessageType;
import com.networkgroupchat.util.ChatLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * CLIENT CLASS — MessageListener
 *
 * ── Why a separate thread? ───────────────────────────────────────────────────
 * The client has TWO things to do simultaneously:
 *   1. Read keyboard input from the user (ChatClient's main thread)
 *   2. Read incoming messages from the server socket (THIS class)
 *
 * If we tried to do both on one thread, reading from the socket would BLOCK
 * the keyboard input, and reading from the keyboard would BLOCK socket reading.
 * The solution: put the socket-reading in a separate background thread.
 *
 * ChatClient (main thread) → reads keyboard, sends messages
 * MessageListener (background thread) → reads socket, displays messages
 *
 * They run TRULY SIMULTANEOUSLY — the user can type while messages arrive.
 *
 * ── Runnable ─────────────────────────────────────────────────────────────────
 * MessageListener implements Runnable — it will be passed to a Thread:
 *   new Thread(new MessageListener(...)).start()
 */
public class MessageListener implements Runnable {

    private static final String SOURCE = "MessageListener";

    private final ObjectInputStream inputStream;
    private final ChatClient chatClient; // callback to notify ChatClient of events
    private volatile boolean running;

    /**
     * @param inputStream The socket's input stream (reads from server)
     * @param chatClient  Reference back to ChatClient for coordinator notifications
     */
    public MessageListener(ObjectInputStream inputStream, ChatClient chatClient) {
        this.inputStream = inputStream;
        this.chatClient = chatClient;
        this.running = true;
    }

    /**
     * Background thread loop — reads messages from server and displays them.
     * Runs until the connection is closed or stop() is called.
     */
    @Override
    public void run() {
        try {
            while (running) {
                Object obj = inputStream.readObject();
                if (obj instanceof Message) {
                    handleMessage((Message) obj);
                }
            }
        } catch (IOException e) {
            if (running) {
                ChatLogger.system("Connection to server lost: " + e.getMessage());
                chatClient.onServerDisconnected();
            }
        } catch (ClassNotFoundException e) {
            ChatLogger.error(SOURCE, "Unknown message class: " + e.getMessage());
        }
    }

    /**
     * Handle and display a message received from the server.
     * Each message type gets formatted differently for clarity.
     */
    private void handleMessage(Message message) {
        switch (message.getType()) {

            case BROADCAST:
                ChatLogger.chat("[BROADCAST] " + message.getSenderId()
                        + ": " + message.getContent());
                break;

            case PRIVATE:
                ChatLogger.chat("[PRIVATE from " + message.getSenderId()
                        + "]: " + message.getContent());
                break;

            case SYSTEM_NOTIFICATION:
                ChatLogger.system(message.getContent());
                break;

            case COORDINATOR_NOTIFY:
                // Either we are now coordinator, or someone else is
                Member coordinator = message.getMemberPayload();
                if (coordinator != null) {
                    ChatLogger.system(message.getContent());
                    // Tell ChatClient — it may need to start ping responses
                    chatClient.onCoordinatorNotify(coordinator);
                }
                break;

            case MEMBER_LIST:
                // Display all current members
                displayMemberList(message);
                break;

            case PING:
                // Server is checking we're alive — respond immediately
                ChatLogger.info(SOURCE, "PING received from coordinator — sending PONG");
                chatClient.sendPong();
                break;

            default:
                ChatLogger.warn(SOURCE, "Unhandled message type: " + message.getType());
        }
    }

    /**
     * Format and print the member list in a readable table.
     */
    private void displayMemberList(Message message) {
        List<Member> members = message.getMemberListPayload();
        if (members == null || members.isEmpty()) {
            ChatLogger.system("No members currently connected.");
            return;
        }
        System.out.println("\n┌─── Current Members (" + members.size() + ") ─────────────────────┐");
        for (Member m : members) {
            String role = m.isCoordinator() ? " [COORDINATOR]" : "";
            System.out.println("│  " + m.getId() + " | " + m.getIpAddress()
                    + ":" + m.getPort() + role);
        }
        System.out.println("└────────────────────────────────────────────────┘\n");
    }

    public void stop() {
        running = false;
    }
}