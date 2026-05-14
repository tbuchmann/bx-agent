package dev.bxagent.cli;

import dev.bxagent.llm.*;
import dev.bxagent.mapping.BidirectionalityChecker;
import dev.bxagent.mapping.MappingModel;
import dev.bxagent.metamodel.EcoreParser;
import dev.bxagent.service.BXAgentService;

import org.jline.reader.*;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * Interactive REPL for BXAgent.
 * Started automatically when no CLI arguments are provided.
 */
public final class ReplCommand {

    private ReplCommand() {}

    // ── Entry point ──────────────────────────────────────────────────────────

    private static final BXAgentService SERVICE = new BXAgentService();

    private static final List<String> ALL_COMMANDS = List.of(
        "/help", "/exit", "/quit", "/status", "/config",
        "/backend", "/model", "/source", "/target",
        "/description", "/desc", "/exclude", "/output",
        "/plan", "/build", "/show", "/integrate", "/test"
    );

    public static void run() {
        TerminalHelper.printBanner();
        System.out.println("  Type " + TerminalHelper.cyan("/help") + " for available commands. "
                + TerminalHelper.cyan("/exit") + " to quit.");
        System.out.println();

        SessionState state = new SessionState();

        // Build JLine3 terminal + reader with tab-completion and history
        Terminal terminal;
        LineReader lineReader;
        try {
            terminal = TerminalBuilder.builder().system(true).dumb(true).build();
            Path historyFile = Paths.get(System.getProperty("user.home"), ".bxagent_history");
            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(buildCompleter())
                    .variable(LineReader.HISTORY_FILE, historyFile)
                    .option(LineReader.Option.CASE_INSENSITIVE, true)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .build();
        } catch (Exception e) {
            // Non-interactive fallback (CI, piped input)
            runWithScanner(state);
            return;
        }

        String prompt = "\033[36mbxagent\033[0m> ";
        while (true) {
            String line;
            try {
                line = lineReader.readLine(prompt);
            } catch (UserInterruptException e) {
                // Ctrl+C — clear line, continue
                System.out.println();
                continue;
            } catch (EndOfFileException e) {
                // Ctrl+D — quit
                System.out.println("  Bye!");
                return;
            }

            if (line == null || line.trim().isEmpty()) continue;
            line = line.trim();

            // Split: first token = command, rest = argument string
            int sp = line.indexOf(' ');
            String cmd  = (sp < 0 ? line : line.substring(0, sp)).toLowerCase();
            String rest = sp < 0 ? "" : line.substring(sp + 1).trim();

            try {
                switch (cmd) {
                    case "/exit", "/quit" -> { System.out.println("  Bye!"); return; }
                    case "/help"          -> cmdHelp();
                    case "/status"        -> cmdStatus(state);
                    case "/config"        -> cmdConfig(state, rest);
                    case "/backend"       -> cmdBackend(state, rest);
                    case "/model"         -> cmdModel(state, rest);
                    case "/source"        -> cmdSource(state, rest);
                    case "/target"        -> cmdTarget(state, rest);
                    case "/description",
                         "/desc"          -> cmdDescription(state, rest);
                    case "/exclude"       -> cmdExclude(state, rest);
                    case "/output"        -> cmdOutput(state, rest);
                    case "/plan"          -> cmdPlan(state, rest);
                    case "/build"         -> cmdBuild(state);
                    case "/show"          -> cmdShow(state, rest);
                    case "/integrate"     -> cmdIntegrate(state, rest);
                    case "/test"          -> cmdTest(state, rest);
                    default               -> TerminalHelper.error(
                            "Unknown command: " + cmd + " — type /help for help");
                }
            } catch (Exception e) {
                TerminalHelper.error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            System.out.println();
        }
    }

    /** Scanner-based fallback for non-interactive / CI environments. */
    private static void runWithScanner(SessionState state) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("bxagent> ");
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            int sp = line.indexOf(' ');
            String cmd  = (sp < 0 ? line : line.substring(0, sp)).toLowerCase();
            String rest = sp < 0 ? "" : line.substring(sp + 1).trim();
            try {
                switch (cmd) {
                    case "/exit", "/quit" -> { System.out.println("  Bye!"); return; }
                    case "/help"          -> cmdHelp();
                    case "/status"        -> cmdStatus(state);
                    case "/config"        -> cmdConfig(state, rest);
                    case "/backend"       -> cmdBackend(state, rest);
                    case "/model"         -> cmdModel(state, rest);
                    case "/source"        -> cmdSource(state, rest);
                    case "/target"        -> cmdTarget(state, rest);
                    case "/description",
                         "/desc"          -> cmdDescription(state, rest);
                    case "/exclude"       -> cmdExclude(state, rest);
                    case "/output"        -> cmdOutput(state, rest);
                    case "/plan"          -> cmdPlan(state, rest);
                    case "/build"         -> cmdBuild(state);
                    case "/show"          -> cmdShow(state, rest);
                    case "/integrate"     -> cmdIntegrate(state, rest);
                    case "/test"          -> cmdTest(state, rest);
                    default               -> TerminalHelper.error(
                            "Unknown command: " + cmd + " — type /help for help");
                }
            } catch (Exception e) {
                TerminalHelper.error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            System.out.println();
        }
    }

