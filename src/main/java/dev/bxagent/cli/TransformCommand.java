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
    name = "bx-agent",
    mixinStandardHelpOptions = true,
    version = "bx-agent 1.0.0",
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
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║              BX Agent - Transformation Generator               ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Step 1: Load configuration
            System.out.println("[1/7] Loading configuration...");
            if (!Files.exists(configPath)) {
                System.err.println("ERROR: Config file not found: " + configPath);
                System.err.println("Please create config/agent.properties (see config/agent.properties.example)");
                return 1;
            }
            LlmConfig config = new LlmConfig(configPath.toString());
            LlmClient llmClient = createLlmClient(config);
            System.out.println("      ✓ Using provider: " + llmClient.getProviderName() + " / " + llmClient.getModelName());
            System.out.println();

            // Step 2: Parse source metamodel
            System.out.println("[2/7] Parsing source metamodel: " + sourceEcore.getFileName());
            if (!Files.exists(sourceEcore)) {
                System.err.println("ERROR: Source .ecore file not found: " + sourceEcore);
                return 1;
            }
            MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
            System.out.println("      ✓ Package: " + sourceSummary.packageName());
            System.out.println("      ✓ Classes: " + sourceSummary.classes().size());
            System.out.println();

            // Step 3: Parse target metamodel
            System.out.println("[3/7] Parsing target metamodel: " + targetEcore.getFileName());
            if (!Files.exists(targetEcore)) {
                System.err.println("ERROR: Target .ecore file not found: " + targetEcore);
                return 1;
            }
            MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);
            System.out.println("      ✓ Package: " + targetSummary.packageName());
            System.out.println("      ✓ Classes: " + targetSummary.classes().size());
            System.out.println();

            // Step 4: Extract mapping (from LLM or cached JSON)
            MappingExtractor extractor = new MappingExtractor(llmClient);
            extractor.setDebugLog(debugLog);
            MappingModel.TransformationSpec spec;
            if (fromJson != null) {
                System.out.println("[4/7] Loading transformation mappings from cached JSON: " + fromJson);
                if (!Files.exists(fromJson)) {
                    System.err.println("ERROR: JSON file not found: " + fromJson);
                    return 1;
                }
                spec = extractor.extractFromJson(Files.readString(fromJson));
                System.out.println("      ✓ Loaded from cache (no LLM call)");
            } else {
                System.out.println("[4/7] Extracting transformation mappings via LLM...");
                spec = extractor.extract(sourceSummary, targetSummary, description);
            }
            System.out.println("      ✓ Type mappings: " + spec.typeMappings().size());
            System.out.println("      ✓ Attribute mappings: " + spec.attributeMappings().size());
            System.out.println("      ✓ Reference mappings: " + spec.referenceMappings().size());

            // Save raw LLM response for debugging (skip if loaded from cache)
            Files.createDirectories(outputDir);
            String rawResponse = extractor.getLastRawResponse();
            if (rawResponse != null && fromJson == null) {
                Path debugPath = outputDir.resolve("mapping-llm-response.json");
                Files.writeString(debugPath, rawResponse);
                System.out.println("      ✓ LLM response saved to: " + debugPath);
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
                System.out.println("      ✓ Excluded attributes: " + excludedAttributes);
            }

            // Step 5: Check bidirectionality
            System.out.println("[5/7] Checking bidirectionality...");
            BidirectionalityChecker checker = interactive
                ? new BidirectionalityChecker(new InteractivePrompter())
                : new BidirectionalityChecker(null);
            MappingModel.TransformationSpec enhancedSpec = checker.resolveUnresolvedMappings(spec);
            long missingBackward = enhancedSpec.attributeMappings().stream()
                .filter(m -> m.backwardExpression() == null)
                .count();
            System.out.println("      ✓ Backward mappings: " +
                (enhancedSpec.attributeMappings().size() - missingBackward) + "/" +
                enhancedSpec.attributeMappings().size());
            System.out.println();

            // Step 6: Generate code
            System.out.println("[6/7] Generating Java code...");
            TransformationCodegen codegen = new TransformationCodegen();
            GeneratedFile transformationFile = codegen.generateTransformation(enhancedSpec);
            GeneratedFile testFile = codegen.generateTest(enhancedSpec);
            System.out.println("      ✓ " + transformationFile.fileName());
            System.out.println("      ✓ " + testFile.fileName());
            System.out.println();

            // Step 7: Validate (optional)
            if (validate) {
                System.out.println("[7/7] Validating generated code...");
                CompilationValidator validator = new CompilationValidator(llmClient);
                CompilationValidator.ValidationResult result = validator.validate(transformationFile);

                if (result.success()) {
                    System.out.println("      ✓ Compilation successful");
                    // Update file content if it was fixed
                    if (!result.finalCode().equals(transformationFile.content())) {
                        transformationFile = new GeneratedFile(
                            transformationFile.fileName(),
                            result.finalCode()
                        );
                        System.out.println("      ✓ Code was automatically fixed by LLM");
                    }
                } else {
                    System.err.println("      ✗ Compilation failed:");
                    System.err.println(result.getErrorSummary());
                    System.err.println();
                    System.err.println("Generated code will still be written, but may not compile.");
                    System.err.println("Please review and fix manually, or re-run with different inputs.");
                }
                System.out.println();
            } else {
                System.out.println("[7/7] Skipping validation (--no-validate)");
                System.out.println();
            }

            // Write files to disk
            System.out.println("Writing generated files to: " + outputDir);
            Files.createDirectories(outputDir);

            Path transformationPath = outputDir.resolve(transformationFile.fileName());
            Files.writeString(transformationPath, transformationFile.content());
            System.out.println("      ✓ " + transformationPath);

            Path testPath = outputDir.resolve(testFile.fileName());
            Files.writeString(testPath, testFile.content());
            System.out.println("      ✓ " + testPath);

            System.out.println();
            System.out.println("════════════════════════════════════════════════════════════════");
            System.out.println("✓ Generation complete!");
            System.out.println("════════════════════════════════════════════════════════════════");

            return 0;

        } catch (IOException e) {
            System.err.println();
            System.err.println("ERROR: IO error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        } catch (Exception e) {
            System.err.println();
            System.err.println("ERROR: Unexpected error: " + e.getMessage());
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
