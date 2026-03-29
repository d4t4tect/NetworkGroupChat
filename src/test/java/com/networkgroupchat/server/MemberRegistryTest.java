package com.networkgroupchat.server;

import com.networkgroupchat.model.Member;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;

/**
 * UNIT TESTS — MemberRegistryTest
 *
 * ── Why this is the most important test class ────────────────────────────────
 * MemberRegistry controls:
 *   - Who is in the group (add/remove)
 *   - Who is the coordinator (election logic)
 *   - Whether IDs are unique
 *
 * Bugs here break the ENTIRE system. We test every path thoroughly.
 *
 * ── TRICKY ISSUE: Singleton in tests ────────────────────────────────────────
 * MemberRegistry is a Singleton — there's only ONE instance per JVM.
 * If Test A adds 3 members and Test B runs next, it would see those leftover
 * members and FAIL for no apparent reason. Tests must be ISOLATED.
 *
 * Solution: call registry.clear() in @BeforeEach.
 * We added a clear() method specifically for testing — it's documented as
 * "test-only" in production code. This is a common, accepted pattern.
 */
@DisplayName("MemberRegistry Tests (Singleton + Coordinator Election)")
class MemberRegistryTest {

    private MemberRegistry registry;

    /**
     * @BeforeEach: get the singleton AND clear it so each test starts empty.
     * If we didn't clear, member state from one test would bleed into the next.
     */
    @BeforeEach
    void setUp() {
        registry = MemberRegistry.getInstance();
        registry.clear(); // CRITICAL: reset state between tests
    }

    // ── SINGLETON TESTS ──────────────────────────────────────────────

    @Test
    @DisplayName("getInstance() always returns the same object reference")
    void testSingletonReturnsSameInstance() {
        // The whole point of Singleton: one and only one instance
        MemberRegistry first  = MemberRegistry.getInstance();
        MemberRegistry second = MemberRegistry.getInstance();

        // assertSame checks REFERENCE equality (same object in memory)
        // not just .equals() — we want the exact same instance
        assertSame(first, second,
                "getInstance() must always return the exact same instance");
    }

    @Test
    @DisplayName("Multiple getInstance() calls all return the same object")
    void testSingletonConsistentAcrossManyCalls() {
        MemberRegistry a = MemberRegistry.getInstance();
        MemberRegistry b = MemberRegistry.getInstance();
        MemberRegistry c = MemberRegistry.getInstance();

        assertSame(a, b);
        assertSame(b, c);
    }

    // ── ADD MEMBER TESTS ─────────────────────────────────────────────

    @Test
    @DisplayName("First member added becomes coordinator automatically")
    void testFirstMemberBecomesCoordinator() {
        // Arrange
        Member alice = new Member("Alice", "127.0.0.1", 5001);

        // Act
        boolean isCoordinator = registry.addMember(alice);

        // Assert — addMember() returns true if this member became coordinator
        assertTrue(isCoordinator, "First member should become coordinator");
        assertTrue(alice.isCoordinator(), "First member's isCoordinator flag should be set");
    }

    @Test
    @DisplayName("Second and subsequent members are NOT coordinator")
    void testSubsequentMembersAreNotCoordinator() {
        // Arrange + Act
        Member alice = new Member("Alice", "127.0.0.1", 5001);
        Member bob   = new Member("Bob",   "127.0.0.1", 5002);

        registry.addMember(alice); // first → coordinator
        boolean bobIsCoord = registry.addMember(bob); // second → not coordinator

        // Assert
        assertFalse(bobIsCoord, "Second member should not be coordinator");
        assertFalse(bob.isCoordinator(), "Bob's coordinator flag should remain false");
        assertTrue(alice.isCoordinator(), "Alice should still be coordinator");
    }

    @Test
    @DisplayName("getMemberCount() increases as members join")
    void testMemberCountIncrementsOnAdd() {
        assertEquals(0, registry.getMemberCount(), "Should start empty");

        registry.addMember(new Member("Alice", "127.0.0.1", 5001));
        assertEquals(1, registry.getMemberCount());

        registry.addMember(new Member("Bob",   "127.0.0.1", 5002));
        assertEquals(2, registry.getMemberCount());

        registry.addMember(new Member("Charlie", "127.0.0.1", 5003));
        assertEquals(3, registry.getMemberCount());
    }