    private static Completer buildCompleter() {
        FileNameCompleter fileCompleter = new FileNameCompleter();

        return (lineReader, parsedLine, candidates) -> {
            String line = parsedLine.line();
            String word = parsedLine.word();
            int wordIdx = parsedLine.wordIndex();

            // First word → complete command names
            if (wordIdx == 0) {
                for (String c : ALL_COMMANDS) {
                    if (c.startsWith(word.toLowerCase())) {
                        candidates.add(new Candidate(c));
                    }
                }
                return;
            }

            // Context-sensitive second-word completion
            String cmd = parsedLine.words().get(0).toLowerCase();
            switch (cmd) {
                case "/source", "/target", "/output", "/config", "/integrate" ->
                    fileCompleter.complete(lineReader, parsedLine, candidates);
                case "/plan" -> {
                    if (wordIdx == 1) {
                        candidates.add(new Candidate("--from"));
                    } else if (wordIdx == 2 && parsedLine.words().get(1).equals("--from")) {
                        fileCompleter.complete(lineReader, parsedLine, candidates);
                    }
                }
                case "/backend" -> {
                    for (String p : List.of("ollama", "anthropic", "openai")) {
                        if (p.startsWith(word)) candidates.add(new Candidate(p));
                    }
                }
                case "/show" -> {
                    for (String s : List.of("plan", "code", "test", "status")) {
                        if (s.startsWith(word)) candidates.add(new Candidate(s));
                    }
                }
                default -> {}
            }
        };
    }

    // ── /help ────────────────────────────────────────────────────────────────

    private static void cmdHelp() {
        System.out.println();
        System.out.println(TerminalHelper.cyan("  Session setup"));
        row("/config [path]",         "Show or set config file (default: config/agent.properties)");
        row("/backend <provider>",    "Set LLM provider: ollama | anthropic | openai");
        row("/model <model-id>",      "Set model ID (e.g. claude-sonnet-4-6, llama3)");
        row("/source <path>",         "Load source .ecore metamodel");
        row("/target <path>",         "Load target .ecore metamodel");
        row("/description <text>",    "Set natural-language description of the transformation");
        row("/exclude <attr>",        "Exclude an attribute from mapping and fingerprinting");
        row("/output [dir]",          "Show or set output directory (default: generated)");
        System.out.println();
        System.out.println(TerminalHelper.cyan("  Transformation"));
        row("/plan",                  "Query LLM to generate mapping plan");
        row("/plan --from <file>",    "Load mapping plan from cached JSON file");
        row("/build",                 "Generate Java transformation code from current plan");
        row("/integrate [dest]",      "Copy generated files to destination directory");
        row("/test [class[#method]]", "Run Maven tests (filter by class or method)");
        System.out.println();
        System.out.println(TerminalHelper.cyan("  Inspection"));
        row("/show plan",             "Print current mapping plan (JSON)");
        row("/show code",             "Print generated transformation code");
        row("/show test",             "Print generated test skeleton");
        row("/status",                "Show current session state");
        System.out.println();
        System.out.println(TerminalHelper.cyan("  General"));
        row("/help",                  "Show this help");
        row("/exit",                  "Quit BXAgent");
    }

