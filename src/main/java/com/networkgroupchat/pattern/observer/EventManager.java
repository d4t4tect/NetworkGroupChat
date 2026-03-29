package com.networkgroupchat.pattern.observer;

import com.networkgroupchat.model.Member;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OBSERVER PATTERN — EventManager (the "Subject" / "Publisher")
 *
 * ── Responsibility ───────────────────────────────────────────────────────────
 * EventManager is the hub of all event notifications in the server.
 * It maintains a list of EventListeners (observers) and notifies them all
 * when something meaningful happens (member joins, message sent, etc.).
 *
 * ── How it works ─────────────────────────────────────────────────────────────
 * 1. ClientHandler registers itself:  eventManager.subscribe(this)
 * 2. When a message arrives:          eventManager.notify("BROADCAST", sender, message)
 * 3. EventManager loops through all   listeners and calls listener.onEvent(...)
 * 4. Each ClientHandler's onEvent()   forwards the message to its own client socket
 *
 * ── Thread Safety ────────────────────────────────────────────────────────────
 * CopyOnWriteArrayList is used because:
 * - subscribe() and unsubscribe() can be called from any ClientHandler thread
 * - notify() iterates the list while handlers may be joining/leaving
 * CopyOnWriteArrayList is safe to iterate while being modified from other threads.
 *
 * ── Event Type Constants ─────────────────────────────────────────────────────
 * We define event types as public constants here (not in a separate Enum)
 * for simplicity. Callers use EventManager.MEMBER_JOINED etc. — no magic strings.
 */
public class EventManager {

    // ── EVENT TYPE CONSTANTS ─────────────────────────────────────────
    // Using constants means: if you rename an event, the compiler catches
    // every place it's used (unlike raw strings which silently break).

    /** A new member has connected and been registered */
    public static final String MEMBER_JOINED      = "MEMBER_JOINED";

    /** A member has disconnected (gracefully or by crash) */
    public static final String MEMBER_LEFT        = "MEMBER_LEFT";

    /** A broadcast message needs to be forwarded to all clients */
    public static final String BROADCAST          = "BROADCAST";

    /** A private message needs to be forwarded to one specific client */
    public static final String PRIVATE_MESSAGE    = "PRIVATE_MESSAGE";

    /** The coordinator has changed — clients need to be informed */
    public static final String COORDINATOR_CHANGED = "COORDINATOR_CHANGED";

    /** A ping needs to be sent to all clients (from coordinator timer) */
    public static final String PING               = "PING";

    // ── STATE ────────────────────────────────────────────────────────

    /** All currently registered observers (one per connected ClientHandler) */
    private final List<EventListener> listeners;

    // ── CONSTRUCTOR ──────────────────────────────────────────────────

    public EventManager() {
        this.listeners = new CopyOnWriteArrayList<>();
    }

    // ── SUBSCRIPTION MANAGEMENT ──────────────────────────────────────

    /**
     * Register a new observer.
     * Called by ClientHandler when a client connects.
     *
     * @param listener The object that wants to receive event notifications
     */
    public void subscribe(EventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove an observer.
     * Called by ClientHandler when a client disconnects.
     * After this, the listener will receive no more events.
     *
     * @param listener The object to stop notifying
     */
    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }

    /**
     * How many observers are currently registered?
     * Useful for assertions in tests.
     */
    public int getListenerCount() {
        return listeners.size();
    }

    // ── EVENT FIRING ─────────────────────────────────────────────────

    /**
     * Fire an event — notify ALL registered listeners.
     *
     * This is the core of the Observer pattern.
     * EventManager doesn't know or care WHAT the listeners do with the event.
     * It just delivers the notification to everyone.
     *
     * IMPORTANT: Each listener's onEvent() is called on the CALLING thread.
     * If a listener does slow work (like writing to a socket), it will block
     * this method. For our scale, this is acceptable. In production, you'd
     * dispatch events on separate threads.
     *
     * @param eventType One of the EVENT TYPE CONSTANTS defined above
     * @param sender    The Member who caused this event (may be null)
     * @param data      The event payload (cast inside each listener based on eventType)
     */
    public void notify(String eventType, Member sender, Object data) {
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(eventType, sender, data);
            } catch (Exception e) {
                // If ONE listener crashes, we don't want to stop notifying the others.
                // Log the error but continue. This is fault-tolerant event delivery.
                System.err.println("[EventManager] Listener error on event '"
                        + eventType + "': " + e.getMessage());
            }
        }
    }

    /**
     * Convenience overload — fire event with no sender (server-generated events).
     *
     * @param eventType The event type constant
     * @param data      The event payload
     */
    public void notify(String eventType, Object data) {
        notify(eventType, null, data);
    }
}