    // ── REMOVE MEMBER TESTS ──────────────────────────────────────────

    @Test
    @DisplayName("removeMember() removes the member and returns them")
    void testRemoveMemberReturnsRemovedMember() {
        // Arrange
        Member alice = new Member("Alice", "127.0.0.1", 5001);
        registry.addMember(alice);

        // Act
        Optional<Member> removed = registry.removeMember("Alice");

        // Assert
        assertTrue(removed.isPresent(), "Should return the removed member");
        assertEquals("Alice", removed.get().getId());
        assertEquals(0, registry.getMemberCount(), "Registry should be empty after removal");
    }

    @Test
    @DisplayName("removeMember() returns empty Optional for unknown ID")
    void testRemoveMemberUnknownIdReturnsEmpty() {
        // Arrange — registry is empty (from setUp clear)

        // Act
        Optional<Member> result = registry.removeMember("Nobody");

        // Assert — must return empty, not throw an exception
        assertFalse(result.isPresent(),
                "Removing an unknown member should return Optional.empty()");
    }

    @Test
    @DisplayName("getMemberCount() decreases after removal")
    void testMemberCountDecrementsOnRemove() {
        registry.addMember(new Member("Alice", "127.0.0.1", 5001));
        registry.addMember(new Member("Bob",   "127.0.0.1", 5002));
        assertEquals(2, registry.getMemberCount());

        registry.removeMember("Alice");
        assertEquals(1, registry.getMemberCount());
    }

    // ── findById TESTS ───────────────────────────────────────────────

    @Test
    @DisplayName("findById() finds an existing member")
    void testFindByIdFindsExistingMember() {
        registry.addMember(new Member("Alice", "127.0.0.1", 5001));

        Optional<Member> found = registry.findById("Alice");

        assertTrue(found.isPresent(), "Should find Alice in the registry");
        assertEquals("Alice", found.get().getId());
    }

    @Test
    @DisplayName("findById() returns empty Optional for non-existent ID")
    void testFindByIdReturnsEmptyForMissing() {
        Optional<Member> found = registry.findById("Nobody");

        assertFalse(found.isPresent(),
                "findById should return empty Optional for unknown ID");
    }

    // ── DUPLICATE ID TESTS ───────────────────────────────────────────

    @Test
    @DisplayName("isIdTaken() returns true when ID already exists")
    void testIsIdTakenReturnsTrueForExistingId() {
        registry.addMember(new Member("Alice", "127.0.0.1", 5001));

        assertTrue(registry.isIdTaken("Alice"),
                "isIdTaken() should return true for a registered ID");
    }

    @Test
    @DisplayName("isIdTaken() returns false when ID is not registered")
    void testIsIdTakenReturnsFalseForNewId() {
        assertFalse(registry.isIdTaken("NewPerson"),
                "isIdTaken() should return false for an unregistered ID");
    }

    // ── COORDINATOR ELECTION TESTS ───────────────────────────────────
    // This is the most critical section — coordinator election on disconnect

    @Test
    @DisplayName("getCoordinator() returns the coordinator member")
    void testGetCoordinatorReturnsCoordinator() {
        Member alice = new Member("Alice", "127.0.0.1", 5001);
        registry.addMember(alice); // first → becomes coordinator

        Optional<Member> coord = registry.getCoordinator();

        assertTrue(coord.isPresent(), "Coordinator should be found");
        assertEquals("Alice", coord.get().getId());
    }

    @Test
    @DisplayName("getCoordinator() returns empty when registry is empty")
    void testGetCoordinatorEmptyWhenNoMembers() {
        // Registry is empty from setUp
        Optional<Member> coord = registry.getCoordinator();
        assertFalse(coord.isPresent(),
                "getCoordinator() should return empty when no members exist");
    }

