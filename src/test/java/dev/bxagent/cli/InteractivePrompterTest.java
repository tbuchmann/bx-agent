package dev.bxagent.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InteractivePrompter using mock streams.
 */
class InteractivePrompterTest {

    @Test
    void testPromptForCustomExpression() {
        // Simulate user choosing option 1 and entering a custom expression
        String input = "1\ntarget.getName().split(\" \")[0]\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));

        InteractivePrompter.BackwardMappingStrategy strategy = prompter.promptForBackwardMapping(
            "Name cannot be split into firstName/lastName",
            "firstName",
            "name"
        );

        assertInstanceOf(InteractivePrompter.BackwardMappingStrategy.CustomExpression.class, strategy);
        InteractivePrompter.BackwardMappingStrategy.CustomExpression customExpr =
            (InteractivePrompter.BackwardMappingStrategy.CustomExpression) strategy;
        assertEquals("target.getName().split(\" \")[0]", customExpr.expression());

        String output = out.toString();
        assertTrue(output.contains("Unklares Rückmapping"));
        assertTrue(output.contains("Ihre Wahl [1-4]:"));
    }

    @Test
    void testPromptForDefaultValue() {
        // Simulate user choosing option 2 (default value), then option 2 (empty string)
        String input = "2\n2\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));

        InteractivePrompter.BackwardMappingStrategy strategy = prompter.promptForBackwardMapping(
            "Cannot infer firstName from name",
            "firstName",
            "name"
        );

        assertInstanceOf(InteractivePrompter.BackwardMappingStrategy.DefaultValue.class, strategy);
        InteractivePrompter.BackwardMappingStrategy.DefaultValue defaultVal =
            (InteractivePrompter.BackwardMappingStrategy.DefaultValue) strategy;
        assertEquals("\"\"", defaultVal.value());
    }

    @Test
    void testPromptForThrowException() {
        // Simulate user choosing option 3 (throw exception)
        String input = "3\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));

        InteractivePrompter.BackwardMappingStrategy strategy = prompter.promptForBackwardMapping(
            "Lossy transformation",
            "age",
            "birthYear"
        );

        assertInstanceOf(InteractivePrompter.BackwardMappingStrategy.ThrowException.class, strategy);
    }

    @Test
    void testPromptForSkip() {
        // Simulate user choosing option 4 (skip)
        String input = "4\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));

        InteractivePrompter.BackwardMappingStrategy strategy = prompter.promptForBackwardMapping(
            "Skip this attribute",
            "computed",
            "value"
        );

        assertInstanceOf(InteractivePrompter.BackwardMappingStrategy.Skip.class, strategy);
    }

    @Test
    void testInvalidInputRetry() {
        // Simulate user entering invalid input first, then valid choice
        String input = "abc\n5\n2\n1\n";  // abc, 5 are invalid; 2 is valid main choice; 1 is valid sub-choice
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));

        InteractivePrompter.BackwardMappingStrategy strategy = prompter.promptForBackwardMapping(
            "Test",
            "attr",
            "attr"
        );

        assertInstanceOf(InteractivePrompter.BackwardMappingStrategy.DefaultValue.class, strategy);

        String output = out.toString();
        assertTrue(output.contains("Ungültige Eingabe"));  // Should show error for "abc"
        assertTrue(output.contains("zwischen 1 und 4"));   // Should show error for "5"
    }
}
