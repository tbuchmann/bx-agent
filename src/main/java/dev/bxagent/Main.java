package dev.bxagent;

import dev.bxagent.cli.TransformCommand;
import picocli.CommandLine;

/**
 * Main entry point for EMT Agent CLI application.
 */
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TransformCommand()).execute(args);
        System.exit(exitCode);
    }
}
