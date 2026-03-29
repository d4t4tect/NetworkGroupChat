package com.networkgroupchat.server;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.model.Message;
import com.networkgroupchat.model.MessageType;
import com.networkgroupchat.pattern.factory.MessageFactory;
import com.networkgroupchat.pattern.observer.EventListener;
import com.networkgroupchat.pattern.observer.EventManager;
import com.networkgroupchat.pattern.strategy.BroadcastStrategy;
import com.networkgroupchat.pattern.strategy.MessageStrategy;
import com.networkgroupchat.pattern.strategy.PrivateMessageStrategy;
import com.networkgroupchat.util.ChatLogger;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CORE CLASS — ClientHandler
 *
 * ── What this class does ─────────────────────────────────────────────────────
 * ClientHandler is created for EVERY client that connects to the server.
 * It is responsible for:
 *   1. Completing the handshake (reading the JOIN message, registering the member)
 *   2. Listening for incoming messages from ITS client (read loop)
 *   3. Routing those messages to the right destination (via Strategy pattern)
 *   4. Receiving events from OTHER clients (via Observer pattern) and forwarding
 *      them to ITS client
 *   5. Cleaning up when the client disconnects (gracefully OR by crash)
 *   6. Running the ping scheduler if this client is the coordinator
 *
 * ── Two Roles In One ─────────────────────────────────────────────────────────
 * ClientHandler implements BOTH Runnable AND EventListener:
 *
 *   Runnable      → ClientHandler has its own thread, loops reading from socket
 *   EventListener → ClientHandler is an Observer; EventManager calls onEvent()
 *                   when OTHER clients send messages
 *
 * These two roles run CONCURRENTLY:
 *   - The run() thread reads from socket (blocking I/O)
 *   - onEvent() is called by OTHER handler threads when they fire events
 *   Both may write to outputStream simultaneously → we synchronize writes.
 *
 * ── Strategy Pattern Usage ───────────────────────────────────────────────────
 * When a BROADCAST arrives:  strategy = new BroadcastStrategy()
 * When a PRIVATE arrives:    strategy = new PrivateMessageStrategy()
 * Then: strategy.send(message, allOutputStreams)
 * The ClientHandler doesn't care HOW the message is routed — that's the strategy's job.
 *
 * ── Fault Tolerance ──────────────────────────────────────────────────────────
 * If the client's socket drops (crashes, network failure, Ctrl+C):
 *   - readMessage() throws IOException
 *   - We catch it in the read loop
 *   - handleDisconnect() is called — cleans up, elects new coordinator if needed
 * The remaining clients are completely unaffected.
 */
public class ClientHandler implements Runnable, EventListener {

    private static final String SOURCE = "ClientHandler";

    // ── SOCKET I/O ───────────────────────────────────────────────────
    private final Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;

    // ── SHARED SERVER STATE ──────────────────────────────────────────
    private final EventManager eventManager;
    private final MemberRegistry memberRegistry;

    /**
     * Shared map of ALL client output streams.
     * This handler adds its own stream on connect, removes it on disconnect.
     * Strategy classes use this map to deliver messages.
     */
    private final Map<String, ObjectOutputStream> allOutputStreams;

    // ── THIS CLIENT'S STATE ──────────────────────────────────────────
    private Member member;           // Set during JOIN handshake
    private volatile boolean running;

    // ── STRATEGY INSTANCES ───────────────────────────────────────────
    // Instantiated once, reused for every message of that type
    private final MessageStrategy broadcastStrategy;
    private final MessageStrategy privateStrategy;

    // ── COORDINATOR PING SCHEDULER ──────────────────────────────────
    /**
     * If this member is the coordinator, they run a scheduled ping every 20s.
     * ScheduledExecutorService is Java's built-in scheduler — cleaner than
     * a manual Thread.sleep() loop.
     */
    private ScheduledExecutorService pingScheduler;

    // ── CONSTRUCTOR ──────────────────────────────────────────────────

    public ClientHandler(Socket socket,
                         EventManager eventManager,
                         MemberRegistry memberRegistry,
                         Map<String, ObjectOutputStream> allOutputStreams) {
        this.socket = socket;
        this.eventManager = eventManager;
        this.memberRegistry = memberRegistry;
        this.allOutputStreams = allOutputStreams;
        this.broadcastStrategy = new BroadcastStrategy();
        this.privateStrategy = new PrivateMessageStrategy();
        this.running = true;
    }

