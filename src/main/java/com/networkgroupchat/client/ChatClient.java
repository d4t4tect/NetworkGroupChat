package com.networkgroupchat.client;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.model.Message;
import com.networkgroupchat.pattern.factory.MessageFactory;
import com.networkgroupchat.util.ChatLogger;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * CLIENT CLASS — ChatClient
 *
 * ── What this class does ─────────────────────────────────────────────────────
 * ChatClient is the user-facing application. It:
 *   1. Connects to the server via TCP socket
 *   2. Sends a JOIN message with the user's chosen ID
 *   3. Starts MessageListener on a background thread (reads incoming messages)
 *   4. Runs the input loop on the MAIN thread (reads keyboard commands)
 *   5. Sends LEAVE and closes cleanly when the user types /quit
 *
 * ── CLI Command Protocol ─────────────────────────────────────────────────────
 * Users interact via typed commands:
 *
 *   /msg <receiverId> <text>   → Send a PRIVATE message
 *   /list                      → Request the member list from server
 *   /quit                      → Leave the group gracefully
 *   <anything else>            → Send as a BROADCAST message
 *
 *
 * ── Two-Thread Design ────────────────────────────────────────────────────────
 *   Main thread:        inputLoop() — reads Scanner (keyboard), sends messages
 *   Background thread:  MessageListener — reads socket, prints received messages
 *
 * ── Shutdown Hook ────────────────────────────────────────────────────────────
 * Runtime.getRuntime().addShutdownHook() registers code that runs when the JVM
 * exits (including Ctrl+C). This ensures we always send a LEAVE message and
 * clean up — even if the user force-quits.
 */
public class ChatClient {

    // ── CONSTANTS ────────────────────────────────────────────────────
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5000;
    private static final String SOURCE = "ChatClient";

    // ── COMMANDS ─────────────────────────────────────────────────────
    private static final String CMD_PRIVATE  = "/msg";
    private static final String CMD_LIST     = "/list";
    private static final String CMD_QUIT     = "/quit";
    private static final String CMD_HELP     = "/help";

    // ── STATE ────────────────────────────────────────────────────────
    private final String memberId;
    private final String serverHost;
    private final int serverPort;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private MessageListener messageListener;
    private volatile boolean connected;

    /** Tracks if WE are the current coordinator */
    private volatile boolean isCoordinator;

    // ── CONSTRUCTOR ──────────────────────────────────────────────────

    /**
     * @param memberId   The unique ID this client will use in the group
     * @param serverHost The server's hostname or IP
     * @param serverPort The server's port number
     */
    public ChatClient(String memberId, String serverHost, int serverPort) {
        this.memberId = memberId;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.connected = false;
        this.isCoordinator = false;
    }

    // Convenience constructor with defaults
    public ChatClient(String memberId) {
        this(memberId, DEFAULT_HOST, DEFAULT_PORT);
    }

    // ── CONNECTION ────────────────────────────────────────────────────

    /**
     * Connect to the server, complete the JOIN handshake, and start listening.
     * Then enters the interactive input loop (blocks until /quit).
     */
    public void start() {
        try {
            connect();
            sendJoin();
            startMessageListener();
            registerShutdownHook();
            printHelp();
            inputLoop();   // BLOCKS here until /quit

        } catch (IOException e) {
            ChatLogger.error(SOURCE, "Could not connect to server at "
                    + serverHost + ":" + serverPort + " — " + e.getMessage());
            ChatLogger.system("Is the server running? Try: java Main server");
        } finally {
            disconnect();
        }
    }

    /**
     * Open the TCP socket and set up streams.
     *
     * CRITICAL ORDER — same as server side:
     * Create ObjectOutputStream FIRST (writes header), then ObjectInputStream.
     * Both sides must follow this rule, or they deadlock.
     */
    private void connect() throws IOException {
        ChatLogger.info(SOURCE, "Connecting to server at " + serverHost + ":" + serverPort + "...");

        socket = new Socket(serverHost, serverPort);

        // OutputStream first — writes the stream header to the server
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();

        // InputStream second — reads the stream header from the server
        inputStream = new ObjectInputStream(socket.getInputStream());

        connected = true;
        ChatLogger.info(SOURCE, "Connected successfully!");
    }

    /**
     * Send the initial JOIN message to register with the server.
     */
    private void sendJoin() throws IOException {
        Message joinMsg = MessageFactory.join(memberId);
        outputStream.writeObject(joinMsg);
        outputStream.flush();
        ChatLogger.info(SOURCE, "JOIN sent with ID: '" + memberId + "'");
    }

    /**
     * Start the MessageListener background thread.
     * From this point, incoming messages will be displayed automatically.
     */
    private void startMessageListener() {
        messageListener = new MessageListener(inputStream, this);
        Thread listenerThread = new Thread(messageListener, "MessageListener-" + memberId);
        listenerThread.setDaemon(true);
        listenerThread.start();
        ChatLogger.info(SOURCE, "Message listener started.");
    }