    private static void row(String cmd, String desc) {
        System.out.printf("  %-30s %s%n", TerminalHelper.cyan(cmd), desc);
    }

    // ── /status ──────────────────────────────────────────────────────────────

    private static void cmdStatus(SessionState s) {
        System.out.println();
        System.out.println(TerminalHelper.cyan("  Current session state:"));
        statusLine("Config",      s.configPath.toString());
        statusLine("Backend",     s.backendOverride != null ? s.backendOverride : "(from config file)");
        statusLine("Model",       s.modelOverride   != null ? s.modelOverride   : "(from config file)");
        statusLine("Source",      s.sourceEcore != null ? s.sourceEcore.toString() : "—");
        statusLine("Target",      s.targetEcore != null ? s.targetEcore.toString() : "—");
        statusLine("Description", s.description != null ? s.description : "—");
        statusLine("Excludes",    s.excludedAttributes.isEmpty() ? "—" : String.join(", ", s.excludedAttributes));
        statusLine("Plan",        s.spec != null
                ? s.spec.typeMappings().size() + " type mappings, "
                        + s.spec.attributeMappings().size() + " attribute mappings"
                : "—");
        statusLine("Code",        s.generatedTransformation != null
                ? s.generatedTransformation.fileName() : "—");
        statusLine("Output dir",  s.outputDir.toString());
    }

    private static void statusLine(String label, String value) {
        System.out.printf("    %-14s %s%n", label + ":", value);
    }

    // ── /config ──────────────────────────────────────────────────────────────

    private static void cmdConfig(SessionState s, String rest) {
        if (rest.isEmpty()) {
            TerminalHelper.info("Config file: " + TerminalHelper.cyan(s.configPath.toString())
                    + (Files.exists(s.configPath) ? " ✓" : " (not found)"));
        } else {
            s.configPath = Paths.get(rest);
            TerminalHelper.success("Config path set to: " + TerminalHelper.cyan(rest));
        }
    }

    // ── /backend + /model ────────────────────────────────────────────────────

    private static void cmdBackend(SessionState s, String rest) {
        if (rest.isEmpty()) { TerminalHelper.error("Usage: /backend <ollama|anthropic|openai>"); return; }
        s.backendOverride = rest.trim();
        TerminalHelper.success("Backend set to: " + TerminalHelper.cyan(s.backendOverride));
    }

    private static void cmdModel(SessionState s, String rest) {
        if (rest.isEmpty()) { TerminalHelper.error("Usage: /model <model-id>"); return; }
        s.modelOverride = rest.trim();
        TerminalHelper.success("Model set to: " + TerminalHelper.cyan(s.modelOverride));
    }

    // ── /source + /target ────────────────────────────────────────────────────

    private static void cmdSource(SessionState s, String rest) throws IOException {
        if (rest.isEmpty()) { TerminalHelper.error("Usage: /source <path-to.ecore>"); return; }
        Path p = Paths.get(rest);
        if (!Files.exists(p)) { TerminalHelper.error("File not found: " + p); return; }
        s.sourceSummary = EcoreParser.parse(p);
        s.sourceEcore = p;
        TerminalHelper.success("Source loaded: " + TerminalHelper.cyan(s.sourceSummary.packageName())
                + " (" + s.sourceSummary.classes().size() + " classes)");
    }

    private static void cmdTarget(SessionState s, String rest) throws IOException {
        if (rest.isEmpty()) { TerminalHelper.error("Usage: /target <path-to.ecore>"); return; }
        Path p = Paths.get(rest);
        if (!Files.exists(p)) { TerminalHelper.error("File not found: " + p); return; }
        s.targetSummary = EcoreParser.parse(p);
        s.targetEcore = p;
        TerminalHelper.success("Target loaded: " + TerminalHelper.cyan(s.targetSummary.packageName())
                + " (" + s.targetSummary.classes().size() + " classes)");
    }

