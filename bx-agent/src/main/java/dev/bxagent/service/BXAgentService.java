package dev.bxagent.service;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.codegen.TransformationCodegen;
import dev.bxagent.llm.LlmClient;
import dev.bxagent.mapping.BidirectionalityChecker;
import dev.bxagent.mapping.MappingExtractor;
import dev.bxagent.mapping.MappingModel;
import dev.bxagent.metamodel.EcoreParser;
import dev.bxagent.metamodel.MetamodelSummary;
import dev.bxagent.validation.CompilationValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * UI-agnostic facade over the bxagent pipeline.
 * CLI commands and the Eclipse plugin both delegate to this class.
 */
public class BXAgentService {

    /**
     * Immutable pipeline state passed between steps.
     * Fields are null until the corresponding step has run.
     */
    public record Session(
        MetamodelSummary.Summary leftSummary,
        MetamodelSummary.Summary rightSummary,
        MappingModel.TransformationSpec spec,
        String rawMappingJson,
        GeneratedFile generatedTransformation,
        GeneratedFile generatedTest
    ) {
        public Session withSpec(MappingModel.TransformationSpec spec) {
            return new Session(leftSummary, rightSummary, spec, rawMappingJson,
                generatedTransformation, generatedTest);
        }

        public Session withGenerated(GeneratedFile transformation, GeneratedFile test) {
            return new Session(leftSummary, rightSummary, spec, rawMappingJson,
                transformation, test);
        }
    }

    // ── Step 1: Load metamodels ──────────────────────────────────────────────

    public Session load(Path leftEcore, Path rightEcore) throws IOException {
        MetamodelSummary.Summary left  = EcoreParser.parse(leftEcore);
        MetamodelSummary.Summary right = EcoreParser.parse(rightEcore);
        return new Session(left, right, null, null, null, null);
    }

    // ── Step 2a: LLM extraction ──────────────────────────────────────────────

    /**
     * Calls the LLM (or loads from a cached JSON file) and returns an updated
     * Session with spec + rawMappingJson set. Does not run the bidirectionality check.
     *
     * @param fromJson  if non-null, skip the LLM and load from this JSON file
     * @param description  optional natural-language description passed to the LLM
     * @param excludedAttrs  attribute names to exclude from the spec
     */
    public Session extractSpec(
        Session s,
        LlmClient client,
        Path fromJson,
        String description,
        List<String> excludedAttrs,
        boolean debugLog
    ) throws Exception {

        MappingExtractor extractor = new MappingExtractor(client);
        extractor.setDebugLog(debugLog);

        MappingModel.TransformationSpec spec;
        String rawJson;

        if (fromJson != null) {
            rawJson = Files.readString(fromJson);
            spec    = extractor.extractFromJson(rawJson);
        } else {
            spec    = extractor.extract(s.leftSummary(), s.rightSummary(), description);
            rawJson = extractor.getLastRawResponse();
        }

        if (!excludedAttrs.isEmpty()) {
            spec = injectExcludes(spec, excludedAttrs);
        }

        return new Session(s.leftSummary(), s.rightSummary(), spec, rawJson,
            s.generatedTransformation(), s.generatedTest());
    }

    // ── Step 2b: Bidirectionality check ─────────────────────────────────────

    /**
     * Resolves unresolved backward mappings using the supplied choice resolver.
     * Pass null for non-interactive mode (unresolvable mappings are skipped).
     *
     * @param choiceResolver  receives a list of option labels, returns the chosen 0-based index;
     *                        Eclipse supplies a ListSelectionDialog-backed lambda
     */
    public Session checkBidirectionality(
        Session s,
        Function<List<String>, Integer> choiceResolver
    ) {
        BidirectionalityChecker checker = new BidirectionalityChecker(choiceResolver);
        MappingModel.TransformationSpec enhanced = checker.resolveUnresolvedMappings(s.spec());
        return s.withSpec(enhanced);
    }

    // ── Step 3: Generate code ────────────────────────────────────────────────

    /**
     * Runs FreeMarker and writes both generated files to {@code outputDir}.
     */
    public Session generate(Session s, Path outputDir) throws IOException {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile transformation = codegen.generateTransformation(s.spec());
        GeneratedFile test           = codegen.generateTest(s.spec());

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve(transformation.fileName()), transformation.content());
        Files.writeString(outputDir.resolve(test.fileName()), test.content());

        return s.withGenerated(transformation, test);
    }

    // ── Step 4: Validate ────────────────────────────────────────────────────

    /**
     * Compiles the generated transformation and runs the LLM fix loop on failure.
     */
    public CompilationValidator.ValidationResult validate(Session s, LlmClient client)
        throws Exception {
        CompilationValidator validator = new CompilationValidator(client);
        return validator.validate(s.generatedTransformation());
    }

    // ── Shared helper ────────────────────────────────────────────────────────

    /** Returns a new spec with the given excluded-attributes list applied. */
    public static MappingModel.TransformationSpec injectExcludes(
        MappingModel.TransformationSpec spec, List<String> excludes) {

        return new MappingModel.TransformationSpec(
            spec.sourcePackageName(), spec.targetPackageName(), spec.generatedClassName(),
            spec.typeMappings(), spec.attributeMappings(), spec.referenceMappings(),
            spec.unresolvedMappings(), spec.transformationOptions(),
            spec.roleBasedTypeMappings(), spec.backwardConfigs(),
            excludes,
            spec.edgeMaterializationMappings(), spec.aggregationMappings(),
            spec.structuralDeduplicationMappings(), spec.conditionalTypeMappings(),
            spec.syntheticObjectMappings(),
            spec.annotationContainerRef(), spec.annotationEClass(), spec.annotationTextAttr(),
            spec.targetLinkMappings(), spec.sqlTypeMapping(), spec.targetLinkMetamodel()
        );
    }
}
