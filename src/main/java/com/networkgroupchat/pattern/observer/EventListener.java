package com.networkgroupchat.pattern.observer;

import com.networkgroupchat.model.Member;

/**
 * OBSERVER PATTERN — EventListener (the "Observer" interface)
 *
 * ── What is the Observer Pattern? ───────────────────────────────────────────
 * Observer defines a one-to-many dependency: when ONE object (the "Subject" or
 * "Publisher") changes state, ALL registered "Observers" are notified automatically.
 *
 * Real-world analogy: a YouTube channel (Subject) has subscribers (Observers).
 * When the channel posts a video, ALL subscribers are notified. The channel
 * doesn't need to know how many subscribers there are or what they'll do with
 * the notification — it just calls notify and they all receive it.
 *
 * ── In our system ───────────────────────────────────────────────────────────
 * Subject  = EventManager (holds the list of listeners, fires events)
 * Observer = anything implementing EventListener
 *
 * Currently, ClientHandler implements EventListener.
 * When a member joins, leaves, or sends a message, EventManager fires an event
 * and ALL registered ClientHandlers are notified — so they can forward the
 * message to their respective connected client.
 *
 * ── Why an interface? ───────────────────────────────────────────────────────
 * An interface means EventManager doesn't need to know what type of object
 * is listening — it just knows it has an onEvent() method. This is LOOSE COUPLING:
 * you could add a Logger, an AdminConsole, etc. as observers without changing
 * EventManager at all.
 */
public interface EventListener {

    /**
     * Called by EventManager when a relevant event occurs in the system.
     *
     * @param eventType A string label describing the event.
     *                  Defined constants are in EventManager:
     *                  e.g. "MEMBER_JOINED", "MEMBER_LEFT", "BROADCAST", "PRIVATE"
     *
     * @param sender    The Member who triggered the event.
     *                  e.g. for "MEMBER_JOINED", this is the new joiner.
     *                  e.g. for "BROADCAST", this is the message sender.
     *                  May be null for server-generated events.
     *
     * @param data      The event payload — what to DO with this notification.
     *                  For message events: the Message object to forward.
     *                  For join/leave events: the Member who joined/left.
     *                  Callers cast this to the expected type based on eventType.
     */
    void onEvent(String eventType, Member sender, Object data);
}