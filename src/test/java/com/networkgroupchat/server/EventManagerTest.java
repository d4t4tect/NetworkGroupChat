package com.networkgroupchat.server;

import com.networkgroupchat.model.Member;
import com.networkgroupchat.pattern.observer.EventListener;
import com.networkgroupchat.pattern.observer.EventManager;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;

/**
 * UNIT TESTS — EventManagerTest
 *
 * ── What we're testing ───────────────────────────────────────────────────────
 * The Observer pattern: subscribe, unsubscribe, and notify.
 *
 * ── How we test Observer without real ClientHandlers ────────────────────────
 * We use a FAKE (test double) implementation of EventListener called
 * RecordingListener. It records every event it receives — then we
 * assert on what was recorded.
 *
 * This is a fundamental testing technique: instead of testing with real
 * ClientHandlers (which need sockets, threads, etc.), we create a simple
 * stand-in that only does what the test needs.
 *
 * In proper Mockito we'd use mock(EventListener.class) — but this manual
 * approach shows you the concept just as clearly.
 */
@DisplayName("EventManager Tests (Observer Pattern)")
class EventManagerTest {

    /**
     * TEST DOUBLE — RecordingListener
     *
     * A simple implementation of EventListener that records every event
     * it receives. Tests can then inspect what events were delivered.
     *
     * This is a "Spy" or "Fake" in testing terminology.
     */
    static class RecordingListener implements EventListener {
        final List<String> receivedEventTypes = new ArrayList<>();
        final List<Object> receivedData       = new ArrayList<>();
        int callCount = 0;

        @Override
        public void onEvent(String eventType, Member sender, Object data) {
            callCount++;
            receivedEventTypes.add(eventType);
            receivedData.add(data);
        }

        /** Convenience: did this listener receive a specific event type? */
        boolean receivedEventType(String type) {
            return receivedEventTypes.contains(type);
        }
    }

    private EventManager eventManager;
    private RecordingListener listenerA;
    private RecordingListener listenerB;

    @BeforeEach
    void setUp() {
        eventManager = new EventManager();
        listenerA = new RecordingListener();
        listenerB = new RecordingListener();
    }

    // ── SUBSCRIBE TESTS ──────────────────────────────────────────────

    @Test
    @DisplayName("subscribe() increases listener count")
    void testSubscribeIncreasesCount() {
        assertEquals(0, eventManager.getListenerCount(), "Should start with no listeners");

        eventManager.subscribe(listenerA);
        assertEquals(1, eventManager.getListenerCount());

        eventManager.subscribe(listenerB);
        assertEquals(2, eventManager.getListenerCount());
    }

    // ── UNSUBSCRIBE TESTS ────────────────────────────────────────────

    @Test
    @DisplayName("unsubscribe() decreases listener count")
    void testUnsubscribeDecreasesCount() {
        eventManager.subscribe(listenerA);
        eventManager.subscribe(listenerB);
        assertEquals(2, eventManager.getListenerCount());

        eventManager.unsubscribe(listenerA);
        assertEquals(1, eventManager.getListenerCount());
    }

    @Test
    @DisplayName("Unsubscribed listener receives NO further events")
    void testUnsubscribedListenerReceivesNoEvents() {
        // Arrange — subscribe then immediately unsubscribe
        eventManager.subscribe(listenerA);
        eventManager.unsubscribe(listenerA);

        // Act — fire an event
        eventManager.notify(EventManager.BROADCAST, "test payload");

        // Assert — listenerA should have received nothing
        assertEquals(0, listenerA.callCount,
                "Unsubscribed listener should not receive any events");
    }

    // ── NOTIFY TESTS ─────────────────────────────────────────────────