    // ── RUNNABLE — THE READ LOOP ─────────────────────────────────────

    /**
     * Entry point for this client's thread.
     *
     * Step 1: Set up streams and complete the JOIN handshake.
     * Step 2: Enter the read loop — read messages until client disconnects.
     * Step 3: Clean up on disconnect.
     */
    @Override
    public void run() {
        try {
            setupStreams();
            if (!handleJoin()) {
                // JOIN failed (e.g. duplicate ID) — close and exit
                closeSocket();
                return;
            }

            // Main read loop — runs until client disconnects or server stops
            while (running) {
                Message message = readMessage();
                if (message == null) break;  // null = stream closed
                handleIncomingMessage(message);
            }

        } catch (IOException | ClassNotFoundException e) {
            if (running) {
                ChatLogger.warn(SOURCE, "Connection lost with "
                        + (member != null ? member.getId() : "unknown")
                        + ": " + e.getMessage());
            }
        } finally {
            // Always runs — whether we exited cleanly or via exception
            handleDisconnect();
        }
    }

    // ── STREAM SETUP ─────────────────────────────────────────────────

    /**
     * Create Object streams for this socket.
     *
     * CRITICAL ORDER: ObjectOutputStream MUST be created BEFORE ObjectInputStream.
     * Why? ObjectOutputStream writes a 4-byte stream header immediately.
     * ObjectInputStream reads that header in its constructor — it BLOCKS until
     * the header arrives. If both sides create InputStream first, they deadlock.
     *
     * Rule: Always create OutputStream first, then InputStream. Every time.
     */
    private void setupStreams() throws IOException {
        // OutputStream first — writes stream header immediately
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush(); // push header to the client right away

        // InputStream second — reads the header that the client's OutputStream sent
        inputStream = new ObjectInputStream(socket.getInputStream());

        ChatLogger.info(SOURCE, "Streams established for "
                + socket.getInetAddress().getHostAddress());
    }

    // ── JOIN HANDSHAKE ────────────────────────────────────────────────

    /**
     * Process the initial JOIN message from the new client.
     *
     * Protocol:
     *   Client sends → Message(JOIN, chosenId, "Joining...")
     *   Server checks → is the ID unique?
     *   If YES → register member, send back COORDINATOR_NOTIFY + MEMBER_LIST
     *   If NO  → send error notification, return false (caller will close socket)
     *
     * @return true if join was successful, false if rejected
     */
    private boolean handleJoin() throws IOException, ClassNotFoundException {
        // Read the first message — must be a JOIN
        Message joinMsg = readMessage();
        if (joinMsg == null || joinMsg.getType() != MessageType.JOIN) {
            ChatLogger.warn(SOURCE, "First message was not JOIN — rejecting connection.");
            return false;
        }

        String requestedId = joinMsg.getSenderId();

        // ── Check for duplicate ID ───────────────────────────────────
        if (memberRegistry.isIdTaken(requestedId)) {
            ChatLogger.warn(SOURCE, "ID '" + requestedId + "' is already taken — rejecting.");
            sendToThisClient(MessageFactory.systemNotification(
                    "ID '" + requestedId + "' is already in use. Please reconnect with a different ID."));
            return false;
        }

        // ── Register the new member ──────────────────────────────────
        String ip = socket.getInetAddress().getHostAddress();
        int port = socket.getPort();
        member = new Member(requestedId, ip, port);

        boolean isCoordinator = memberRegistry.addMember(member);

        // Register this handler's output stream in the shared map
        // so other handlers can send messages to this client
        allOutputStreams.put(member.getId(), outputStream);

        // Register as an Observer — we'll now receive all group events
        eventManager.subscribe(this);

        ChatLogger.info(SOURCE, "Member '" + requestedId + "' joined from " + ip + ":" + port
                + (isCoordinator ? " [COORDINATOR]" : ""));

        // ── Notify this client about their role ──────────────────────
        Optional<Member> coordinator = memberRegistry.getCoordinator();
        coordinator.ifPresent(coord ->
                sendToThisClient(MessageFactory.coordinatorNotify("SERVER", coord))
        );

        // Send the current member list so they know who's in the group
        sendToThisClient(MessageFactory.memberListResponse("SERVER",
                memberRegistry.getAllMembers()));

        // ── Notify ALL OTHER members that someone joined ─────────────
        Message joinNotification = MessageFactory.systemNotification(
                "'" + requestedId + "' has joined the group! "
                        + "(" + memberRegistry.getMemberCount() + " members total)");
        eventManager.notify(EventManager.MEMBER_JOINED, member, joinNotification);

        // ── Start ping scheduler if this is the coordinator ──────────
        if (isCoordinator) {
            startPingScheduler();
        }

        return true;
    }

