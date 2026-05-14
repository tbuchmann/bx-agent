package dev.bxagent;

import dev.bxagent.cli.ReplCommand;
import dev.bxagent.cli.TransformCommand;
import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            ReplCommand.run();
            System.exit(0);
        }
        int exitCode = new CommandLine(new TransformCommand()).execute(args);
        System.exit(exitCode);
    }
}
