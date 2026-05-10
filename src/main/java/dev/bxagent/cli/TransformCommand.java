package dev.bxagent.cli;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.codegen.TransformationCodegen;
import dev.bxagent.llm.*;
import dev.bxagent.mapping.BidirectionalityChecker;
import dev.bxagent.mapping.MappingExtractor;
import dev.bxagent.mapping.MappingModel;
import dev.bxagent.metamodel.EcoreParser;
import dev.bxagent.metamodel.MetamodelSummary;
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

            // Step 1: Load configuration
            TerminalHelper.step("[1/7] Loading configuration...");
            if (!Files.exists(configPath)) {
                TerminalHelper.error("Config file not found: " + configPath);
                TerminalHelper.error("Please create config/agent.properties (see config/agent.properties.example)");
                return 1;
            }
            LlmConfig config = new LlmConfig(configPath.toString());
            LlmClient llmClient = createLlmClient(config);
            TerminalHelper.success("Provider: " + TerminalHelper.cyan(llmClient.getProviderName() + " / " + llmClient.getModelName()));
            System.out.println();

            // Step 2: Parse source metamodel
            TerminalHelper.step("[2/7] Parsing source metamodel: " + TerminalHelper.cyan(sourceEcore.getFileName().toString()));
            if (!Files.exists(sourceEcore)) {
                TerminalHelper.error("Source .ecore file not found: " + sourceEcore);
                return 1;
            }
            MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
            TerminalHelper.success("Package: " + TerminalHelper.cyan(sourceSummary.packageName()));
            TerminalHelper.success("Classes: " + sourceSummary.classes().size());
            System.out.println();

            // Step 3: Parse target metamodel
            TerminalHelper.step("[3/7] Parsing target metamodel: " + TerminalHelper.cyan(targetEcore.getFileName().toString()));
            if (!Files.exists(targetEcore)) {
                TerminalHelper.error("Target .ecore file not found: " + targetEcore);
                return 1;
            }
            MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);
            TerminalHelper.success("Package: " + TerminalHelper.cyan(targetSummary.packageName()));
            TerminalHelper.success("Classes: " + targetSummary.classes().size());
            System.out.println();

            // Step 4: Extract mapping (from LLM or cached JSON)
            MappingExtractor extractor = new MappingExtractor(llmClient);
            extractor.setDebugLog(debugLog);
            MappingModel.TransformationSpec spec;
            if (fromJson != null) {
                TerminalHelper.step("[4/7] Loading transformation mappings from cached JSON: " + TerminalHelper.cyan(fromJson.toString()));
                if (!Files.exists(fromJson)) {
                    TerminalHelper.error("JSON file not found: " + fromJson);
                    return 1;
                }
                spec = extractor.extractFromJson(Files.readString(fromJson));
                TerminalHelper.success("Loaded from cache (no LLM call)");
            } else {
                TerminalHelper.step("[4/7] Extracting transformation mappings via LLM...");
                final MetamodelSummary.Summary src = sourceSummary, tgt = targetSummary;
                spec = TerminalHelper.spinner(
                        "Querying " + TerminalHelper.cyan(llmClient.getProviderName() + " / " + llmClient.getModelName()),
                        () -> extractor.extract(src, tgt, description));
            }
            TerminalHelper.success("Type mappings: " + spec.typeMappings().size());
            TerminalHelper.success("Attribute mappings: " + spec.attributeMappings().size());
            TerminalHelper.success("Reference mappings: " + spec.referenceMappings().size());

            // Save raw LLM response for debugging (skip if loaded from cache)
            Files.createDirectories(outputDir);
            String rawResponse = extractor.getLastRawResponse();
            if (rawResponse != null && fromJson == null) {
                Path debugPath = outputDir.resolve("mapping-llm-response.json");
                Files.writeString(debugPath, rawResponse);
                TerminalHelper.success("LLM response saved to: " + TerminalHelper.cyan(debugPath.toString()));
            }
            System.out.println();

            // Inject excluded attributes from CLI into spec (overrides whatever the LLM returned)
            if (!excludedAttributes.isEmpty()) {
                spec = new MappingModel.TransformationSpec(
                    spec.sourcePackageName(), spec.targetPackageName(), spec.generatedClassName(),
                    spec.typeMappings(), spec.attributeMappings(), spec.referenceMappings(),
                    spec.unresolvedMappings(), spec.transformationOptions(),
                    spec.roleBasedTypeMappings(), spec.backwardConfigs(),
                    excludedAttributes, spec.edgeMaterializationMappings(),
                    spec.aggregationMappings(),
                    spec.structuralDeduplicationMappings(),
                    spec.conditionalTypeMappings(),
                    spec.syntheticObjectMappings(),
                    spec.annotationContainerRef(),
                    spec.annotationEClass(),
                    spec.annotationTextAttr(),
                    spec.targetLinkMappings(),
                    spec.sqlTypeMapping(),
                    spec.targetLinkMetamodel()
                );
                TerminalHelper.success("Excluded attributes: " + excludedAttributes);
            }

            // Step 5: Check bidirectionality
            TerminalHelper.step("[5/7] Checking bidirectionality...");
            BidirectionalityChecker checker = interactive
                ? new BidirectionalityChecker(new InteractivePrompter())
                : new BidirectionalityChecker(null);
            MappingModel.TransformationSpec enhancedSpec = checker.resolveUnresolvedMappings(spec);
            long missingBackward = enhancedSpec.attributeMappings().stream()
                .filter(m -> m.backwardExpression() == null)
                .count();
            TerminalHelper.success("Backward mappings: " +
                (enhancedSpec.attributeMappings().size() - missingBackward) + "/" +
                enhancedSpec.attributeMappings().size());
            System.out.println();

            // Step 6: Generate code
            TerminalHelper.step("[6/7] Generating Java code...");
            TransformationCodegen codegen = new TransformationCodegen();
            GeneratedFile transformationFile = codegen.generateTransformation(enhancedSpec);
            GeneratedFile testFile = codegen.generateTest(enhancedSpec);
            TerminalHelper.success(TerminalHelper.cyan(transformationFile.fileName()));
            TerminalHelper.success(TerminalHelper.cyan(testFile.fileName()));
            System.out.println();

            // Step 7: Validate (optional)
            if (validate) {
                TerminalHelper.step("[7/7] Validating generated code...");
                CompilationValidator validator = new CompilationValidator(llmClient);
                final GeneratedFile fileToValidate = transformationFile;
                CompilationValidator.ValidationResult result = TerminalHelper.spinner(
                        "Compiling " + TerminalHelper.cyan(fileToValidate.fileName()),
                        () -> validator.validate(fileToValidate));

                if (result.success()) {
                    TerminalHelper.success("Compilation successful");
                    if (!result.finalCode().equals(transformationFile.content())) {
                        transformationFile = new GeneratedFile(
                            transformationFile.fileName(),
                            result.finalCode()
                        );
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

            // Write files to disk
            TerminalHelper.step("Writing generated files to: " + TerminalHelper.cyan(outputDir.toString()));
            Files.createDirectories(outputDir);

            Path transformationPath = outputDir.resolve(transformationFile.fileName());
            Files.writeString(transformationPath, transformationFile.content());
            TerminalHelper.success(TerminalHelper.cyan(transformationPath.toString()));

            Path testPath = outputDir.resolve(testFile.fileName());
            Files.writeString(testPath, testFile.content());
            TerminalHelper.success(TerminalHelper.cyan(testPath.toString()));

            System.out.println();
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