    // ── INPUT LOOP ────────────────────────────────────────────────────

    /**
     * The main interactive loop — reads the user's keyboard input and
     * sends the appropriate message to the server.
     *
     * Runs on the MAIN thread (blocks Scanner.nextLine() waiting for input).
     */
    private void inputLoop() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n[" + memberId + "] Ready. Type a message or /help for commands.\n");

        while (connected && scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.equalsIgnoreCase(CMD_QUIT)) {
                handleQuit();
                break;

            } else if (input.equalsIgnoreCase(CMD_LIST)) {
                sendMessage(MessageFactory.memberListRequest(memberId));

            } else if (input.equalsIgnoreCase(CMD_HELP)) {
                printHelp();

            } else if (input.startsWith(CMD_PRIVATE + " ")) {
                handlePrivateCommand(input);

            } else {
                // Anything else = broadcast message
                sendMessage(MessageFactory.broadcast(memberId, input));
            }
        }
    }

    /**
     * Parse and send a private message command.
     * Format: /msg <receiverId> <message text>
     * Example: /msg Bob Hey Bob, this is private!
     */
    private void handlePrivateCommand(String input) {
        // Remove "/msg " prefix, then split on first space to get [receiverId, rest]
        String[] parts = input.substring(CMD_PRIVATE.length() + 1).split(" ", 2);

        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            System.out.println("Usage: /msg <receiverId> <your message>");
            return;
        }

        String receiverId = parts[0];
        String content = parts[1];
        sendMessage(MessageFactory.privateMessage(memberId, receiverId, content));
    }

    /**
     * Gracefully leave the group.
     */
    private void handleQuit() {
        ChatLogger.system("Leaving the group...");
        sendMessage(MessageFactory.leave(memberId));
        connected = false;
    }

    // ── CALLBACKS (called by MessageListener) ─────────────────────────

    /**
     * Called by MessageListener when a COORDINATOR_NOTIFY is received.
     * If WE are the new coordinator, set our flag and log it clearly.
     */
    public void onCoordinatorNotify(Member coordinator) {
        if (coordinator.getId().equals(memberId)) {
            isCoordinator = true;
            System.out.println("\n🎯 YOU ARE NOW THE COORDINATOR!\n");
        } else {
            isCoordinator = false;
        }
    }

    /**
     * Called by MessageListener when the server connection drops.
     * Sets connected=false, which will cause inputLoop() to exit.
     */
    public void onServerDisconnected() {
        connected = false;
        ChatLogger.system("Disconnected from server.");
    }

    /**
     * Called by MessageListener when a PING arrives.
     * Send a PONG back to confirm we're still alive.
     */
    public void sendPong() {
        sendMessage(MessageFactory.pong(memberId));
    }

    // ── SEND HELPER ───────────────────────────────────────────────────

    /**
     * Write a message to the server socket.
     * Synchronized to prevent concurrent write interleaving.
     *
     * @param message The message to send
     */
    public void sendMessage(Message message) {
        if (outputStream == null || !connected) return;
        synchronized (outputStream) {
            try {
                outputStream.reset();
                outputStream.writeObject(message);
                outputStream.flush();
            } catch (IOException e) {
                ChatLogger.error(SOURCE, "Failed to send message: " + e.getMessage());
                connected = false;
            }
        }
    }

    // ── SHUTDOWN ──────────────────────────────────────────────────────

    /**
     * Register a JVM shutdown hook — runs when Ctrl+C is pressed or JVM exits.
     * This ensures we always clean up, even on force-quit.
     *
     * Without this, pressing Ctrl+C would just kill the socket without sending
     * LEAVE — the server would have to detect the broken socket via IOException.
     * With this, we send LEAVE first for a cleaner shutdown.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (connected) {
                ChatLogger.system("Shutdown detected — sending LEAVE...");
                sendMessage(MessageFactory.leave(memberId));
            }
            closeResources();
        }, "ShutdownHook-" + memberId));
    }

    private void disconnect() {
        connected = false;
        if (messageListener != null) messageListener.stop();
        closeResources();
    }

    private void closeResources() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            ChatLogger.error(SOURCE, "Error closing socket: " + e.getMessage());
        }
    }

    // ── DISPLAY ───────────────────────────────────────────────────────

    private void printHelp() {
        System.out.println("\n┌─── Commands ─────────────────────────────────────────┐");
        System.out.println("│  <message>              → Broadcast to everyone       │");
        System.out.println("│  /msg <id> <message>    → Private message to <id>     │");
        System.out.println("│  /list                  → Show all connected members  │");
        System.out.println("│  /help                  → Show this help              │");
        System.out.println("│  /quit                  → Leave the group             │");
        System.out.println("└──────────────────────────────────────────────────────┘\n");
    }

    // ── GETTERS ───────────────────────────────────────────────────────

    public String getMemberId() { return memberId; }
    public boolean isConnected() { return connected; }
    public boolean isCoordinator() { return isCoordinator; }
}