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
        out.println("[bx-agent] ambigous backward mapping:");
        out.println("  " + unresolvedDescription);
        out.println();
        out.println("  Mapping: " + targetAttr + " → " + sourceAttr);
        out.println();
        out.println("  Options:");
        out.println("  [1] I specify a Java expression for the backward direction");
        out.println("  [2] Backward direction should return standard value (e.g. 0, \"\", null)");
        out.println("  [3] Backward direction should throw RuntimeException (lossy transformation)");
        out.println("  [4] Ommit this attribute in the backward direction");
        out.println();
        out.print("Your choice [1-4]: ");
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
        out.println("Enter Java expression (variable 'target' available):");
        out.println("Example: target.getName().contains(\" \") ? target.getName().substring(0, target.getName().indexOf(' ')) : target.getName()");
        out.print("> ");
        out.flush();

        String expression = scanner.nextLine().trim();

        if (expression.isEmpty()) {
            out.println("[!] Empty expression - using RuntimeException instead");
            return new BackwardMappingStrategy.ThrowException();
        }

        // Basic validation
        if (!expression.contains("target")) {
            out.println("[!] Warning: Expression does not contain 'target' - could be wrong");
        }

        return new BackwardMappingStrategy.CustomExpression(expression);
    }

    /**
     * Prompts user for a default value based on attribute type.
     */
    private BackwardMappingStrategy promptForDefaultValue(String sourceAttr) {
        out.println();
        out.println("Which standard value should be used?");
        out.println("  [1] null");
        out.println("  [2] \"\" (empty String)");
        out.println("  [3] 0 (Number)");
        out.println("  [4] false (Boolean)");
        out.print("Your choice [1-4]: ");
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
                out.println("[!] Please pick number between " + min + " and " + max);
                out.print("> ");
                out.flush();
            } catch (NumberFormatException e) {
                out.println("[!] Invalid input - please enter number");
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
