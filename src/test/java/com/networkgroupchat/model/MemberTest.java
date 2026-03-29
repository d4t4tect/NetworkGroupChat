package com.networkgroupchat.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UNIT TESTS — MemberTest
 *
 * ── What we're testing ───────────────────────────────────────────────────────
 * The Member class is the foundation of the whole system — if it behaves
 * incorrectly, everything built on top of it is unreliable.
 * We test construction, equality, coordinator state, and pong tracking.
 *
 * ── Testing strategy ─────────────────────────────────────────────────────────
 * These are UNIT tests — they test ONE class in complete isolation.
 * No networking, no server, no other classes involved.
 * Fast, reliable, and easy to understand.
 *
 * ── AAA Pattern ──────────────────────────────────────────────────────────────
 * Every test follows: Arrange → Act → Assert
 */
@DisplayName("Member Model Tests")
class MemberTest {

    // ── Test fixtures — shared test data ────────────────────────────
    private Member alice;
    private Member bob;

    /**
     * @BeforeEach runs BEFORE every single test method.
     * Creates fresh Member objects for each test so they don't share state.
     * Without this, a test that modifies a Member could break the next test.
     */
    @BeforeEach
    void setUp() {
        alice = new Member("Alice", "127.0.0.1", 52000);
        bob   = new Member("Bob",   "127.0.0.1", 52001);
    }

    // ── CONSTRUCTION TESTS ───────────────────────────────────────────

    @Test
    @DisplayName("New member stores ID, IP, and port correctly")
    void testMemberCreation() {
        // Assert — verify all fields were set correctly in the constructor
        assertEquals("Alice", alice.getId(),
                "ID should match what was passed to constructor");
        assertEquals("127.0.0.1", alice.getIpAddress(),
                "IP should match what was passed to constructor");
        assertEquals(52000, alice.getPort(),
                "Port should match what was passed to constructor");
    }

    @Test
    @DisplayName("New member is NOT coordinator by default")
    void testNewMemberIsNotCoordinatorByDefault() {
        // A brand-new Member should never start as coordinator.
        // The MemberRegistry decides who is coordinator — not the Member constructor.
        assertFalse(alice.isCoordinator(),
                "Member should not be coordinator until explicitly set");
    }

    @Test
    @DisplayName("Member has a non-null joinedAt timestamp")
    void testJoinedAtIsSetOnCreation() {
        // Verifies the joinedAt field is set in the constructor (not left null)
        assertNotNull(alice.getJoinedAt(),
                "joinedAt should be set when Member is created");
    }

    // ── COORDINATOR STATE TESTS ──────────────────────────────────────

    @Test
    @DisplayName("setCoordinator(true) makes member the coordinator")
    void testSetCoordinatorTrue() {
        // Arrange — alice starts as non-coordinator (from setUp)
        assertFalse(alice.isCoordinator()); // precondition

        // Act
        alice.setCoordinator(true);

        // Assert
        assertTrue(alice.isCoordinator(),
                "isCoordinator() should return true after setCoordinator(true)");
    }

    @Test
    @DisplayName("setCoordinator(false) demotes coordinator back to member")
    void testSetCoordinatorFalse() {
        // Arrange — make alice coordinator first
        alice.setCoordinator(true);
        assertTrue(alice.isCoordinator()); // precondition

        // Act
        alice.setCoordinator(false);

        // Assert
        assertFalse(alice.isCoordinator(),
                "isCoordinator() should return false after setCoordinator(false)");
    }

    // ── PONG / HEARTBEAT TESTS ───────────────────────────────────────

    @Test
    @DisplayName("updateLastPong() updates the lastPongReceived timestamp")
    void testUpdateLastPong() throws InterruptedException {
        // Arrange — record the current lastPong time
        var beforeUpdate = alice.getLastPongReceived();

        // Act — wait 10ms then update (so the new timestamp is definitely later)
        Thread.sleep(10);
        alice.updateLastPong();

        // Assert — the new timestamp must be AFTER the old one
        assertTrue(alice.getLastPongReceived().isAfter(beforeUpdate),
                "lastPongReceived should be updated to a later time after updateLastPong()");
    }

    @Test
    @DisplayName("lastPongReceived is non-null on fresh member")
    void testLastPongReceivedInitialized() {
        // The lastPongReceived is initialized to now() in the constructor.
        // This means a brand-new member is assumed alive — correct behaviour.
        assertNotNull(alice.getLastPongReceived(),
                "lastPongReceived should be initialized on construction");
    }

    // ── EQUALITY TESTS ───────────────────────────────────────────────

    @Test
    @DisplayName("Two Members with the same ID are equal")
    void testEqualityById() {
        // Arrange — create a second Member with the same ID but different port
        Member aliceDuplicate = new Member("Alice", "192.168.1.1", 99999);

        // Assert — equals() is based on ID only, not IP or port
        assertEquals(alice, aliceDuplicate,
                "Members with the same ID should be equal regardless of IP/port");
    }

    @Test
    @DisplayName("Two Members with different IDs are not equal")
    void testInequalityByDifferentId() {
        assertNotEquals(alice, bob,
                "Members with different IDs should not be equal");
    }

    @Test
    @DisplayName("hashCode is consistent with equals (same ID = same hash)")
    void testHashCodeConsistency() {
        // Java contract: if a.equals(b), then a.hashCode() == b.hashCode()
        // This is required for correct behaviour in HashSets and HashMaps
        Member aliceDuplicate = new Member("Alice", "10.0.0.1", 1234);
        assertEquals(alice.hashCode(), aliceDuplicate.hashCode(),
                "Equal objects must have equal hash codes");
    }

    // ── toString TEST ────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes ID and role label")
    void testToStringContainsIdAndRole() {
        alice.setCoordinator(true);
        String str = alice.toString();

        // We don't test the EXACT format (it might change) —
        // we just verify the key info is present
        assertTrue(str.contains("Alice"),        "toString should contain the member ID");
        assertTrue(str.contains("COORDINATOR"),  "toString should indicate coordinator role");
    }
}