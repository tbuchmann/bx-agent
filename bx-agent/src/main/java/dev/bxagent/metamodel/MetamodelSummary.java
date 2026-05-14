package dev.bxagent.metamodel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Records representing a parsed EMF metamodel in a compact, LLM-friendly format.
 */
public class MetamodelSummary {

    /**
     * Summary of an EAttribute.
     */
    public record AttributeSummary(
        String name,
        String type,        // e.g., "EString", "EInt", "EDate"
        boolean required,   // lowerBound >= 1
        boolean many        // upperBound > 1 or -1
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(":").append(type);
            if (many) {
                sb.append(" [*]");
            }
            if (required) {
                sb.append(" (required)");
            }
            return sb.toString();
        }
    }

    /**
     * Summary of an EReference.
     */
    public record ReferenceSummary(
        String name,
        String targetType,     // Name of the referenced EClass
        boolean containment,   // true if composition
        boolean many,          // upperBound > 1 or -1
        boolean required       // lowerBound >= 1
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" -> ").append(targetType);
            if (many) {
                sb.append(" [0..*]");
            } else if (required) {
                sb.append(" [1]");
            } else {
                sb.append(" [0..1]");
            }
            if (containment) {
                sb.append(" (containment)");
            }
            return sb.toString();
        }
    }

    /**
     * Summary of an EClass.
     */
    public record EClassSummary(
        String name,
        boolean isAbstract,
        List<String> superTypes,
        List<AttributeSummary> attributes,
        List<ReferenceSummary> references
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("  ").append(name);
            if (isAbstract) {
                sb.append(" (abstract)");
            }
            if (!superTypes.isEmpty()) {
                sb.append(" extends ").append(String.join(", ", superTypes));
            }
            sb.append("\n");

            if (!attributes.isEmpty()) {
                sb.append("    Attributes: ");
                sb.append(attributes.stream()
                    .map(AttributeSummary::toString)
                    .collect(Collectors.joining(", ")));
                sb.append("\n");
            }

            if (!references.isEmpty()) {
                sb.append("    References:\n");
                for (ReferenceSummary ref : references) {
                    sb.append("      - ").append(ref.toString()).append("\n");
                }
            }

            return sb.toString();
        }
    }

    /**
     * Complete metamodel summary.
     */
    public record Summary(
        String packageName,
        String nsURI,
        String nsPrefix,
        List<EClassSummary> classes
    ) {
        /**
         * Formats the metamodel as a compact text block for LLM prompts.
         */
        public String toPromptString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Package: ").append(packageName);
            sb.append(" (nsURI: ").append(nsURI).append(")");
            sb.append("\n\nClasses:\n");

            for (EClassSummary eClass : classes) {
                sb.append(eClass.toString());
            }

            return sb.toString();
        }
    }
}
