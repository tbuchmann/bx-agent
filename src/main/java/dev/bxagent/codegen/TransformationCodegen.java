package dev.bxagent.codegen;

import dev.bxagent.mapping.MappingModel;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates Java transformation code from a TransformationSpec using FreeMarker templates.
 */
public class TransformationCodegen {

    private final Configuration freemarkerConfig;

    public TransformationCodegen() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates");
        freemarkerConfig.setDefaultEncoding("UTF-8");
    }

    /**
     * Generates the transformation class code.
     *
     * @param spec Transformation specification
     * @return GeneratedFile with class name and content
     * @throws RuntimeException if code generation fails
     */
    public GeneratedFile generateTransformation(MappingModel.TransformationSpec spec) {
        try {
            Template template = freemarkerConfig.getTemplate("Transformation.java.ftl");

            Map<String, Object> dataModel = buildDataModel(spec);

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);

            String fileName = deriveClassName(spec.sourcePackageName(), spec.targetPackageName()) + ".java";
            return new GeneratedFile(fileName, writer.toString());

        } catch (IOException | TemplateException e) {
            throw new RuntimeException("Failed to generate transformation code", e);
        }
    }

    /**
     * Generates a test skeleton for the transformation.
     *
     * @param spec Transformation specification
     * @return GeneratedFile with test class name and content
     * @throws RuntimeException if code generation fails
     */
    public GeneratedFile generateTest(MappingModel.TransformationSpec spec) {
        try {
            Template template = freemarkerConfig.getTemplate("TransformationTest.java.ftl");

            Map<String, Object> dataModel = buildDataModel(spec);

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);

            String fileName = deriveClassName(spec.sourcePackageName(), spec.targetPackageName()) + "Test.java";
            return new GeneratedFile(fileName, writer.toString());

        } catch (IOException | TemplateException e) {
            throw new RuntimeException("Failed to generate test code", e);
        }
    }

    /**
     * Builds the FreeMarker data model from the transformation spec.
     */
    private Map<String, Object> buildDataModel(MappingModel.TransformationSpec spec) {
        Map<String, Object> model = new HashMap<>();

        // Collect sourceTypes covered by roleBasedTypeMappings or conditionalTypeMappings so we
        // can filter overlapping entries that the LLM may have also included in typeMappings/attributeMappings.
        Set<String> roleBasedSourceTypes = spec.roleBasedTypeMappings().stream()
            .map(MappingModel.RoleBasedTypeMapping::sourceType)
            .collect(Collectors.toSet());
        // Also exclude sourceTypes exclusively handled by conditionalTypeMappings
        spec.conditionalTypeMappings().stream()
            .map(MappingModel.ConditionalTypeMapping::sourceType)
            .forEach(roleBasedSourceTypes::add);

        // Bug 6: filter TypeMappings whose sourceType is already handled by a RoleBasedTypeMapping
        // Also strip excluded attributes from sourceKeyAttributes/targetKeyAttributes so they are
        // not used as fingerprint keys (e.g. when -e incrementalID is passed via CLI).
        Set<String> excludedAttrs = new java.util.HashSet<>(spec.excludedAttributes());
        List<MappingModel.TypeMapping> filteredTypeMappings = spec.typeMappings().stream()
            .filter(tm -> !roleBasedSourceTypes.contains(tm.sourceType()))
            .map(tm -> excludedAttrs.isEmpty() ? tm : new MappingModel.TypeMapping(
                tm.sourceType(), tm.targetType(),
                tm.sourceKeyAttributes().stream().filter(k -> !excludedAttrs.contains(k)).toList(),
                tm.targetKeyAttributes().stream().filter(k -> !excludedAttrs.contains(k)).toList(),
                tm.backwardCondition(), tm.forwardAnnotations()))
            .toList();
        List<MappingModel.AttributeMapping> filteredAttributeMappings = spec.attributeMappings().stream()
            .filter(am -> !roleBasedSourceTypes.contains(am.sourceOwnerType()))
            .filter(am -> !excludedAttrs.contains(am.sourceAttr()) && !excludedAttrs.contains(am.targetAttr()))
            .toList();
        // CTM blocks need attribute mappings for CTM source types (e.g. EAttribute→Column/Table).
        // roleBasedSourceTypes includes CTM source types, so filteredAttributeMappings drops them.
        // ctmAttributeMappings excludes only pure role-based types (not CTM source types).
        Set<String> roleOnlySourceTypes = spec.roleBasedTypeMappings().stream()
            .map(MappingModel.RoleBasedTypeMapping::sourceType)
            .collect(Collectors.toSet());
        List<MappingModel.AttributeMapping> ctmAttributeMappings = spec.attributeMappings().stream()
            .filter(am -> !roleOnlySourceTypes.contains(am.sourceOwnerType()))
            .filter(am -> !excludedAttrs.contains(am.sourceAttr()) && !excludedAttrs.contains(am.targetAttr()))
            .toList();

        // Bug 1: filter TransformationOptions that duplicate a BackwardConfig parameter
        Set<String> backwardConfigNames = spec.backwardConfigs().stream()
            .map(MappingModel.BackwardConfig::parameterName)
            .collect(Collectors.toSet());
        List<MappingModel.TransformationOption> filteredOptions = spec.transformationOptions().stream()
            .filter(opt -> !backwardConfigNames.contains(opt.name()))
            .toList();

        // Basic spec fields
        model.put("sourcePackageName", spec.sourcePackageName());
        model.put("targetPackageName", spec.targetPackageName());
        // Class name is always derived from package names, not from spec
        model.put("generatedClassName", deriveClassName(spec.sourcePackageName(), spec.targetPackageName()));
        model.put("typeMappings", filteredTypeMappings);
        model.put("attributeMappings", filteredAttributeMappings);
        model.put("ctmAttributeMappings", ctmAttributeMappings);
        model.put("referenceMappings", spec.referenceMappings());
        model.put("transformationOptions", filteredOptions);

        // Group TypeMappings by targetType for backward dispatch.
        // When multiple source types share the same targetType (m:1 mapping),
        // the backward direction needs a discriminator (backwardCondition) per entry.
        Map<String, List<MappingModel.TypeMapping>> byTarget = new LinkedHashMap<>();
        for (MappingModel.TypeMapping tm : filteredTypeMappings) {
            byTarget.computeIfAbsent(tm.targetType(), k -> new ArrayList<>()).add(tm);
        }
        List<Map<String, Object>> typeMappingGroups = new ArrayList<>();
        for (Map.Entry<String, List<MappingModel.TypeMapping>> e : byTarget.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("targetType", e.getKey());
            g.put("mappings", e.getValue());
            g.put("hasMultiple", e.getValue().size() > 1);
            typeMappingGroups.add(g);
        }
        model.put("typeMappingGroups", typeMappingGroups);

        // Derived values: root type from first remaining TypeMapping
        if (!filteredTypeMappings.isEmpty()) {
            MappingModel.TypeMapping rootMapping = filteredTypeMappings.get(0);
            model.put("rootSourceType", rootMapping.sourceType());
            model.put("rootTargetType", rootMapping.targetType());
        } else if (!spec.typeMappings().isEmpty()) {
            // Fallback to original first mapping if all were filtered (shouldn't happen in practice)
            MappingModel.TypeMapping rootMapping = spec.typeMappings().get(0);
            model.put("rootSourceType", rootMapping.sourceType());
            model.put("rootTargetType", rootMapping.targetType());
        }

        // Factory names (EMF convention: <Package>Factory)
        model.put("sourceFactory", capitalize(spec.sourcePackageName()) + "Factory");
        model.put("targetFactory", capitalize(spec.targetPackageName()) + "Factory");

        // BackwardConfig parameters (merged into Options record alongside TransformationOptions)
        model.put("backwardConfigs", spec.backwardConfigs());

        // Excluded attributes: skipped in mapping and in reflective fingerprint fallback
        model.put("excludedAttributes", spec.excludedAttributes());

        // Edge materialization mappings: references → explicit edge objects
        model.put("edgeMaterializationMappings", spec.edgeMaterializationMappings());

        // Aggregation mappings: many source objects → one target object per group key
        model.put("aggregationMappings", spec.aggregationMappings());

        // Structural deduplication mappings: tree → DAG (equal subtrees → shared target)
        model.put("structuralDeduplicationMappings", spec.structuralDeduplicationMappings());

        // Conditional type mappings: one source type → multiple target types by condition
        model.put("conditionalTypeMappings", spec.conditionalTypeMappings());

        // Synthetic object mappings: fixed-structure objects without source counterpart
        model.put("syntheticObjectMappings", spec.syntheticObjectMappings());

        // Annotation mechanism (may be null if target metamodel has no annotation support)
        model.put("annotationContainerRef", spec.annotationContainerRef());
        model.put("annotationEClass", spec.annotationEClass());
        model.put("annotationTextAttr", spec.annotationTextAttr());

        // Target link mappings (Phase 1.6)
        model.put("targetLinkMappings", spec.targetLinkMappings());
        model.put("sqlTypeMapping", spec.sqlTypeMapping() != null ? spec.sqlTypeMapping() : java.util.Map.of());
        model.put("targetLinkMetamodel", spec.targetLinkMetamodel());

        // RoleBasedTypeMappings with precomputed role groups for backward assignment
        List<Map<String, Object>> roleBasedTypeMappingModels = new ArrayList<>();
        for (MappingModel.RoleBasedTypeMapping rbm : spec.roleBasedTypeMappings()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rbm", rbm);

            // roleEntries: flat list of {role, targetType, isMany} in declaration order
            List<Map<String, Object>> roleEntries = new ArrayList<>();
            for (Map.Entry<String, String> roleEntry : rbm.roleToTargetType().entrySet()) {
                Map<String, Object> re = new LinkedHashMap<>();
                re.put("role", roleEntry.getKey());
                re.put("targetType", roleEntry.getValue());
                re.put("isMany", rbm.roleIsMany().getOrDefault(roleEntry.getKey(), false));
                roleEntries.add(re);
            }
            entry.put("roleEntries", roleEntries);

            // roleGroups: per unique target type: {targetType, singleRole (nullable), multiRoles: List<String>}
            Map<String, Map<String, Object>> byTargetType = new LinkedHashMap<>();
            for (Map.Entry<String, String> roleEntry : rbm.roleToTargetType().entrySet()) {
                String role = roleEntry.getKey();
                String targetType = roleEntry.getValue();
                boolean isMany = rbm.roleIsMany().getOrDefault(role, false);
                Map<String, Object> group = byTargetType.computeIfAbsent(targetType, t -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("targetType", t);
                    g.put("singleRole", null);
                    g.put("multiRoles", new ArrayList<String>());
                    return g;
                });
                if (!isMany) {
                    group.put("singleRole", role);
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> multiRoles = (List<String>) group.get("multiRoles");
                    multiRoles.add(role);
                }
            }
            entry.put("roleGroups", new ArrayList<>(byTargetType.values()));
            roleBasedTypeMappingModels.add(entry);
        }
        model.put("roleBasedTypeMappingModels", roleBasedTypeMappingModels);

        return model;
    }

    /**
     * Derives the transformation class name from the two package names.
     * Convention: capitalize(sourcePkg) + "2" + capitalize(targetPkg) + "Transformation"
     * Example: "pdb1" + "pdb2" → "Pdb12Pdb2Transformation"
     */
    private String deriveClassName(String sourcePkg, String targetPkg) {
        return capitalize(sourcePkg) + "2" + capitalize(targetPkg) + "Transformation";
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
