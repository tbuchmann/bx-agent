package dev.bxagent.cli;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.llm.*;
import dev.bxagent.mapping.BidirectionalityChecker;
import dev.bxagent.mapping.MappingModel;
import dev.bxagent.service.BXAgentService;
import dev.bxagent.validation.CompilationValidator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Main CLI command for EMT Agent.
 * Generates bidirectional EMF transformations from two .ecore metamodels.
 */
@Command(
    name = "bxagent",
    mixinStandardHelpOptions = true,
    version = "bxagent 1.0.0",
    description = "Generates bidirectional EMF transformations using LLMs",
    subcommands = {SyncCommand.class}
)
public class TransformCommand implements Callable<Integer> {

    @Option(
        names = {"-s", "--source"},
        required = true,
        description = "Path to source .ecore metamodel"
    )
    Path sourceEcore;

    @Option(
        names = {"-t", "--target"},
        required = true,
        description = "Path to target .ecore metamodel"
    )
    Path targetEcore;

    @Option(
        names = {"-o", "--output-dir"},
        description = "Output directory for generated code (default: ./generated)"
    )
    Path outputDir = Paths.get("./generated");

    @Option(
        names = {"-c", "--config"},
        description = "Path to agent.properties config file (default: config/agent.properties)"
    )
    Path configPath = Paths.get("config/agent.properties");

    @Option(
        names = {"-d", "--description"},
        description = "Natural language description of the transformation (optional)"
    )
    String description;

    @Option(
        names = {"--validate"},
        description = "Validate generated code by compiling (default: false). Requires EMF JARs on the classpath.",
        negatable = true,
        defaultValue = "false",
        fallbackValue = "true"
    )
    boolean validate;

    @Option(
        names = {"--interactive"},
        description = "Enable interactive prompts for backward mappings (default: true)",
        negatable = true,
        defaultValue = "true",
        fallbackValue = "true"
    )
    boolean interactive;

    @Option(
        names = {"-e", "--exclude-attr"},
        description = "Attribute name(s) to exclude from mapping and fingerprinting (e.g. incrementalID). Repeatable.",
        arity = "1..*"
    )
    java.util.List<String> excludedAttributes = new java.util.ArrayList<>();

    @Option(
        names = {"--from-json"},
        description = "Skip LLM call and regenerate code from a cached mapping-llm-response.json file."
    )
    java.nio.file.Path fromJson;

    @Option(
        names = {"--debug-log"},
        description = "Write LLM system prompt, user message and raw response to llm-debug.log (default: false)",
        negatable = true,
        defaultValue = "false",
        fallbackValue = "true"
    )
    boolean debugLog;

