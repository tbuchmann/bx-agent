package dev.bxagent.cli;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ANSI-colored terminal output helpers for BXAgent CLI.
 * Falls back to plain text when running in CI or non-interactive terminals.
 */
public final class TerminalHelper {

    private static final boolean ANSI = detectAnsi();

    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String BLUE   = "\033[34m";
    private static final String GREEN  = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED    = "\033[31m";
    private static final String CYAN   = "\033[36m";

    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private TerminalHelper() {}

    private static boolean detectAnsi() {
        return System.console() != null
                && System.getenv("NO_COLOR") == null
                && System.getenv("CI") == null;
    }

    // ── Banner ───────────────────────────────────────────────────────────────

    public static void printBanner() {
        if (!ANSI) {
            System.out.println("[BXAgent] Bidirectional Model Transformation Agent v1.0.0");
            System.out.println();
            return;
        }
        String b = BLUE + BOLD;
        System.out.println();
        System.out.println(b + "  ██████╗ ██╗  ██╗ █████╗  ██████╗ ███████╗███╗   ██╗████████╗" + RESET);
        System.out.println(b + "  ██╔══██╗╚██╗██╔╝██╔══██╗██╔════╝ ██╔════╝████╗  ██║╚══██╔══╝" + RESET);
        System.out.println(b + "  ██████╔╝ ╚███╔╝ ███████║██║  ███╗█████╗  ██╔██╗ ██║   ██║   " + RESET);
        System.out.println(b + "  ██╔══██╗ ██╔██╗ ██╔══██║██║   ██║██╔══╝  ██║╚██╗██║   ██║   " + RESET);
        System.out.println(b + "  ██████╔╝██╔╝ ██╗██║  ██║╚██████╔╝███████╗██║ ╚████║   ██║   " + RESET);
        System.out.println(b + "  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═══╝   ╚═╝  " + RESET);
        System.out.println(BLUE + "  Bidirectional Model Transformation Agent  " + BOLD + "v1.0.0" + RESET);
        System.out.println(BLUE + "  " + "─".repeat(61) + RESET);
        System.out.println();
    }

    // ── Step headers ─────────────────────────────────────────────────────────

    /** Bold step line, e.g. "[1/7] Loading configuration..." */
    public static void step(String msg) {
        System.out.println(ANSI ? BOLD + msg + RESET : msg);
    }

    /** Completion footer line. */
    public static void footer(String msg) {
        if (ANSI) {
            System.out.println(GREEN + BOLD + "  " + msg + RESET);
        } else {
            System.out.println(msg);
        }
    }

    // ── Status lines ─────────────────────────────────────────────────────────

    /** Green ✓ success line. */
    public static void success(String msg) {
        System.out.println(ANSI ? "      " + GREEN + "✓ " + RESET + msg : "      ✓ " + msg);
    }

    /** Red ✗ error line (to stderr). */
    public static void error(String msg) {
        System.err.println(ANSI ? RED + "ERROR: " + RESET + msg : "ERROR: " + msg);
    }

    /** Yellow ⚠ warning line. */
    public static void warn(String msg) {
        System.out.println(ANSI ? "      " + YELLOW + "⚠ " + RESET + msg : "      ⚠ " + msg);
    }

    /** Indented info line (no icon). */
    public static void info(String msg) {
        System.out.println("      " + msg);
    }

    // ── Inline formatters ─────────────────────────────────────────────────────

    /** Returns msg wrapped in cyan if ANSI supported, otherwise plain. */
    public static String cyan(String msg) {
        return ANSI ? CYAN + msg + RESET : msg;
    }

    /** Separator line. */
    public static void separator() {
        System.out.println(ANSI ? BLUE + "  " + "═".repeat(62) + RESET : "═".repeat(64));
    }

    // ── Spinner ───────────────────────────────────────────────────────────────

    /**
     * Runs {@code task} while showing an animated spinner labeled {@code msg}.
     * In non-ANSI mode, prints "{msg}..." then "done." when finished.
     */
    public static <T> T spinner(String msg, Callable<T> task) throws Exception {
        if (!ANSI) {
            System.out.print("      " + msg + "... ");
            System.out.flush();
            T result = task.call();
            System.out.println("done.");
            return result;
        }

        AtomicBoolean done = new AtomicBoolean(false);
        Thread spinner = new Thread(() -> {
            int i = 0;
            while (!done.get()) {
                System.out.print("\r      " + YELLOW + SPINNER[i % SPINNER.length] + " " + RESET + msg);
                System.out.flush();
                i++;
                try { Thread.sleep(80); } catch (InterruptedException e) { break; }
            }
            System.out.print("\r\033[K"); // clear the spinner line
            System.out.flush();
        });
        spinner.setDaemon(true);
        spinner.start();

        try {
            return task.call();
        } finally {
            done.set(true);
            spinner.interrupt();
            spinner.join(500);
        }
    }
}
