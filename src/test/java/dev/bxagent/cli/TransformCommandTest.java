package dev.bxagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransformCommand CLI.
 */
class TransformCommandTest {

    @Test
    void testHelpOption() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute("--help");

        assertEquals(0, exitCode);
        String output = sw.toString();
        assertTrue(output.contains("bx-agent"));
        assertTrue(output.contains("--source"));
        assertTrue(output.contains("--target"));
        assertTrue(output.contains("--output-dir"));
    }

    @Test
    void testVersionOption() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute("--version");

        assertEquals(0, exitCode);
        String output = sw.toString();
        assertTrue(output.contains("bx-agent"));
        assertTrue(output.contains("1.0.0"));
    }

    @Test
    void testMissingRequiredArguments() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        StringWriter sw = new StringWriter();
        cli.setErr(new PrintWriter(sw));

        // Missing --source and --target
        int exitCode = cli.execute();

        assertEquals(2, exitCode); // Picocli returns 2 for usage errors
    }

    @Test
    void testMissingSourceArgument() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        StringWriter sw = new StringWriter();
        cli.setErr(new PrintWriter(sw));

        // Only --target provided
        int exitCode = cli.execute("--target", "test.ecore");

        assertEquals(2, exitCode);
    }

    @Test
    void testNonExistentSourceFile(@TempDir Path tempDir) throws IOException {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        // Create a valid config file
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("agent.properties");
        Files.writeString(configFile, """
            llm.provider=ollama
            llm.ollama.baseUrl=http://localhost:11434
            llm.ollama.model=llama2
            """);

        Path nonExistent = tempDir.resolve("nonexistent.ecore");
        Path target = tempDir.resolve("target.ecore");
        Files.writeString(target, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ecore:EPackage/>");

        int exitCode = cli.execute(
            "--source", nonExistent.toString(),
            "--target", target.toString(),
            "--config", configFile.toString(),
            "--output-dir", tempDir.toString()
        );

        assertEquals(1, exitCode);
    }

    @Test
    void testNonExistentConfigFile(@TempDir Path tempDir) throws IOException {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        Path source = tempDir.resolve("source.ecore");
        Path target = tempDir.resolve("target.ecore");
        Files.writeString(source, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ecore:EPackage/>");
        Files.writeString(target, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ecore:EPackage/>");

        Path nonExistentConfig = tempDir.resolve("nonexistent.properties");

        int exitCode = cli.execute(
            "--source", source.toString(),
            "--target", target.toString(),
            "--config", nonExistentConfig.toString(),
            "--output-dir", tempDir.toString()
        );

        assertEquals(1, exitCode);
    }

    @Test
    void testValidateFlag() {
        // Test default value (after parsing without the flag)
        String[] args = {"-s", "source.ecore", "-t", "target.ecore"};
        TransformCommand cmd = CommandLine.populateCommand(new TransformCommand(), args);
        assertFalse(cmd.validate, "Validation should be disabled by default");

        // Test --no-validate
        args = new String[]{"--no-validate", "-s", "source.ecore", "-t", "target.ecore"};
        cmd = CommandLine.populateCommand(new TransformCommand(), args);
        assertFalse(cmd.validate, "--no-validate should disable validation");

        // Test explicit --validate
        args = new String[]{"--validate", "-s", "source.ecore", "-t", "target.ecore"};
        cmd = CommandLine.populateCommand(new TransformCommand(), args);
        assertTrue(cmd.validate, "--validate should enable validation");
    }

    @Test
    void testInteractiveFlag() {
        // Test default value (after parsing without the flag)
        String[] args = {"-s", "source.ecore", "-t", "target.ecore"};
        TransformCommand cmd = CommandLine.populateCommand(new TransformCommand(), args);
        assertTrue(cmd.interactive, "Interactive mode should be enabled by default");

        // Test --no-interactive
        args = new String[]{"--no-interactive", "-s", "source.ecore", "-t", "target.ecore"};
        cmd = CommandLine.populateCommand(new TransformCommand(), args);
        assertFalse(cmd.interactive, "--no-interactive should disable interactive mode");

        // Test explicit --interactive
        args = new String[]{"--interactive", "-s", "source.ecore", "-t", "target.ecore"};
        cmd = CommandLine.populateCommand(new TransformCommand(), args);
        assertTrue(cmd.interactive, "--interactive should enable interactive mode");
    }

    @Test
    void testDescriptionOption() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        String description = "This is a test transformation";
        cli.execute("--description", description, "--help");

        assertEquals(description, cmd.description);
    }

    @Test
    void testShortOptions() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        // Test short options
        cli.execute(
            "-s", "source.ecore",
            "-t", "target.ecore",
            "-o", "output",
            "-c", "config.properties",
            "-d", "test description",
            "--help"
        );

        assertEquals(Path.of("source.ecore"), cmd.sourceEcore);
        assertEquals(Path.of("target.ecore"), cmd.targetEcore);
        assertEquals(Path.of("output"), cmd.outputDir);
        assertEquals(Path.of("config.properties"), cmd.configPath);
        assertEquals("test description", cmd.description);
    }
}
