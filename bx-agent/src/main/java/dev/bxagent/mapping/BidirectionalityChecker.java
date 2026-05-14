package dev.bxagent.mapping;

import dev.bxagent.cli.InteractivePrompter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Checks transformation specifications for bidirectionality issues
 * and resolves them through user interaction.
 */
public class BidirectionalityChecker {

    private final InteractivePrompter prompter;
    private final Function<List<String>, Integer> choiceResolver;

    public BidirectionalityChecker(InteractivePrompter prompter) {
        this.prompter = prompter;
        this.choiceResolver = null;
    }

    /** For programmatic callers (Eclipse, tests): resolver receives option labels, returns 0-based index. */
    public BidirectionalityChecker(Function<List<String>, Integer> choiceResolver) {
        this.prompter = null;
        this.choiceResolver = choiceResolver;
    }

    /** Default constructor using System.in/out. */
    public BidirectionalityChecker() {
        this(new InteractivePrompter());
    }

    /**
     * Checks and resolves all unresolved backward mappings in the specification.
     * BackwardConfig entries are considered already resolved by configuration and
     * are not forwarded to the interactive prompter.
     *
     * @param spec Original transformation specification
     * @return Updated specification with resolved backward mappings
     */
    public MappingModel.TransformationSpec resolveUnresolvedMappings(
        MappingModel.TransformationSpec spec
    ) {
        // If no unresolved mappings, return as-is
        if (spec.unresolvedMappings().isEmpty()) {
            System.out.println("[BidirectionalityChecker] No unresolved mappings - all clear!");
            return new MappingModel.TransformationSpec(
                spec.sourcePackageName(),
                spec.targetPackageName(),
                spec.generatedClassName(),
                spec.typeMappings(),
                spec.attributeMappings(),
                spec.referenceMappings(),
                List.of(),
                spec.transformationOptions(),
                spec.roleBasedTypeMappings(),
                spec.backwardConfigs(),
                spec.excludedAttributes(),
                spec.edgeMaterializationMappings(),
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
        }

        // BackwardConfigs represent intentional configuration choices, not ambiguities.
        // Log their presence and don't forward to interactive prompter.
        if (!spec.backwardConfigs().isEmpty()) {
            System.out.println("[BidirectionalityChecker] " + spec.backwardConfigs().size()
                + " backward config(s) found — backward direction handled by configuration parameters.");
        }

        System.out.println("[BidirectionalityChecker] Found " + spec.unresolvedMappings().size()
            + " unresolved mapping(s)");

        // Create mutable copy of attribute mappings
        List<MappingModel.AttributeMapping> updatedMappings = new ArrayList<>(spec.attributeMappings());

        // Process each unresolved mapping that has a corresponding attribute with no backward expression
        for (String unresolvedDescription : spec.unresolvedMappings()) {
            // Find corresponding attribute mapping with null backwardExpression
            MappingModel.AttributeMapping unmappedAttr = findUnmappedAttribute(updatedMappings);

            if (unmappedAttr == null) {
                // No attribute mapping with null backward — covered by BackwardConfig or just informational
                System.out.println("[BidirectionalityChecker] Note: '" + unresolvedDescription
                    + "' has no corresponding attribute mapping — handled by BackwardConfig or skipped.");
                continue;
            }

            // Resolve via available mechanism
            InteractivePrompter.BackwardMappingStrategy strategy;
            if (choiceResolver != null) {
                strategy = resolveViaFunction(unmappedAttr, unresolvedDescription);
            } else if (prompter != null) {
                strategy = prompter.promptForBackwardMapping(
                    unresolvedDescription,
                    unmappedAttr.sourceAttr(),
                    unmappedAttr.targetAttr()
                );
            } else {
                System.out.println("[BidirectionalityChecker] Non-interactive mode: skipping '"
                    + unresolvedDescription + "'");
                continue;
            }

            // Update the mapping based on strategy
            MappingModel.AttributeMapping updatedMapping = applyStrategy(unmappedAttr, strategy);
            int index = updatedMappings.indexOf(unmappedAttr);
            if (index >= 0) {
                updatedMappings.set(index, updatedMapping);
            }
        }

        // Return updated spec with resolved mappings and cleared unresolvedMappings list
        return new MappingModel.TransformationSpec(
            spec.sourcePackageName(),
            spec.targetPackageName(),
            spec.generatedClassName(),
            spec.typeMappings(),
            updatedMappings,
            spec.referenceMappings(),
            List.of(),  // Clear unresolved mappings
            spec.transformationOptions(),
            spec.roleBasedTypeMappings(),
            spec.backwardConfigs(),
            spec.excludedAttributes(),
            spec.edgeMaterializationMappings(),
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
    }

    /**
     * Resolves a backward mapping using the injected choice resolver (0-based index).
     * Offers three options: Default value, Throw exception, Skip.
     * Custom expressions are not supported in this path (no text-input channel available).
     */
    private InteractivePrompter.BackwardMappingStrategy resolveViaFunction(
        MappingModel.AttributeMapping attr, String description) {

        List<String> mainOptions = List.of(
            "Default value (null, \"\", 0, or false)",
            "Throw RuntimeException (lossy transformation)",
            "Skip (omit from backward direction)"
        );
        int choice = choiceResolver.apply(mainOptions);

        return switch (choice) {
            case 0 -> {
                List<String> defaults = List.of("null", "\"\" (empty string)", "0 (number)", "false (boolean)");
                int dChoice = choiceResolver.apply(defaults);
                String val = switch (dChoice) {
                    case 0 -> "null"; case 1 -> "\"\""; case 2 -> "0"; default -> "false";
                };
                yield new InteractivePrompter.BackwardMappingStrategy.DefaultValue(val);
            }
            case 1 -> new InteractivePrompter.BackwardMappingStrategy.ThrowException();
            default -> new InteractivePrompter.BackwardMappingStrategy.Skip();
        };
    }

    /**
     * Finds the first attribute mapping with null backwardExpression.
     */
    private MappingModel.AttributeMapping findUnmappedAttribute(
        List<MappingModel.AttributeMapping> mappings
    ) {
        for (MappingModel.AttributeMapping mapping : mappings) {
            if (mapping.backwardExpression() == null) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Applies the user's chosen strategy to an attribute mapping.
     */
    private MappingModel.AttributeMapping applyStrategy(
        MappingModel.AttributeMapping original,
        InteractivePrompter.BackwardMappingStrategy strategy
    ) {
        String newBackwardExpression = switch (strategy) {
            case InteractivePrompter.BackwardMappingStrategy.CustomExpression(String expr) ->
                expr;

            case InteractivePrompter.BackwardMappingStrategy.DefaultValue(String value) ->
                value;

            case InteractivePrompter.BackwardMappingStrategy.ThrowException() ->
                "throw new UnsupportedOperationException(\"Backward mapping not supported for "
                    + original.targetAttr() + "\")";

            case InteractivePrompter.BackwardMappingStrategy.Skip() ->
                null;  // Keep it null to skip in code generation
        };

        return new MappingModel.AttributeMapping(
            original.sourceOwnerType(),
            original.sourceAttr(),
            original.sourceAttrType(),
            original.targetOwnerType(),
            original.targetAttr(),
            original.targetAttrType(),
            original.forwardExpression(),
            newBackwardExpression
        );
    }
}