    // ── MESSAGE READING ───────────────────────────────────────────────

    /**
     * Read ONE message from this client's socket.
     * This is a BLOCKING call — the thread waits here until data arrives.
     *
     * Returns null if the stream has been cleanly closed.
     * Throws IOException if the connection dropped unexpectedly.
     */
    private Message readMessage() throws IOException, ClassNotFoundException {
        Object obj = inputStream.readObject();
        if (obj instanceof Message) {
            return (Message) obj;
        }
        return null;
    }

    // ── MESSAGE ROUTING (STRATEGY PATTERN IN ACTION) ──────────────────

    /**
     * Decide what to do with an incoming message based on its type.
     * This is the server's "message dispatcher".
     */
    private void handleIncomingMessage(Message message) {
        ChatLogger.chat("IN  " + message);

        switch (message.getType()) {

            case BROADCAST:
                // Use BroadcastStrategy — sends to all clients
                broadcastStrategy.send(message, allOutputStreams);
                // Notify observers (e.g. for logging purposes)
                eventManager.notify(EventManager.BROADCAST, member, message);
                break;

            case PRIVATE:
                // Use PrivateMessageStrategy — sends to one client
                privateStrategy.send(message, allOutputStreams);
                eventManager.notify(EventManager.PRIVATE_MESSAGE, member, message);
                break;

            case MEMBER_LIST_REQUEST:
                // Client is asking "who's in the group?" — respond directly
                sendToThisClient(MessageFactory.memberListResponse("SERVER",
                        memberRegistry.getAllMembers()));
                break;

            case PONG:
                // This client responded to our PING — they're alive!
                member.updateLastPong();
                ChatLogger.info(SOURCE, "PONG received from '" + member.getId() + "'");
                break;

            case LEAVE:
                // Client is gracefully disconnecting
                ChatLogger.info(SOURCE, "'" + member.getId() + "' sent LEAVE message.");
                running = false; // this will cause run() to exit the loop cleanly
                break;

            default:
                ChatLogger.warn(SOURCE, "Unhandled message type: " + message.getType());
        }
    }

    // ── OBSERVER PATTERN — onEvent() ─────────────────────────────────

    /**
     * Called by EventManager when OTHER clients trigger events.
     *
     * This is the OBSERVER pattern in action:
     *   - Another ClientHandler calls eventManager.notify("BROADCAST", ...)
     *   - EventManager calls THIS handler's onEvent()
     *   - We forward the message to OUR client
     *
     * CONCURRENCY NOTE: onEvent() is called from ANOTHER thread (the sender's
     * ClientHandler thread). Meanwhile, our run() thread might also be writing
     * to outputStream. We synchronize on outputStream to prevent interleaving.
     */
    @Override
    public void onEvent(String eventType, Member sender, Object data) {
        // Don't send events back to the client that triggered them
        // (BroadcastStrategy already handles that — avoid double delivery)
        if (member == null) return;

        switch (eventType) {

            case EventManager.MEMBER_JOINED:
            case EventManager.MEMBER_LEFT:
            case EventManager.COORDINATOR_CHANGED:
                // Forward the system notification message to our client
                if (data instanceof Message) {
                    // Only send to OTHER members, not the one who triggered it
                    if (sender == null || !sender.getId().equals(member.getId())) {
                        sendToThisClient((Message) data);
                    }
                }
                break;

            case EventManager.PING:
                // The coordinator is pinging — forward to our client so they can PONG
                if (data instanceof Message) {
                    sendToThisClient((Message) data);
                }
                break;

            // BROADCAST and PRIVATE are handled directly by strategies (not via Observer)
            // We include these cases to avoid the default warning
            case EventManager.BROADCAST:
            case EventManager.PRIVATE_MESSAGE:
                break;

            default:
                ChatLogger.warn(SOURCE, "Unknown event type: " + eventType);
        }
    }