    @Test
    @DisplayName("ELECTION: When coordinator leaves, next member becomes coordinator")
    void testCoordinatorElectionAfterCoordinatorLeaves() {
        // Arrange — add 3 members (Alice = coordinator, Bob and Charlie = regular)
        Member alice   = new Member("Alice",   "127.0.0.1", 5001);
        Member bob     = new Member("Bob",     "127.0.0.1", 5002);
        Member charlie = new Member("Charlie", "127.0.0.1", 5003);

        registry.addMember(alice);   // first → coordinator
        registry.addMember(bob);
        registry.addMember(charlie);

        // Verify precondition
        assertTrue(alice.isCoordinator(), "Alice should be coordinator before test");

        // Act — Alice (coordinator) leaves; remove her, then elect new coordinator
        registry.removeMember("Alice");
        Optional<Member> newCoord = registry.electNewCoordinator();

        // Assert — Bob should become coordinator (he joined next after Alice)
        assertTrue(newCoord.isPresent(), "A new coordinator should be elected");
        assertEquals("Bob", newCoord.get().getId(),
                "Bob (who joined second) should become the new coordinator");
        assertTrue(bob.isCoordinator(), "Bob's isCoordinator flag should be set");
        assertFalse(charlie.isCoordinator(), "Charlie should NOT be coordinator");
    }

    @Test
    @DisplayName("ELECTION: electNewCoordinator() returns empty when no members remain")
    void testElectionWithNoRemainingMembers() {
        // Arrange — only one member, then they leave
        registry.addMember(new Member("Alice", "127.0.0.1", 5001));
        registry.removeMember("Alice");

        // Act
        Optional<Member> result = registry.electNewCoordinator();

        // Assert — nobody left to elect
        assertFalse(result.isPresent(),
                "Election should return empty when no members remain");
    }

    @Test
    @DisplayName("ELECTION: Non-coordinator leaving does NOT trigger election")
    void testNonCoordinatorLeavingDoesNotChangeCoordinator() {
        // Arrange
        Member alice = new Member("Alice", "127.0.0.1", 5001);
        Member bob   = new Member("Bob",   "127.0.0.1", 5002);
        registry.addMember(alice); // coordinator
        registry.addMember(bob);

        // Act — Bob (non-coordinator) leaves. No election needed.
        registry.removeMember("Bob");

        // Assert — Alice is still coordinator; no election was called
        Optional<Member> coord = registry.getCoordinator();
        assertTrue(coord.isPresent());
        assertEquals("Alice", coord.get().getId(),
                "Alice should remain coordinator when a non-coordinator leaves");
    }

    @Test
    @DisplayName("ELECTION: Each elected coordinator has flag set, others don't")
    void testElectionClearsOldCoordinatorFlags() {
        // Arrange — add members where Alice is coordinator
        Member alice   = new Member("Alice",   "127.0.0.1", 5001);
        Member bob     = new Member("Bob",     "127.0.0.1", 5002);
        Member charlie = new Member("Charlie", "127.0.0.1", 5003);

        registry.addMember(alice);
        registry.addMember(bob);
        registry.addMember(charlie);

        // Act — Alice leaves, Bob becomes coordinator. Bob also leaves.
        registry.removeMember("Alice");
        registry.electNewCoordinator(); // Bob elected
        registry.removeMember("Bob");
        Optional<Member> newCoord = registry.electNewCoordinator(); // Charlie elected

        // Assert — Charlie is coordinator, everyone else's flags are clear
        assertTrue(newCoord.isPresent());
        assertEquals("Charlie", newCoord.get().getId());
        assertTrue(charlie.isCoordinator());
        assertFalse(alice.isCoordinator(), "Alice's coordinator flag should be cleared");
        assertFalse(bob.isCoordinator(),   "Bob's coordinator flag should be cleared");
    }

    // ── getAllMembers() ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllMembers() returns a copy, not the live list")
    void testGetAllMembersReturnsCopy() {
        registry.addMember(new Member("Alice", "127.0.0.1", 5001));

        // Get the list and add to it — this should NOT affect the registry
        var copy = registry.getAllMembers();
        copy.add(new Member("Intruder", "10.0.0.1", 9999));

        // Assert — the registry should still only have Alice
        assertEquals(1, registry.getMemberCount(),
                "Modifying the returned list should not affect the registry");
    }
}