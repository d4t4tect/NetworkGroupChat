package com.networkgroupchat.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UTILITY CLASS — Logger

 * Centralises ALL console output in one place.
 * Every print goes through here, meaning:
 *   1. All output has a consistent timestamp format
 *   2. You can add log levels (INFO/WARN/ERROR) in one place
 *   3. You could swap to a file logger later without changing any other class

 * This is an example of the DRY principle — Don't Repeat Yourself.
 * Without this, every class would have its own System.out.println() calls
 * with inconsistent formatting.

 * All methods are static — this is a pure utility class, never instantiated.
 */
public class ChatLogger {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // Prevent instantiation
    private ChatLogger() {}

    private static String now() {
        return LocalDateTime.now().format(FMT);
    }

    /** Standard informational log */
    public static void info(String source, String message) {
        System.out.println("[" + now() + "] [INFO] [" + source + "] " + message);
    }

    /** Warning — something unexpected but recoverable */
    public static void warn(String source, String message) {
        System.out.println("[" + now() + "] [WARN] [" + source + "] " + message);
    }

    /** Error — something went wrong */
    public static void error(String source, String message) {
        System.err.println("[" + now() + "] [ERROR] [" + source + "] " + message);
    }

    /** Chat message display — used by both server and client sides */
    public static void chat(String message) {
        System.out.println("[" + now() + "] " + message);
    }

    /** System event display (join, leave, coordinator change) */
    public static void system(String message) {
        System.out.println("[" + now() + "] *** " + message + " ***");
    }
}