    @Override
    public Integer call() {
        try {
            TerminalHelper.printBanner();
            BXAgentService service = new BXAgentService();

            // Step 1: Load configuration
            TerminalHelper.step("[1/7] Loading configuration...");
            if (!Files.exists(configPath)) {
                TerminalHelper.error("Config file not found: " + configPath);
                TerminalHelper.error("Please create config/agent.properties (see config/agent.properties.example)");
                return 1;
            }
            LlmConfig config   = new LlmConfig(configPath.toString());
            LlmClient llmClient = createLlmClient(config);
            TerminalHelper.success("Provider: " + TerminalHelper.cyan(
                llmClient.getProviderName() + " / " + llmClient.getModelName()));
            System.out.println();

            // Steps 2–3: Parse both metamodels
            TerminalHelper.step("[2/7] Parsing source metamodel: "
                + TerminalHelper.cyan(sourceEcore.getFileName().toString()));
            if (!Files.exists(sourceEcore)) {
                TerminalHelper.error("Source .ecore file not found: " + sourceEcore); return 1; }
            TerminalHelper.step("[3/7] Parsing target metamodel: "
                + TerminalHelper.cyan(targetEcore.getFileName().toString()));
            if (!Files.exists(targetEcore)) {
                TerminalHelper.error("Target .ecore file not found: " + targetEcore); return 1; }

            BXAgentService.Session session = service.load(sourceEcore, targetEcore);
            TerminalHelper.success("Source — Package: "
                + TerminalHelper.cyan(session.leftSummary().packageName())
                + ", Classes: " + session.leftSummary().classes().size());
            TerminalHelper.success("Target — Package: "
                + TerminalHelper.cyan(session.rightSummary().packageName())
                + ", Classes: " + session.rightSummary().classes().size());
            System.out.println();

            // Step 4: Extract mapping (LLM or cached JSON)
            if (fromJson != null) {
                TerminalHelper.step("[4/7] Loading mappings from cached JSON: "
                    + TerminalHelper.cyan(fromJson.toString()));
                if (!Files.exists(fromJson)) {
                    TerminalHelper.error("JSON file not found: " + fromJson); return 1; }
            } else {
                TerminalHelper.step("[4/7] Extracting transformation mappings via LLM...");
            }
            final BXAgentService.Session s0 = session;
            session = TerminalHelper.spinner(
                fromJson != null ? "Loading from cache"
                    : "Querying " + TerminalHelper.cyan(
                        llmClient.getProviderName() + " / " + llmClient.getModelName()),
                () -> service.extractSpec(s0, llmClient, fromJson, description,
                    excludedAttributes, debugLog));

            TerminalHelper.success("Type mappings: "      + session.spec().typeMappings().size());
            TerminalHelper.success("Attribute mappings: " + session.spec().attributeMappings().size());
            TerminalHelper.success("Reference mappings: " + session.spec().referenceMappings().size());
            if (!excludedAttributes.isEmpty())
                TerminalHelper.success("Excluded attributes: " + excludedAttributes);

            // Save raw LLM response
            if (session.rawMappingJson() != null && fromJson == null) {
                Files.createDirectories(outputDir);
                Path debugPath = outputDir.resolve("mapping-llm-response.json");
                Files.writeString(debugPath, session.rawMappingJson());
                TerminalHelper.success("LLM response saved to: " + TerminalHelper.cyan(debugPath.toString()));
            } else if (fromJson != null) {
                TerminalHelper.success("Loaded from cache (no LLM call)");
            }
            System.out.println();

            // Step 5: Bidirectionality check (CLI keeps InteractivePrompter for rich terminal prompts)
            TerminalHelper.step("[5/7] Checking bidirectionality...");
            BidirectionalityChecker checker = interactive
                ? new BidirectionalityChecker(new InteractivePrompter())
                : new BidirectionalityChecker((InteractivePrompter) null);
            MappingModel.TransformationSpec enhanced = checker.resolveUnresolvedMappings(session.spec());
            session = session.withSpec(enhanced);
            long missingBackward = session.spec().attributeMappings().stream()
                .filter(m -> m.backwardExpression() == null).count();
            TerminalHelper.success("Backward mappings: "
                + (session.spec().attributeMappings().size() - missingBackward) + "/"
                + session.spec().attributeMappings().size());
            System.out.println();

            // Step 6: Generate code
            TerminalHelper.step("[6/7] Generating Java code...");
            final BXAgentService.Session s1 = session;
            session = service.generate(s1, outputDir);
            TerminalHelper.success(TerminalHelper.cyan(session.generatedTransformation().fileName()));
            TerminalHelper.success(TerminalHelper.cyan(session.generatedTest().fileName()));
            TerminalHelper.success("Written to: " + TerminalHelper.cyan(outputDir.toString()));
            System.out.println();

            // Step 7: Validate (optional)
            if (validate) {
                TerminalHelper.step("[7/7] Validating generated code...");
                final BXAgentService.Session s2 = session;
                CompilationValidator.ValidationResult result = TerminalHelper.spinner(
                    "Compiling " + TerminalHelper.cyan(session.generatedTransformation().fileName()),
                    () -> service.validate(s2, llmClient));

                if (result.success()) {
                    TerminalHelper.success("Compilation successful");
                    if (!result.finalCode().equals(session.generatedTransformation().content())) {
                        GeneratedFile fixed = new GeneratedFile(
                            session.generatedTransformation().fileName(), result.finalCode());
                        Files.writeString(outputDir.resolve(fixed.fileName()), fixed.content());
                        TerminalHelper.success("Code was automatically fixed by LLM");
                    }
                } else {
                    TerminalHelper.error("Compilation failed:");
                    System.err.println(result.getErrorSummary());
                    System.err.println();
                    TerminalHelper.warn("Generated code will still be written, but may not compile.");
                    TerminalHelper.warn("Please review and fix manually, or re-run with different inputs.");
                }
                System.out.println();
            } else {
                TerminalHelper.step("[7/7] Skipping validation (--no-validate)");
                System.out.println();
            }

            TerminalHelper.separator();
            TerminalHelper.footer("✓ Generation complete!");
            TerminalHelper.separator();

            return 0;

        } catch (IOException e) {
            System.err.println();
            TerminalHelper.error("IO error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        } catch (Exception e) {
            System.err.println();
            TerminalHelper.error("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Creates an LLM client based on configuration.
     */
    private LlmClient createLlmClient(LlmConfig config) {
        String provider = config.getProvider();

        return switch (provider.toLowerCase()) {
            case "ollama" -> new OllamaClient(config);
            case "anthropic" -> new AnthropicClient(config);
            case "openai" -> new OpenAiClient(config);
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        };
    }

    /**
     * Main entry point for testing the command directly.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new TransformCommand()).execute(args);
        System.exit(exitCode);
    }
}
