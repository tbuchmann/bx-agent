package dev.bxagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the CLI command with real .ecore files.
 * These tests verify that the command can be invoked end-to-end,
 * but use non-existent config to avoid requiring actual LLM access.
 */
class CLIIntegrationTest {

    @Test
    void testCLIWithValidEcoreFilesButMissingConfig(@TempDir Path tempDir) throws IOException {
        // This test verifies that the CLI properly validates files exist
        // and properly reports missing config

        Path source = Paths.get("examples/pdb/PersonsDB1.ecore");
        Path target = Paths.get("examples/pdb/PersonsDB2.ecore");
        Path nonExistentConfig = tempDir.resolve("nonexistent.properties");
        Path outputDir = tempDir.resolve("output");

        assertTrue(Files.exists(source), "Source example should exist");
        assertTrue(Files.exists(target), "Target example should exist");

        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        int exitCode = cli.execute(
            "--source", source.toString(),
            "--target", target.toString(),
            "--config", nonExistentConfig.toString(),
            "--output-dir", outputDir.toString()
        );

        // Should fail with exit code 1 due to missing config
        assertEquals(1, exitCode);
    }

    @Test
    void testCLIWithNonExistentEcoreFiles(@TempDir Path tempDir) throws IOException {
        Path nonExistentSource = tempDir.resolve("nonexistent-source.ecore");
        Path nonExistentTarget = tempDir.resolve("nonexistent-target.ecore");
        Path config = tempDir.resolve("config.properties");
        Path outputDir = tempDir.resolve("output");

        // Create a dummy config
        Files.writeString(config, """
            llm.provider=ollama
            llm.base_url=http://localhost:11434
            llm.model=test
            """);

        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        int exitCode = cli.execute(
            "--source", nonExistentSource.toString(),
            "--target", nonExistentTarget.toString(),
            "--config", config.toString(),
            "--output-dir", outputDir.toString()
        );

        // Should fail with exit code 1 due to missing source file
        assertEquals(1, exitCode);
    }

    @Test
    void testCLIHelpOption() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        int exitCode = cli.execute("--help");

        assertEquals(0, exitCode);
    }

    @Test
    void testCLIVersionOption() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        int exitCode = cli.execute("--version");

        assertEquals(0, exitCode);
    }

    @Test
    void testCLIRequiredArgumentsMissing() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        // Missing both required arguments
        int exitCode = cli.execute();

        // Picocli returns 2 for usage errors
        assertEquals(2, exitCode);
    }

    @Test
    void testCLIFlagsAreParsedCorrectly() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        // Parse without executing (to avoid file system checks)
        String[] args = {
            "--source", "source.ecore",
            "--target", "target.ecore",
            "--no-validate",
            "--no-interactive",
            "--description", "Test transformation"
        };

        cmd = CommandLine.populateCommand(new TransformCommand(), args);

        assertEquals(Paths.get("source.ecore"), cmd.sourceEcore);
        assertEquals(Paths.get("target.ecore"), cmd.targetEcore);
        assertFalse(cmd.validate);
        assertFalse(cmd.interactive);
        assertEquals("Test transformation", cmd.description);
    }

    @Test
    void testCLIDefaultValues() {
        TransformCommand cmd = new TransformCommand();
        CommandLine cli = new CommandLine(cmd);

        String[] args = {
            "--source", "source.ecore",
            "--target", "target.ecore"
        };

        cmd = CommandLine.populateCommand(new TransformCommand(), args);

        // Check defaults
        assertEquals(Paths.get("./generated"), cmd.outputDir);
        assertEquals(Paths.get("config/agent.properties"), cmd.configPath);
        assertFalse(cmd.validate);
        assertTrue(cmd.interactive);
        assertNull(cmd.description);
    }

    @Test
    void testCLIShortOptions() {
        TransformCommand cmd = new TransformCommand();

        String[] args = {
            "-s", "src.ecore",
            "-t", "tgt.ecore",
            "-o", "out",
            "-c", "cfg.properties",
            "-d", "test description"
        };

        cmd = CommandLine.populateCommand(new TransformCommand(), args);

        assertEquals(Paths.get("src.ecore"), cmd.sourceEcore);
        assertEquals(Paths.get("tgt.ecore"), cmd.targetEcore);
        assertEquals(Paths.get("out"), cmd.outputDir);
        assertEquals(Paths.get("cfg.properties"), cmd.configPath);
        assertEquals("test description", cmd.description);
    }

    @Test
    void testCLIOutputDirectoryCreation(@TempDir Path tempDir) throws IOException {
        // This test verifies that output directory would be created
        // We can't fully test this without a valid config and LLM,
        // but we can verify the path is set correctly

        Path outputDir = tempDir.resolve("custom-output");
        assertFalse(Files.exists(outputDir), "Output dir should not exist yet");

        TransformCommand cmd = new TransformCommand();

        String[] args = {
            "-s", "source.ecore",
            "-t", "target.ecore",
            "-o", outputDir.toString()
        };

        cmd = CommandLine.populateCommand(new TransformCommand(), args);

        assertEquals(outputDir, cmd.outputDir);
    }
}