    @Test
    @DisplayName("notify() calls onEvent() on ALL subscribed listeners")
    void testNotifyCallsAllListeners() {
        // Arrange
        eventManager.subscribe(listenerA);
        eventManager.subscribe(listenerB);

        // Act
        eventManager.notify(EventManager.BROADCAST, null, "Hello!");

        // Assert — both listeners called exactly once
        assertEquals(1, listenerA.callCount, "listenerA should be called once");
        assertEquals(1, listenerB.callCount, "listenerB should be called once");
    }

    @Test
    @DisplayName("notify() delivers the correct event type string")
    void testNotifyDeliversCorrectEventType() {
        eventManager.subscribe(listenerA);
        eventManager.notify(EventManager.MEMBER_JOINED, null, "join data");

        assertTrue(listenerA.receivedEventType(EventManager.MEMBER_JOINED),
                "Listener should receive the MEMBER_JOINED event type");
    }

    @Test
    @DisplayName("notify() delivers the correct data payload")
    void testNotifyDeliversCorrectData() {
        eventManager.subscribe(listenerA);
        String payload = "test payload data";

        eventManager.notify(EventManager.BROADCAST, payload);

        assertFalse(listenerA.receivedData.isEmpty(), "Listener should have received data");
        assertEquals(payload, listenerA.receivedData.get(0),
                "Listener should receive the exact payload object");
    }

    @Test
    @DisplayName("notify() with no listeners does not throw")
    void testNotifyWithNoListenersDoesNotThrow() {
        // If nobody is subscribed, notify() must not crash
        // assertDoesNotThrow verifies no exception is thrown
        assertDoesNotThrow(() ->
                        eventManager.notify(EventManager.BROADCAST, "data"),
                "notify() with no listeners should not throw any exception"
        );
    }

    @Test
    @DisplayName("Notify fires multiple different event types independently")
    void testMultipleEventTypesFired() {
        eventManager.subscribe(listenerA);

        eventManager.notify(EventManager.MEMBER_JOINED,  "join");
        eventManager.notify(EventManager.BROADCAST,      "message");
        eventManager.notify(EventManager.MEMBER_LEFT,    "leave");

        // Assert — listener received all 3 events
        assertEquals(3, listenerA.callCount, "Listener should receive all 3 events");
        assertTrue(listenerA.receivedEventType(EventManager.MEMBER_JOINED));
        assertTrue(listenerA.receivedEventType(EventManager.BROADCAST));
        assertTrue(listenerA.receivedEventType(EventManager.MEMBER_LEFT));
    }

    // ── FAULT TOLERANCE IN EVENT DELIVERY ────────────────────────────

    @Test
    @DisplayName("A crashing listener does not prevent other listeners from receiving events")
    void testCrashingListenerDoesNotBlockOthers() {
        // Arrange — one listener that always throws, one that works fine
        EventListener crashingListener = (type, sender, data) -> {
            throw new RuntimeException("Simulated listener crash!");
        };

        eventManager.subscribe(crashingListener);
        eventManager.subscribe(listenerA); // listenerA is healthy

        // Act — fire an event (crashingListener will throw)
        // EventManager must catch the exception and still notify listenerA
        assertDoesNotThrow(() ->
                        eventManager.notify(EventManager.BROADCAST, "test"),
                "EventManager should handle crashing listeners gracefully"
        );

        // Assert — listenerA still received the event despite the crash
        assertEquals(1, listenerA.callCount,
                "Healthy listener should still receive event even if another listener crashed");
    }

    // ── EDGE CASES ────────────────────────────────────────────────────

    @Test
    @DisplayName("Subscribing the same listener twice results in double notification")
    void testDoubleSubscribeNotifiesTwice() {
        // Note: this tests ACTUAL behaviour — we don't deduplicate subscriptions.
        // This is documented behaviour: don't subscribe the same listener twice.
        eventManager.subscribe(listenerA);
        eventManager.subscribe(listenerA); // intentionally double

        eventManager.notify(EventManager.BROADCAST, "test");

        assertEquals(2, listenerA.callCount,
                "Double-subscribed listener is called twice (expected behaviour)");
    }
}