    // ── /description + /exclude + /output ────────────────────────────────────

    private static void cmdDescription(SessionState s, String rest) {
        if (rest.isEmpty()) {
            TerminalHelper.info("Description: " + (s.description != null ? s.description : "—"));
            return;
        }
        s.description = rest;
        TerminalHelper.success("Description set.");
    }

    private static void cmdExclude(SessionState s, String rest) {
        if (rest.isEmpty()) {
            TerminalHelper.info("Excluded attributes: "
                    + (s.excludedAttributes.isEmpty() ? "—" : String.join(", ", s.excludedAttributes)));
            return;
        }
        for (String attr : rest.split("\\s+")) {
            if (!s.excludedAttributes.contains(attr)) {
                s.excludedAttributes.add(attr);
                TerminalHelper.success("Excluded: " + TerminalHelper.cyan(attr));
            } else {
                TerminalHelper.info(attr + " is already excluded.");
            }
        }
    }

    private static void cmdOutput(SessionState s, String rest) {
        if (rest.isEmpty()) {
            TerminalHelper.info("Output dir: " + TerminalHelper.cyan(s.outputDir.toString()));
        } else {
            s.outputDir = Paths.get(rest);
            TerminalHelper.success("Output dir set to: " + TerminalHelper.cyan(rest));
        }
    }

    // ── /plan ────────────────────────────────────────────────────────────────

    private static void cmdPlan(SessionState s, String rest) throws Exception {
        Path fromJson = null;

        if (rest.startsWith("--from ")) {
            String filePath = rest.substring("--from ".length()).trim();
            fromJson = Paths.get(filePath);
            if (!Files.exists(fromJson)) { TerminalHelper.error("File not found: " + fromJson); return; }
        } else {
            if (s.sourceSummary == null) { TerminalHelper.error("Set source metamodel first: /source <path>"); return; }
            if (s.targetSummary == null) { TerminalHelper.error("Set target metamodel first: /target <path>"); return; }
        }

        LlmClient client = fromJson == null ? createClient(s) : null;
        if (fromJson == null && client == null) return;

        final BXAgentService.Session initSession = new BXAgentService.Session(
            s.sourceSummary, s.targetSummary, null, null, null, null);
        final Path finalFromJson = fromJson;
        final LlmClient finalClient = client;
        BXAgentService.Session tmp = TerminalHelper.spinner(
            fromJson != null ? "Loading from cache"
                : "Querying " + TerminalHelper.cyan(client.getProviderName() + " / " + client.getModelName()),
            () -> SERVICE.extractSpec(initSession, finalClient, finalFromJson, s.description,
                s.excludedAttributes, s.debugLog));

        s.spec = tmp.spec();
        s.rawMappingJson = tmp.rawMappingJson();

        // Save raw LLM response
        if (s.rawMappingJson != null && fromJson == null) {
            Files.createDirectories(s.outputDir);
            Path debugPath = s.outputDir.resolve("mapping-llm-response.json");
            Files.writeString(debugPath, s.rawMappingJson);
        }

        TerminalHelper.success("Type mappings: "      + s.spec.typeMappings().size());
        TerminalHelper.success("Attribute mappings: " + s.spec.attributeMappings().size());
        TerminalHelper.success("Reference mappings: " + s.spec.referenceMappings().size());
        TerminalHelper.info("Review plan with " + TerminalHelper.cyan("/show plan"));
    }

    // ── /build ───────────────────────────────────────────────────────────────