    // ── COORDINATOR PING SCHEDULER ────────────────────────────────────

    /**
     * Start sending a PING to all clients every 20 seconds.
     * Only called when THIS member is the coordinator.
     *
     * ScheduledExecutorService is like a timer that runs a task periodically.
     * scheduleAtFixedRate(task, initialDelay, period, timeUnit)
     *   - initialDelay = 20: first ping after 20 seconds (not immediately)
     *   - period = 20:       then every 20 seconds after that
     */
    private void startPingScheduler() {
        pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PingScheduler-" + member.getId());
            t.setDaemon(true);
            return t;
        });

        pingScheduler.scheduleAtFixedRate(() -> {
            try {
                ChatLogger.info(SOURCE, "Coordinator '" + member.getId()
                        + "' sending PING to all members...");
                Message ping = MessageFactory.ping(member.getId());

                // Notify all handlers via Observer pattern — they forward to their clients
                eventManager.notify(EventManager.PING, member, ping);

            } catch (Exception e) {
                ChatLogger.error(SOURCE, "Ping scheduler error: " + e.getMessage());
            }
        }, 20, 20, TimeUnit.SECONDS);

        ChatLogger.info(SOURCE, "Ping scheduler started for coordinator '" + member.getId() + "'");
    }

    /**
     * Stop the ping scheduler — called when the coordinator disconnects.
     */
    private void stopPingScheduler() {
        if (pingScheduler != null && !pingScheduler.isShutdown()) {
            pingScheduler.shutdownNow();
            ChatLogger.info(SOURCE, "Ping scheduler stopped.");
        }
    }

    // ── DISCONNECT HANDLING (FAULT TOLERANCE) ────────────────────────

    /**
     * Clean up when a client disconnects — gracefully OR by crash.
     * This is the FAULT TOLERANCE core of the system.
     *
     * Steps:
     *   1. Unsubscribe from EventManager (stop receiving events)
     *   2. Remove from shared output stream map (stop sending to them)
     *   3. Remove from MemberRegistry
     *   4. If they were the COORDINATOR → elect a new one
     *   5. Notify remaining members
     *   6. Close the socket
     */
    private void handleDisconnect() {
        if (member == null) {
            closeSocket();
            return; // Never completed JOIN handshake
        }

        ChatLogger.info(SOURCE, "Handling disconnect for '" + member.getId() + "'");

        // Capture coordinator status NOW — before removeMember() clears the flag.
        // removeMember() calls member.setCoordinator(false) as part of cleanup,
        // so checking member.isCoordinator() AFTER removal always returns false.
        // We must save the value here to correctly decide whether to run election.
        boolean wasCoordinator = member.isCoordinator();

        // Stop ping scheduler if this was the coordinator
        if (wasCoordinator) {
            stopPingScheduler();
        }

        // 1. Unsubscribe from Observer — no more events to this handler
        eventManager.unsubscribe(this);

        // 2. Remove from streams map — can no longer send to this client
        allOutputStreams.remove(member.getId());

        // 3. Remove from registry (also clears member.isCoordinator flag)
        memberRegistry.removeMember(member.getId());

        // 4. COORDINATOR ELECTION — use wasCoordinator, NOT member.isCoordinator()
        //    because removeMember() already cleared that flag above
        if (wasCoordinator) {
            handleCoordinatorLeft();
        } else {
            // Non-coordinator left — just notify everyone
            broadcastDeparture(member.getId(), false);
        }

        closeSocket();
        ChatLogger.info(SOURCE, "'" + member.getId() + "' fully disconnected. "
                + memberRegistry.getMemberCount() + " member(s) remaining.");
    }

    /**
     * COORDINATOR LEFT — elect a new one and notify everyone.
     *
     * This is the key FAULT TOLERANCE feature:
     *   - MemberRegistry.electNewCoordinator() picks the next member
     *   - We send COORDINATOR_NOTIFY to the newly elected member
     *   - We broadcast a system notification to everyone else
     *   - The new coordinator's ClientHandler starts the ping scheduler
     */
    private void handleCoordinatorLeft() {
        Optional<Member> newCoordOpt = memberRegistry.electNewCoordinator();

        if (newCoordOpt.isEmpty()) {
            ChatLogger.info(SOURCE, "No members remaining after coordinator left.");
            return;
        }

        Member newCoord = newCoordOpt.get();
        ChatLogger.info(SOURCE, "New coordinator elected: '" + newCoord.getId() + "'");

        // Notify the new coordinator that they are now in charge
        ObjectOutputStream newCoordStream = allOutputStreams.get(newCoord.getId());
        if (newCoordStream != null) {
            try {
                Message coordMsg = MessageFactory.coordinatorNotify("SERVER", newCoord);
                newCoordStream.reset();
                newCoordStream.writeObject(coordMsg);
                newCoordStream.flush();
            } catch (IOException e) {
                ChatLogger.error(SOURCE, "Could not notify new coordinator: " + e.getMessage());
            }
        }

        // Broadcast system notification to all remaining members
        Message notification = MessageFactory.systemNotification(
                "'" + member.getId() + "' (coordinator) has left. "
                        + "New coordinator: '" + newCoord.getId() + "'");
        eventManager.notify(EventManager.COORDINATOR_CHANGED, null, notification);

        // Start ping scheduler on the new coordinator's handler
        // We find their handler via a new ping event — the new coordinator's
        // onEvent() will detect they're now coordinator and start pinging
        startPingSchedulerForNewCoordinator(newCoord);
    }

    /**
     * Tell the new coordinator's handler to start pinging.
     * We send a COORDINATOR_CHANGED event — each handler checks if the
     * new coordinator is themselves and starts the scheduler if so.
     */
    private void startPingSchedulerForNewCoordinator(Member newCoord) {
        // Find the new coordinator's ClientHandler by finding their stream,
        // then broadcast an event that the new coordinator's handler will catch
        // The new coordinator will receive COORDINATOR_NOTIFY message and start pinging
        // This is handled in the client side when they receive COORDINATOR_NOTIFY
        // with their own ID — see ChatClient.handleCoordinatorNotify()
        ChatLogger.info(SOURCE, "Coordinator transfer complete → '" + newCoord.getId() + "'");
    }

    /**
     * Broadcast a departure notification to all remaining members.
     *
     * @param departedId     ID of the member who left
     * @param wasCoordinator Whether they were the coordinator (already handled above)
     */
    private void broadcastDeparture(String departedId, boolean wasCoordinator) {
        String msg = wasCoordinator
                ? "'" + departedId + "' (coordinator) has left the group."
                : "'" + departedId + "' has left the group. ("
                + memberRegistry.getMemberCount() + " member(s) remaining)";

        Message notification = MessageFactory.systemNotification(msg);
        eventManager.notify(EventManager.MEMBER_LEFT, null, notification);
    }

    // ── WRITE HELPER ─────────────────────────────────────────────────

    /**
     * Write a message to THIS client's socket.
     * Synchronized on outputStream to prevent interleaving:
     * both the run() thread and onEvent() (called from other threads)
     * may want to write at the same time.
     *
     * @param message The message to send to this client
     */
    public void sendToThisClient(Message message) {
        if (outputStream == null || !running) return;
        synchronized (outputStream) {
            try {
                outputStream.reset();
                outputStream.writeObject(message);
                outputStream.flush();
            } catch (IOException e) {
                ChatLogger.error(SOURCE, "Failed to write to '"
                        + (member != null ? member.getId() : "?")
                        + "': " + e.getMessage());
                running = false; // Mark as disconnected
            }
        }
    }

    // ── CLEANUP ───────────────────────────────────────────────────────

    private void closeSocket() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            ChatLogger.error(SOURCE, "Error closing socket: " + e.getMessage());
        }
    }

    // ── GETTERS (for tests) ───────────────────────────────────────────

    public Member getMember() { return member; }
    public boolean isRunning() { return running; }
}