package com.networkgroupchat;

import com.networkgroupchat.client.ChatClient;
import com.networkgroupchat.server.ChatServer;

/**
 * ENTRY POINT — Main
 *
 * How to run:
 *
 *   Start the server:
 *     java Main server
 *     java Main server 5000        (custom port)
 *
 *   Start a client:
 *     java Main client Alice
 *     java Main client Bob localhost 5000   (custom host + port)
 *
 * The first client to join automatically becomes the coordinator.
 *
 * ── Demo Scenario (from the coursework spec) ────────────────────────────────
 * Terminal 1:  java Main server
 * Terminal 2:  java Main client Alice      ← becomes coordinator
 * Terminal 3:  java Main client Bob
 * Terminal 4:  java Main client Charlie
 *
 * Then in Alice's terminal:  Hello everyone!     ← broadcast
 * Then in Bob's terminal:    /msg Alice Hey!      ← private
 * Then in Bob's terminal:    /quit               ← Bob leaves
 * Then in Alice's terminal:  /quit               ← coordinator leaves → Charlie elected
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();

        switch (mode) {

            case "server":
                startServer(args);
                break;

            case "client":
                startClient(args);
                break;

            default:
                System.out.println("Unknown mode: '" + args[0] + "'");
                printUsage();
        }
    }

    // ── SERVER START ─────────────────────────────────────────────────

    private static void startServer(String[] args) {
        int port = ChatServer.DEFAULT_PORT;

        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port '" + args[1] + "' — using default " + port);
            }
        }

        ChatServer server = new ChatServer(port);
        // Run the server on a new thread so it doesn't block main()
        // (though in practice the main thread waits here until the JVM exits)
        Thread serverThread = new Thread(server, "ChatServer");
        serverThread.start();

        // Keep main thread alive so the program doesn't exit
        try {
            serverThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }

    // ── CLIENT START ─────────────────────────────────────────────────

    private static void startClient(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Main client <yourId> [host] [port]");
            return;
        }

        String memberId = args[1];
        String host = args.length >= 3 ? args[2] : ChatClient.DEFAULT_HOST;
        int port = ChatClient.DEFAULT_PORT;

        if (args.length >= 4) {
            try {
                port = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port '" + args[3] + "' — using default " + port);
            }
        }

        ChatClient client = new ChatClient(memberId, host, port);
        client.start(); // blocks until /quit
    }

    // ── DISPLAY ──────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println("==========================================");
        System.out.println("  NetworkGroupChat Application");
        System.out.println("  COMP1549 — Advanced Programming");
        System.out.println("  JDK Version: " + System.getProperty("java.version"));
        System.out.println("==========================================");
    }

    private static void printUsage() {
        System.out.println("\nUsage:");
        System.out.println("  java Main server [port]");
        System.out.println("  java Main client <yourId> [host] [port]");
        System.out.println("\nExamples:");
        System.out.println("  java Main server");
        System.out.println("  java Main server 5001");
        System.out.println("  java Main client Alice");
        System.out.println("  java Main client Bob localhost 5000");
        System.out.println();
    }
}