    private static void cmdBuild(SessionState s) throws Exception {
        if (s.spec == null) { TerminalHelper.error("No plan available. Run /plan first."); return; }

        // Bidirectionality check (CLI uses InteractivePrompter for rich terminal prompts)
        BidirectionalityChecker checker = s.interactive
                ? new BidirectionalityChecker(new InteractivePrompter())
                : new BidirectionalityChecker((InteractivePrompter) null);
        MappingModel.TransformationSpec enhanced = checker.resolveUnresolvedMappings(s.spec);

        BXAgentService.Session tmp = new BXAgentService.Session(
            null, null, enhanced, null, null, null);
        tmp = SERVICE.generate(tmp, s.outputDir);
        s.generatedTransformation = tmp.generatedTransformation();
        s.generatedTest           = tmp.generatedTest();

        TerminalHelper.success("Generated: " + TerminalHelper.cyan(s.generatedTransformation.fileName())
                + " (" + s.generatedTransformation.content().lines().count() + " lines)");
        TerminalHelper.success("Generated: " + TerminalHelper.cyan(s.generatedTest.fileName()));
        TerminalHelper.success("Written to: " + TerminalHelper.cyan(s.outputDir.toString()));
    }

    // ── /show ────────────────────────────────────────────────────────────────

    private static void cmdShow(SessionState s, String rest) {
        switch (rest.toLowerCase()) {
            case "plan" -> {
                if (s.rawMappingJson == null) { TerminalHelper.error("No plan. Run /plan first."); return; }
                System.out.println();
                System.out.println(s.rawMappingJson);
            }
            case "code" -> {
                if (s.generatedTransformation == null) { TerminalHelper.error("No code. Run /build first."); return; }
                System.out.println();
                System.out.println(s.generatedTransformation.content());
            }
            case "test" -> {
                if (s.generatedTest == null) { TerminalHelper.error("No test. Run /build first."); return; }
                System.out.println();
                System.out.println(s.generatedTest.content());
            }
            case "status", "" -> cmdStatus(s);
            default -> TerminalHelper.error("Usage: /show <plan|code|test|status>");
        }
    }

    // ── /integrate ───────────────────────────────────────────────────────────

    private static void cmdIntegrate(SessionState s, String rest) throws IOException {
        if (s.generatedTransformation == null) { TerminalHelper.error("No generated code. Run /build first."); return; }
        Path dest = rest.isEmpty() ? Paths.get("src/main/java/generated") : Paths.get(rest);
        Files.createDirectories(dest);
        Path tf = dest.resolve(s.generatedTransformation.fileName());
        Path tt = dest.resolve(s.generatedTest.fileName());
        Files.writeString(tf, s.generatedTransformation.content());
        Files.writeString(tt, s.generatedTest.content());
        TerminalHelper.success("Copied: " + TerminalHelper.cyan(tf.toString()));
        TerminalHelper.success("Copied: " + TerminalHelper.cyan(tt.toString()));
    }

    // ── /test ────────────────────────────────────────────────────────────────

