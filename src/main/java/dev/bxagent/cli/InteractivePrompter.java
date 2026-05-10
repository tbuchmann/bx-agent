package dev.bxagent.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

/**
 * Handles interactive user prompts for resolving ambiguous mappings.
 * Testable via constructor injection of streams.
 */
public class InteractivePrompter {

    private final Scanner scanner;
    private final PrintStream out;

    /**
     * Constructor for dependency injection (testability).
     *
     * @param input  Input stream for reading user responses
     * @param output Output stream for displaying prompts
     */
    public InteractivePrompter(InputStream input, PrintStream output) {
        this.scanner = new Scanner(input);
        this.out = output;
    }

    /**
     * Default constructor using System.in and System.out.
     */
    public InteractivePrompter() {
        this(System.in, System.out);
    }

    /**
     * Prompts the user for how to handle an unresolved backward mapping.
     *
     * @param unresolvedDescription Description of the unresolved mapping
     * @param sourceAttr           Source attribute name
     * @param targetAttr           Target attribute name
     * @return BackwardMappingStrategy
     */
    public BackwardMappingStrategy promptForBackwardMapping(
        String unresolvedDescription,
        String sourceAttr,
        String targetAttr
    ) {
        out.println();
        out.println("═══════════════════════════════════════════════════════════════");
        out.println("[bxagent] Unklares Rückmapping:");
        out.println("  " + unresolvedDescription);
        out.println();
        out.println("  Mapping: " + targetAttr + " → " + sourceAttr);
        out.println();
        out.println("  Optionen:");
        out.println("  [1] Ich gebe einen Java-Ausdruck für die Rückrichtung an");
        out.println("  [2] Rückrichtung soll einen Standardwert zurückgeben (z.B. 0, \"\", null)");
        out.println("  [3] Rückrichtung soll eine RuntimeException werfen (verlustbehaftete Transformation)");
        out.println("  [4] Dieses Attribut in der Rückrichtung weglassen");
        out.println();
        out.print("Ihre Wahl [1-4]: ");
        out.flush();

        int choice = readIntInRange(1, 4);

        return switch (choice) {
            case 1 -> promptForCustomExpression(targetAttr, sourceAttr);
            case 2 -> promptForDefaultValue(sourceAttr);
            case 3 -> new BackwardMappingStrategy.ThrowException();
            case 4 -> new BackwardMappingStrategy.Skip();
            default -> throw new IllegalStateException("Unexpected choice: " + choice);
        };
    }

    /**
     * Prompts user for a custom Java expression.
     */
    private BackwardMappingStrategy promptForCustomExpression(String targetAttr, String sourceAttr) {
        out.println();
        out.println("Geben Sie einen Java-Ausdruck ein (Variable 'target' verfügbar):");
        out.println("Beispiel: target.getName().contains(\" \") ? target.getName().substring(0, target.getName().indexOf(' ')) : target.getName()");
        out.print("> ");
        out.flush();

        String expression = scanner.nextLine().trim();

        if (expression.isEmpty()) {
            out.println("[!] Leerer Ausdruck - verwende RuntimeException stattdessen");
            return new BackwardMappingStrategy.ThrowException();
        }

        // Basic validation
        if (!expression.contains("target")) {
            out.println("[!] Warnung: Ausdruck enthält nicht 'target' - könnte fehlerhaft sein");
        }

        return new BackwardMappingStrategy.CustomExpression(expression);
    }

    /**
     * Prompts user for a default value based on attribute type.
     */
    private BackwardMappingStrategy promptForDefaultValue(String sourceAttr) {
        out.println();
        out.println("Welcher Standardwert soll verwendet werden?");
        out.println("  [1] null");
        out.println("  [2] \"\" (leerer String)");
        out.println("  [3] 0 (Zahl)");
        out.println("  [4] false (Boolean)");
        out.print("Ihre Wahl [1-4]: ");
        out.flush();

        int choice = readIntInRange(1, 4);

        String defaultValue = switch (choice) {
            case 1 -> "null";
            case 2 -> "\"\"";
            case 3 -> "0";
            case 4 -> "false";
            default -> "null";
        };

        return new BackwardMappingStrategy.DefaultValue(defaultValue);
    }

    /**
     * Reads an integer within a specified range, with retry on invalid input.
     */
    private int readIntInRange(int min, int max) {
        while (true) {
            try {
                String line = scanner.nextLine().trim();
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) {
                    return value;
                }
                out.println("[!] Bitte wählen Sie eine Zahl zwischen " + min + " und " + max);
                out.print("> ");
                out.flush();
            } catch (NumberFormatException e) {
                out.println("[!] Ungültige Eingabe - bitte eine Zahl eingeben");
                out.print("> ");
                out.flush();
            }
        }
    }

    /**
     * Sealed interface for backward mapping strategies.
     */
    public sealed interface BackwardMappingStrategy
        permits BackwardMappingStrategy.CustomExpression,
                BackwardMappingStrategy.DefaultValue,
                BackwardMappingStrategy.ThrowException,
                BackwardMappingStrategy.Skip {

        record CustomExpression(String expression) implements BackwardMappingStrategy {}
        record DefaultValue(String value) implements BackwardMappingStrategy {}
        record ThrowException() implements BackwardMappingStrategy {}
        record Skip() implements BackwardMappingStrategy {}
    }
}
