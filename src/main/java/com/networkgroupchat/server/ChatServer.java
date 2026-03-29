package com.networkgroupchat.server;

import com.networkgroupchat.pattern.observer.EventManager;
import com.networkgroupchat.util.ChatLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ObjectOutputStream;

/**
 * CORE SERVER CLASS — ChatServer
 *
 * ── Responsibility ───────────────────────────────────────────────────────────
 * ChatServer owns the ServerSocket — it listens for incoming TCP connections
 * on a given port and creates a new ClientHandler thread for each one.
 *
 * ── Threading Model ──────────────────────────────────────────────────────────
 * ChatServer runs its accept-loop on a DEDICATED background thread (it implements
 * Runnable). This means the main thread isn't blocked waiting for connections.
 *
 * For EACH client that connects:
 *   new Thread(new ClientHandler(socket, ...)).start()
 *
 * This is the "Thread Per Client" model — simple, works well for up to ~100
 * concurrent clients. At massive scale you'd use NIO or a thread pool, but
 * for this coursework it's correct and clearly justifiable.
 *
 * ── Shared State ─────────────────────────────────────────────────────────────
 * outputStreams: Map<memberId, ObjectOutputStream>
 * This map is SHARED between:
 *  - ChatServer (adds streams when clients connect)
 *  - All ClientHandlers (use streams to write messages)
 *  - BroadcastStrategy / PrivateMessageStrategy (iterate the map to send)
 *
 * We use ConcurrentHashMap — a thread-safe HashMap that allows concurrent
 * reads and writes from multiple threads without data corruption.
 *
 * ── Why not CopyOnWriteArrayList? ────────────────────────────────────────────
 * For the streams map we need O(1) lookup by memberId (not just iteration).
 * ConcurrentHashMap gives us that. CopyOnWriteArrayList doesn't support keyed lookup.
 */
public class ChatServer implements Runnable {

    // ── CONSTANTS ────────────────────────────────────────────────────
    public static final int DEFAULT_PORT = 5000;
    private static final String SOURCE = "ChatServer";

    // ── STATE ────────────────────────────────────────────────────────

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;   // volatile: read by multiple threads

    /**
     * The single global EventManager — all ClientHandlers share this.
     * When any handler fires an event, ALL other handlers are notified.
     * This is what enables broadcasting: one handler fires "BROADCAST",
     * EventManager calls every other handler's onEvent(), each forwards to client.
     */
    private final EventManager eventManager;

    /**
     * The single global MemberRegistry (Singleton).
     * Stored as a field for convenience — all handlers use the same instance.
     */
    private final MemberRegistry memberRegistry;

    /**
     * Map of memberId → their output stream.
     * This is the "phone book" — given a member's ID, we can find their socket
     * output stream and write messages directly to them.
     *
     * ConcurrentHashMap: thread-safe, multiple handlers read/write this safely.
     */
    private final Map<String, ObjectOutputStream> outputStreams;

    // ── CONSTRUCTOR ──────────────────────────────────────────────────

    public ChatServer(int port) {
        this.port = port;
        this.eventManager = new EventManager();
        this.memberRegistry = MemberRegistry.getInstance();
        this.outputStreams = new ConcurrentHashMap<>();
        this.running = false;
    }

    // Convenience constructor using default port
    public ChatServer() {
        this(DEFAULT_PORT);
    }

    // ── SERVER LIFECYCLE ─────────────────────────────────────────────

    /**
     * Start the server — opens the ServerSocket and begins the accept loop.
     * Called by the Main class:
     *   new Thread(new ChatServer()).start()
     *
     * ── What Runnable.run() means ────────────────────────────────────
     * Runnable is an interface with one method: run().
     * When you pass a Runnable to new Thread(...).start(), Java creates a new
     * OS thread and calls run() on it. So this method runs in its OWN thread,
     * completely separate from main().
     */
    @Override
    public void run() {
        try {
            // ServerSocket binds to a port and listens for connections.
            // The OS will queue up incoming connections if we're busy.
            serverSocket = new ServerSocket(port);
            running = true;

            ChatLogger.info(SOURCE, "========================================");
            ChatLogger.info(SOURCE, "  NetworkGroupChat Server started");
            ChatLogger.info(SOURCE, "  Listening on port " + port);
            ChatLogger.info(SOURCE, "========================================");

            acceptLoop();

        } catch (IOException e) {
            if (running) {
                // Only log as an error if we didn't intentionally stop
                ChatLogger.error(SOURCE, "Server failed to start: " + e.getMessage());
            }
        }
    }

    /**
     * The main accept loop — runs forever until stop() is called.
     *
     * accept() is a BLOCKING call: it pauses this thread until a client connects.
     * The moment a client connects, accept() returns a Socket representing
     * that specific client's connection. We immediately hand it to a new thread.
     */
    private void acceptLoop() {
        while (running) {
            try {
                // BLOCKS HERE until a client connects
                Socket clientSocket = serverSocket.accept();

                ChatLogger.info(SOURCE, "New connection from "
                        + clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort());

                // Create a handler for this client and start it in a new thread.
                // We don't wait for it — we immediately loop back to accept()
                // so we can handle the NEXT client connecting at the same time.
                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        eventManager,
                        memberRegistry,
                        outputStreams
                );

                Thread handlerThread = new Thread(handler);
                handlerThread.setDaemon(true); // daemon thread: dies when main thread dies
                handlerThread.setName("ClientHandler-" + clientSocket.getPort());
                handlerThread.start();

            } catch (IOException e) {
                if (running) {
                    ChatLogger.error(SOURCE, "Error accepting connection: " + e.getMessage());
                }
                // If running is false, the server was intentionally stopped — exit loop
            }
        }
    }

    /**
     * Gracefully stop the server.
     * Sets running=false, closes the ServerSocket (which unblocks accept()),
     * and clears shared state.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // this causes accept() to throw IOException → exits loop
            }
        } catch (IOException e) {
            ChatLogger.error(SOURCE, "Error stopping server: " + e.getMessage());
        }
        ChatLogger.info(SOURCE, "Server stopped.");
    }

    // ── GETTERS (for tests) ──────────────────────────────────────────

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
    public int getConnectedClientCount() { return outputStreams.size(); }
    public EventManager getEventManager() { return eventManager; }
    public Map<String, ObjectOutputStream> getOutputStreams() { return outputStreams; }
}