    private static void cmdTest(SessionState s, String rest) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("mvn", "test", "--no-transfer-progress", "-pl", "bx-agent", "-am"));
        if (!rest.isEmpty()) cmd.add("-Dtest=" + rest);

        // Hint if generated test hasn't been integrated yet
        if (s.generatedTest != null) {
            Path integratedTest = Paths.get("bx-agent/src/test/java")
                    .resolve(s.generatedTest.fileName());
            if (!Files.exists(integratedTest)) {
                TerminalHelper.warn("Generated test not found in src/test/java — run /integrate first.");
            }
        }

        TerminalHelper.step("Running: " + TerminalHelper.cyan(String.join(" ", cmd)));
        System.out.println();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Paths.get("").toAbsolutePath().toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        int totalRun = 0, totalFail = 0, totalErr = 0, totalSkip = 0;
        boolean inFailureBlock = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = stripAnsi(line)
                        .replaceFirst("^\\[INFO\\]\\s*",  "")
                        .replaceFirst("^\\[ERROR\\]\\s*", "")
                        .replaceFirst("^\\[WARN\\]\\s*",  "")
                        .trim();

                if (clean.startsWith("Tests run:") && clean.contains("Failures:")) {
                    boolean perClass = clean.contains("-- in ");
                    int run   = extractInt(clean, "Tests run: ");
                    int fail  = extractInt(clean, "Failures: ");
                    int err   = extractInt(clean, "Errors: ");
                    int skip  = extractInt(clean, "Skipped: ");

                    if (perClass) {
                        String cls = clean.substring(clean.lastIndexOf("-- in ") + 6).trim();
                        if (fail == 0 && err == 0) {
                            TerminalHelper.success(cls + " (" + run + " tests)");
                        } else {
                            // red ✗ via error channel would go to stderr; use warn for inline
                            System.out.println("      \033[31m✗\033[0m " + cls
                                    + " — " + (fail + err) + " failed");
                        }
                    } else {
                        // aggregate summary line
                        totalRun  += run;  totalFail += fail;
                        totalErr  += err;  totalSkip += skip;
                    }
                    inFailureBlock = false;

                } else if (clean.startsWith("BUILD SUCCESS")) {
                    System.out.println();
                    TerminalHelper.separator();
                    TerminalHelper.footer("✓ " + totalRun + " tests — "
                            + (totalRun - totalFail - totalErr) + " passed, "
                            + (totalFail + totalErr) + " failed, "
                            + totalSkip + " skipped");
                    TerminalHelper.separator();
                    inFailureBlock = false;

                } else if (clean.startsWith("BUILD FAILURE")) {
                    System.out.println();
                    TerminalHelper.separator();
                    TerminalHelper.error("BUILD FAILURE — " + totalRun + " tests, "
                            + (totalFail + totalErr) + " failed");
                    TerminalHelper.separator();
                    inFailureBlock = false;

                } else if (clean.contains("FAILURE!") || clean.contains("ERROR!")) {
                    System.out.println("      " + clean);
                    inFailureBlock = true;

                } else if (inFailureBlock && !clean.isEmpty()) {
                    // failure detail lines (expected/actual, stack trace snippet)
                    if (clean.startsWith("at ") || clean.startsWith("Expected")
                            || clean.startsWith("Actual") || clean.startsWith("org.")
                            || clean.startsWith("dev.")) {
                        System.out.println("        " + clean);
                    } else {
                        inFailureBlock = false;
                    }
                }
            }
        }

        proc.waitFor();
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\033\\[[0-9;]*m", "");
    }

    private static int extractInt(String line, String label) {
        int idx = line.indexOf(label);
        if (idx < 0) return 0;
        int start = idx + label.length();
        int end = start;
        while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
        if (start == end) return 0;
        try { return Integer.parseInt(line.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static LlmClient createClient(SessionState s) {
        if (!Files.exists(s.configPath)) {
            TerminalHelper.error("Config file not found: " + s.configPath);
            TerminalHelper.info("Create config/agent.properties or use: /plan --from <cached-json>");
            return null;
        }
        LlmConfig base = new LlmConfig(s.configPath.toString());
        // Apply overrides via Properties
        if (s.backendOverride != null || s.modelOverride != null) {
            Properties props = new Properties();
            props.setProperty("provider", s.backendOverride != null ? s.backendOverride : base.getProvider());
            props.setProperty("model",    s.modelOverride   != null ? s.modelOverride   : base.getModel());
            String apiKey = base.getApiKey();
            if (apiKey != null) props.setProperty("api-key", apiKey);
            String baseUrl = base.getBaseUrl();
            if (baseUrl != null) props.setProperty("base-url", baseUrl);
            props.setProperty("temperature", String.valueOf(base.getTemperature()));
            props.setProperty("max-tokens",  String.valueOf(base.getMaxTokens()));
            props.setProperty("timeout",     String.valueOf(base.getTimeout()));
            base = LlmConfig.fromProperties(props);
        }
        String provider = base.getProvider();
        return switch (provider.toLowerCase()) {
            case "ollama"    -> new OllamaClient(base);
            case "anthropic" -> new AnthropicClient(base);
            case "openai"    -> new OpenAiClient(base);
            default -> { TerminalHelper.error("Unknown provider: " + provider); yield null; }
        };
    }

}
