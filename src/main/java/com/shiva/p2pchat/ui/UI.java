package com.shiva.p2pchat.ui;

public class UI {

    // Color Constants
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    // Prompts
    public static final String MAIN_PROMPT = BOLD + CYAN + "\n(online | requests | chat <user> <msg> | exit) > " + RESET;
    public static final String INBOX_PROMPT = BOLD + CYAN + "\n(accept <user> | read <user> | back) > " + RESET;
    public static final String CHAT_PROMPT = CYAN + "You: " + RESET;

    public static void printHeader(String title) {
        String line = "=================================================";
        System.out.println(BOLD + PURPLE + line + RESET);
        System.out.println(BOLD + PURPLE + "=== " + WHITE + title + PURPLE + " ===" + RESET);
        System.out.println(BOLD + PURPLE + line + RESET);
    }

    public static void printSystem(String message) {
        System.out.println(YELLOW + "[SYSTEM] " + message + RESET);
    }

    public static void printError(String message) {
        System.out.println(BOLD + RED + "[ERROR] " + message + RESET);
    }

    public static void printChat(String user, String message) {
        System.out.println(BOLD + GREEN + "\n[" + user + "]: " + RESET + WHITE + message + RESET);
    }
    
    public static void printNotification(String message) {
        // This combination clears the current line, prints the message, and re-draws the prompt
        System.out.print("\r" + YELLOW + "[!] " + message + RESET + "\n" + MAIN_PROMPT);
    }
}
