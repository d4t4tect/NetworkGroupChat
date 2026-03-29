package com.networkgroupchat.server;

import com.networkgroupchat.model.Member;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SINGLETON PATTERN — MemberRegistry
 *
 * ── What is the Singleton Pattern? ──────────────────────────────────────────
 * Singleton ensures that only ONE instance of a class can EVER exist.
 * Instead of calling 'new MemberRegistry()' (which could create many copies),
 * you always call MemberRegistry.getInstance() — which returns the SAME object
 * every time, no matter how many times you call it or from which thread.
 *
 * ── Why use it here? ────────────────────────────────────────────────────────
 * The member list is GLOBAL SERVER STATE — every ClientHandler thread must see
 * the same list. If each ClientHandler created its own MemberRegistry, they'd
 * each have a separate list and nothing would work correctly.
 *
 * ── Thread Safety ───────────────────────────────────────────────────────────
 * Multiple ClientHandler threads call addMember/removeMember simultaneously.
 * We use CopyOnWriteArrayList — a thread-safe List that creates a fresh copy
 * of the underlying array on every write. Reads are always safe and fast.
 * Perfect for our use case: we READ far more than we WRITE.
 *
 * ── The Double-Checked Locking Pattern ──────────────────────────────────────
 * The getInstance() method uses "double-checked locking" — the safest way to
 * implement a thread-safe singleton in Java:
 *   1. First check (no lock)  — fast path for when instance already exists
 *   2. synchronized block     — ensures only ONE thread creates the instance
 *   3. Second check (in lock) — in case two threads both passed step 1
 * The 'volatile' keyword on the field ensures the instance is fully initialized
 * before other threads can see it (prevents a subtle JVM reordering bug).
 */
public class MemberRegistry {

    // 'volatile' prevents the JVM from partially publishing an uninitialized object
    private static volatile MemberRegistry instance;

    /**
     * CopyOnWriteArrayList — thread-safe without needing explicit synchronization
     * on every read. Writes (add/remove) are slower but reads are very fast.
     * Since we read this list (to send messages, to find coordinator) far more
     * often than we write it (only on join/leave), this is the right choice.
     */
    private final List<Member> members;

    // ── PRIVATE CONSTRUCTOR ──────────────────────────────────────────
    // Private = nobody outside this class can call 'new MemberRegistry()'
    // This is the key to enforcing the Singleton pattern.
    private MemberRegistry() {
        members = new CopyOnWriteArrayList<>();
    }

    // ── SINGLETON ACCESS POINT ───────────────────────────────────────

    /**
     * The ONE way to get the MemberRegistry.
     * Thread-safe via double-checked locking.
     *
     * @return The single global MemberRegistry instance
     */
    public static MemberRegistry getInstance() {
        if (instance == null) {                    // 1st check — no lock, fast path
            synchronized (MemberRegistry.class) {  // acquire lock
                if (instance == null) {             // 2nd check — inside lock
                    instance = new MemberRegistry();
                }
            }
        }
        return instance;
    }

    // ── MEMBER MANAGEMENT ────────────────────────────────────────────

    /**
     * Register a new member when they connect.
     * If this is the FIRST member ever, they become coordinator automatically.
     *
     * @param member The newly connected Member
     * @return true if this member is the coordinator (i.e. they were first)
     */
    public boolean addMember(Member member) {
        boolean isFirstMember = members.isEmpty();
        if (isFirstMember) {
            member.setCoordinator(true);  // first to join = coordinator
        }
        members.add(member);
        return isFirstMember;
    }

    /**
     * Remove a member when they disconnect (gracefully or by crash).
     * After removal, if they were the coordinator, a new one must be elected.
     *
     * @param memberId The ID of the member to remove
     * @return The removed Member (or empty if not found)
     */
    public Optional<Member> removeMember(String memberId) {
        Optional<Member> found = findById(memberId);
        found.ifPresent(m -> {
            m.setCoordinator(false); // clear flag — member is no longer in the system
            members.remove(m);
        });
        return found;
    }

    /**
     * Find a member by their ID.
     * Returns Optional (not null) — forces callers to handle "not found" safely.
     * Optional is a Java best practice: never return null when a value might be absent.
     *
     * @param id The member's unique ID string
     * @return Optional containing the Member, or Optional.empty() if not found
     */
    public Optional<Member> findById(String id) {
        return members.stream()
                .filter(m -> m.getId().equals(id))
                .findFirst();
    }

    /**
     * Check if a given ID is already taken (IDs must be unique).
     * Called when a new client tries to join with a chosen ID.
     *
     * @param id The ID to check
     * @return true if this ID is already registered
     */
    public boolean isIdTaken(String id) {
        return findById(id).isPresent();
    }

    /**
     * Get the current coordinator.
     * Uses Optional to safely handle the case where there are no members.
     *
     * @return Optional containing the coordinator Member, or empty if no members
     */
    public Optional<Member> getCoordinator() {
        return members.stream()
                .filter(Member::isCoordinator)
                .findFirst();
    }

    /**
     * Elect a new coordinator — called when the current coordinator leaves.
     *
     * ALGORITHM: Simple "first remaining member" election.
     * The first member in our list (i.e. who joined earliest) becomes coordinator.
     * This is a deterministic, predictable rule — no ambiguity.
     *
     * In a real system you might use a more sophisticated algorithm (e.g. Bully),
     * but for this coursework this is correct and clearly justifiable.
     *
     * @return The newly elected coordinator, or empty if no members remain
     */
    public Optional<Member> electNewCoordinator() {
        // First, clear any stale coordinator flag (defensive programming)
        members.forEach(m -> m.setCoordinator(false));

        if (members.isEmpty()) return Optional.empty();

        Member newCoordinator = members.get(0);  // oldest remaining member
        newCoordinator.setCoordinator(true);
        return Optional.of(newCoordinator);
    }

    /**
     * Get a COPY of the member list.
     * We return a copy (not the live list) so callers can't accidentally
     * modify the registry's internal state. Defensive programming.
     *
     * @return A new ArrayList containing all current members
     */
    public List<Member> getAllMembers() {
        return new ArrayList<>(members);
    }

    /**
     * How many members are currently connected?
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Check if a member with this ID exists.
     */
    public boolean hasMember(String id) {
        return findById(id).isPresent();
    }

    /**
     * Reset the registry — used ONLY in unit tests to start fresh.
     * In production code, this is never called.
     */
    public void clear() {
        members.clear();
    }
}