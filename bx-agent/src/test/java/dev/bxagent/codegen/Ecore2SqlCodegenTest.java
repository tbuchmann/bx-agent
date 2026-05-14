package dev.bxagent.codegen;

import dev.bxagent.mapping.MappingModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConditionalTypeMapping, SyntheticObjectMapping, and forwardAnnotations
 * using an Ecore-to-SQL scenario.
 *
 * Scenario:
 *   Source: Ecore (EPackage, EClass, EAttribute)
 *   Target: SQL (Schema, Table, Column, PrimaryKey, Annotation)
 *
 *   EAttribute → Column  (when upperBound == 1)
 *               → Table  (when upperBound != 1, i.e., multi-valued → junction table)
 *   SyntheticObjectMapping: EPackage → sentinel "_EObjectTable" Table (with id Column + PrimaryKey)
 *   forwardAnnotations on TypeMapping: annotate EClass-mapped Tables with "eclass"
 */
class Ecore2SqlCodegenTest {

    /**
     * Builds a TransformationSpec representing Ecore → SQL mapping.
     */
    private MappingModel.TransformationSpec buildEcore2SqlSpec() {
        // TypeMapping: EPackage → Schema, EClass → Table
        // EClass-mapped tables get a "eclass" annotation (forwardAnnotations)
        List<MappingModel.TypeMapping> typeMappings = List.of(
            new MappingModel.TypeMapping(
                "EPackage", "Schema",
                List.of("name"), List.of("name"),
                null,
                List.of()  // No forward annotations on Schema
            ),
            new MappingModel.TypeMapping(
                "EClass", "Table",
                List.of("name"), List.of("name"),
                null,
                List.of("eclass")  // EClass-mapped tables get "eclass" annotation
            )
        );

        // AttributeMappings: name fields for EPackage→Schema and EClass→Table
        // Also: EAttribute.name → Column.name (for bound=1 branch)
        List<MappingModel.AttributeMapping> attributeMappings = List.of(
            new MappingModel.AttributeMapping(
                "EPackage", "name", "EString",
                "Schema", "name", "EString",
                "source.getName()",
                "target.getName()"
            ),
            new MappingModel.AttributeMapping(
                "EClass", "name", "EString",
                "Table", "name", "EString",
                "source.getName()",
                "target.getName()"
            ),
            new MappingModel.AttributeMapping(
                "EAttribute", "name", "EString",
                "Column", "name", "EString",
                "source.getName()",
                "target.getName()"
            )
        );

        // ConditionalTypeMapping: EAttribute → Column (bound=1) or Table (bound!=1)
        // Container: EClass.eStructuralFeatures
        List<MappingModel.ConditionalBranch> branches = List.of(
            new MappingModel.ConditionalBranch(
                "_ctmSrc.getUpperBound() == 1",    // condition: singular attribute
                "Column",                           // targetType
                "PARENT",                           // targetPlacementType: add to mapped EClass→Table
                null,                               // targetPlacementRef (unused for PARENT)
                "ownedColumns",                     // targetContainerRef: Table.ownedColumns
                "source.getName()",                 // nameExpression
                List.of("eattribute"),              // forwardAnnotations
                "_ctmBwTyped.eContainer() instanceof sql.Table",  // backwardCondition
                null                                // backwardParentExpression (use default: map eContainer)
            ),
            new MappingModel.ConditionalBranch(
                "_ctmSrc.getUpperBound() != 1",    // condition: multi-valued → junction table
                "Table",                            // targetType
                "ROOT",                             // targetPlacementType: add to Schema (root)
                null,                               // targetPlacementRef
                "ownedTables",                      // targetContainerRef: Schema.ownedTables
                "source.getName() + \"_junction\"", // nameExpression
                List.of("junction"),                // forwardAnnotations
                "_ctmBwTyped.getName().endsWith(\"_junction\")",  // backwardCondition
                "allSourceObjects(_ctmBwSrcRes).stream().filter(o -> o instanceof ecore.EPackage).findFirst().orElse(null)"
                // backwardParentExpression: find EPackage in source model
            )
        );

        List<MappingModel.ConditionalTypeMapping> conditionalTypeMappings = List.of(
            new MappingModel.ConditionalTypeMapping(
                "EAttribute",       // sourceType
                "EClass",           // sourceContainerOwnerType
                "eStructuralFeatures",  // sourceContainerRef
                branches,
                List.of("name"),    // sourceKeyAttributes
                List.of("name")     // targetKeyAttributes
            )
        );

        // SyntheticObjectMapping: EPackage → sentinel "_EObject" Table with id Column + PrimaryKey
        List<MappingModel.SyntheticObjectMapping> syntheticObjectMappings = List.of(
            new MappingModel.SyntheticObjectMapping(
                "EPackage",         // sourceContainerType
                "Table",            // targetType
                "ownedTables",      // targetContainerRef: Schema.ownedTables
                "\"_EObject\"",     // nameExpression (literal string)
                List.of("sentinel"), // forwardAnnotations
                List.of(
                    new MappingModel.SyntheticColumnDef("id", "BIGINT", List.of("PRIMARY_KEY"))
                ),
                true,               // createPrimaryKey
                List.of("name"),    // targetKeyAttributes
                false               // nestedInMappedTarget
            )
        );

        return new MappingModel.TransformationSpec(
            "ecore",
            "sql",
            "Ecore2SqlTransformation",
            typeMappings,
            attributeMappings,
            List.of(),   // No direct reference mappings
            List.of(),   // No unresolved mappings
            List.of(),   // No transformation options
            List.of(),   // No role-based type mappings
            List.of(),   // No backward configs
            List.of(),   // No excluded attributes
            List.of(),   // No edge materialization mappings
            List.of(),   // No aggregation mappings
            List.of(),   // No structural deduplication mappings
            conditionalTypeMappings,
            syntheticObjectMappings,
            "ownedAnnotations",   // annotationContainerRef
            "Annotation",         // annotationEClass
            "text",               // annotationTextAttr
            List.of(),            // No target link mappings
            java.util.Map.of(),   // No SQL type mapping
            null                  // No target link metamodel
        );
    }

    @Test
    void testGeneratedFileNameAndPackage() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());

        assertNotNull(file);
        assertEquals("Ecore2SqlTransformation.java", file.fileName());

        String code = file.content();
        assertTrue(code.contains("package dev.bxagent.generated;"));
        assertTrue(code.contains("public class Ecore2SqlTransformation"));
        assertTrue(code.contains("import ecore.*;"));
        assertTrue(code.contains("import sql.*;"));
    }

    @Test
    void testAnnotationHelpersGenerated() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());
        String code = file.content();

        // Annotation helpers must be generated when annotationContainerRef/EClass/TextAttr are set
        assertTrue(code.contains("private static void _addAnnotation(EObject target, String tag)"),
            "Should generate _addAnnotation helper");
        assertTrue(code.contains("private static boolean _hasAnnotation(EObject target, String tag)"),
            "Should generate _hasAnnotation helper");

        // Helpers reference the configured annotation mechanism
        assertTrue(code.contains("getEStructuralFeature(\"ownedAnnotations\")"),
            "_addAnnotation should look for 'ownedAnnotations' feature");
        assertTrue(code.contains("getEPackage().getEClassifier(\"Annotation\")"),
            "_addAnnotation should create Annotation instances");
        assertTrue(code.contains("getEStructuralFeature(\"text\")"),
            "_addAnnotation should set 'text' attribute");
    }

    @Test
    void testAnnotationHelpersNotGeneratedWithoutConfig() {
        // Spec without annotation config
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "src", "tgt", "Test",
            List.of(new MappingModel.TypeMapping("A", "B", List.of(), List.of(), null, List.of())),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            null, null, null, List.of(), java.util.Map.of(), null  // No annotation config, no target link mappings
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        assertFalse(code.contains("_addAnnotation"),
            "Should NOT generate _addAnnotation when annotation config is absent");
        assertFalse(code.contains("_hasAnnotation"),
            "Should NOT generate _hasAnnotation when annotation config is absent");
    }

    @Test
    void testForwardAnnotationsOnTypeMapping() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());
        String code = file.content();

        // EClass→Table mapping has forwardAnnotation "eclass"
        // The transformEClass() helper should call _addAnnotation(target, "eclass")
        assertTrue(code.contains("_addAnnotation(target, \"eclass\")"),
            "EClass→Table forward transform should annotate target with 'eclass'");

        // EPackage→Schema mapping has no forward annotations — should NOT have a sentinel call
        // (we can't easily test absence by package, but we check EPackage helper doesn't annotate)
        // The "eclass" annotation should appear specifically in the EClass transform method
        assertTrue(code.contains("transformEClass(") || code.contains("transform(ecore.EClass"),
            "Should generate a helper method for EClass");
    }

    @Test
    void testSyntheticObjectMappingPhase0() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());
        String code = file.content();

        // Phase 0 method exists
        assertTrue(code.contains("private static void _createSyntheticObjects("),
            "Should generate _createSyntheticObjects method");

        // Called in transformBatch before Phase 1
        assertTrue(code.contains("_createSyntheticObjects(sourceRoots.get(i), targetRoots.get(i), objectMap)"),
            "transformBatch should call _createSyntheticObjects in Phase 0");

        // Creates sentinel table named "_EObject"
        assertTrue(code.contains("createTable()"),
            "Should create a Table in _createSyntheticObjects");
        assertTrue(code.contains("\"_EObject\""),
            "Synthetic table should have name '_EObject'");

        // Has the sentinel forward annotation
        assertTrue(code.contains("_addAnnotation(_syn, \"sentinel\")"),
            "Synthetic object should get 'sentinel' annotation");

        // Creates id Column with sqlType BIGINT
        assertTrue(code.contains("_col.setName(\"id\")"),
            "Synthetic table should have column named 'id'");
        assertTrue(code.contains("_col.setType(\"BIGINT\")"),
            "id column should have type BIGINT");

        // Creates PrimaryKey (createPrimaryKey=true)
        assertTrue(code.contains("createPrimaryKey()"),
            "Should create a PrimaryKey for the synthetic table");
        assertTrue(code.contains("getEStructuralFeature(\"ownedPrimaryKey\")"),
            "PrimaryKey should be attached via 'ownedPrimaryKey' feature");

        // Adds to ownedTables feature
        assertTrue(code.contains("getEStructuralFeature(\"ownedTables\")"),
            "Synthetic object should be added to 'ownedTables'");
    }

    @Test
    void testConditionalTypeMappingForwardPhase() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());
        String code = file.content();

        // Phase 1f block exists in transformBatch
        assertTrue(code.contains("Phase 1f: Conditional type mappings"),
            "Should have Phase 1f comment in transformBatch");

        // Iterates allSourceObjects to find EClass instances (sourceContainerOwnerType)
        assertTrue(code.contains("instanceof ecore.EClass _ctmParentObj"),
            "Phase 1f should filter for EClass containers");

        // Accesses eStructuralFeatures via reflective API
        assertTrue(code.contains("getEStructuralFeature(\"eStructuralFeatures\")"),
            "Should access 'eStructuralFeatures' reflectively");

        // Checks instanceof EAttribute (sourceType)
        assertTrue(code.contains("instanceof ecore.EAttribute _ctmSrc"),
            "Should check instanceof ecore.EAttribute");

        // Branch 1: bound==1 → Column
        assertTrue(code.contains("_ctmSrc.getUpperBound() == 1"),
            "Should have condition for singular attribute branch");
        assertTrue(code.contains("createColumn()"),
            "Should create Column for singular attributes");

        // Branch 1 places in PARENT (mapped EClass → Table)
        assertTrue(code.contains("EObject _ctmContainer = _ctmMappedParent"),
            "PARENT placement should use _ctmMappedParent");

        // Branch 1 sets name from nameExpression
        assertTrue(code.contains("setName(source.getName())"),
            "Branch 1 should set Column name via nameExpression");

        // Branch 1 adds to ownedColumns
        assertTrue(code.contains("getEStructuralFeature(\"ownedColumns\")"),
            "Branch 1 should add Column to 'ownedColumns'");

        // Branch 1 annotates with "eattribute"
        assertTrue(code.contains("_addAnnotation(_ctmNewTarget, \"eattribute\")"),
            "Branch 1 should annotate target with 'eattribute'");

        // Branch 2: bound!=1 → Table
        assertTrue(code.contains("_ctmSrc.getUpperBound() != 1"),
            "Should have condition for multi-valued attribute branch");
        assertTrue(code.contains("createTable()"),
            "Should create Table for multi-valued attributes");

        // Branch 2 places in ROOT
        assertTrue(code.contains("targetRoots.isEmpty() ? null : targetRoots.get(0)"),
            "ROOT placement should use targetRoots.get(0)");

        // Branch 2 junction table name expression
        assertTrue(code.contains("source.getName() + \"_junction\""),
            "Branch 2 should set junction table name");

        // Branch 2 annotates with "junction"
        assertTrue(code.contains("_addAnnotation(_ctmNewTarget, \"junction\")"),
            "Branch 2 should annotate target with 'junction'");
    }

    @Test
    void testConditionalTypeMappingBackwardPhase() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());
        String code = file.content();

        // Phase 1f backward block exists
        assertTrue(code.contains("Phase 1f backward: ConditionalTypeMappings"),
            "Should have Phase 1f backward comment");

        // Branch 1 backward: Column instance with backwardCondition
        assertTrue(code.contains("instanceof sql.Column _ctmBwTyped"),
            "Backward phase should check instanceof sql.Column");
        assertTrue(code.contains("_ctmBwTyped.eContainer() instanceof sql.Table"),
            "Branch 1 backward condition should check Column's container is a Table");

        // Branch 1 no backwardParentExpression → uses default (map eContainer)
        assertTrue(code.contains("_ctmBwObjectMapInverse.get(_ctmBwTgt.eContainer())"),
            "Branch 1 backward should resolve parent via objectMapInverse.get(eContainer)");

        // Branch 2 backward: junction Table with backwardCondition + backwardParentExpression
        assertTrue(code.contains("instanceof sql.Table _ctmBwTyped"),
            "Backward phase should check instanceof sql.Table");
        assertTrue(code.contains("_ctmBwTyped.getName().endsWith(\"_junction\")"),
            "Branch 2 backward condition should check junction name suffix");

        // Branch 2 backward uses custom backwardParentExpression
        assertTrue(code.contains("allSourceObjects(_ctmBwSrcRes).stream().filter(o -> o instanceof ecore.EPackage).findFirst().orElse(null)"),
            "Branch 2 backward should use custom backwardParentExpression to find EPackage");

        // Backward creates EAttribute source objects
        assertTrue(code.contains("createEAttribute()"),
            "Backward phase should create EAttribute objects");

        // Adds to source container feature (eStructuralFeatures of EClass)
        assertTrue(code.contains("getEStructuralFeature(\"eStructuralFeatures\")"),
            "Backward phase should add EAttribute to 'eStructuralFeatures'");
    }

    @Test
    void testConditionalTypeMappingAttributeBackwardMappings() {
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(buildEcore2SqlSpec());
        String code = file.content();

        // AttributeMapping: EAttribute.name ← Column.name (backward)
        // In branch 1 backward: source.setName(target.getName())
        assertTrue(code.contains("source.setName(target.getName())"),
            "Branch 1 backward attribute mapping: EAttribute.name ← Column.name");
    }

    @Test
    void testNoPrimaryKeyWhenCreatePrimaryKeyFalse() {
        // Build spec with createPrimaryKey = false
        MappingModel.SyntheticObjectMapping somNoPk = new MappingModel.SyntheticObjectMapping(
            "EPackage", "Table", "ownedTables", "\"_NoPK\"",
            List.of(),
            List.of(new MappingModel.SyntheticColumnDef("id", "INT", List.of())),
            false,  // createPrimaryKey = false
            List.of(),
            false   // nestedInMappedTarget
        );

        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "ecore", "sql", "NoPkTransformation",
            List.of(new MappingModel.TypeMapping("EPackage", "Schema", List.of(), List.of(), null, List.of())),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(somNoPk),
            null, null, null, List.of(), java.util.Map.of(), null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Should create column but NOT PrimaryKey
        assertTrue(code.contains("_col.setName(\"id\")"), "Should still create column");
        assertFalse(code.contains("createPrimaryKey()"), "Should NOT create PrimaryKey when createPrimaryKey=false");
    }

    @Test
    void testConditionalTypeMappingWithNullTargetTypeBranch() {
        // Branch with null targetType: marks source as handled, produces nothing
        List<MappingModel.ConditionalBranch> branches = List.of(
            new MappingModel.ConditionalBranch(
                "_ctmSrc.isTransient()",   // condition: skip transient attributes
                null,                       // targetType = null → produce nothing
                "PARENT", null, "ownedColumns",
                null, List.of(), null, null
            ),
            new MappingModel.ConditionalBranch(
                null,                       // else branch: singular → Column
                "Column",
                "PARENT", null, "ownedColumns",
                "source.getName()",
                List.of(), null, null
            )
        );

        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "ecore", "sql", "SkipTransientTransformation",
            List.of(new MappingModel.TypeMapping("EPackage", "Schema", List.of(), List.of(), null, List.of())),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            List.of(new MappingModel.ConditionalTypeMapping(
                "EAttribute", "EClass", "eStructuralFeatures", branches,
                List.of(), List.of()
            )),
            List.of(),
            "ownedAnnotations", "Annotation", "text",
            List.of(), java.util.Map.of(), null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Null-target branch: mark as handled, skip
        assertTrue(code.contains("null targetType branch: mark as handled"),
            "Should generate skip comment for null-targetType branch");
        assertTrue(code.contains("_ctmSrc.isTransient()"),
            "Should include the transient-skip condition");
    }

    @Test
    void testCtmSourceTypesFilteredFromRegularTypeMappings() {
        // EAttribute appears in both typeMappings and conditionalTypeMappings.
        // The buildDataModel should filter it out from typeMappings to avoid duplicate handling.
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "ecore", "sql", "FilterTest",
            List.of(
                new MappingModel.TypeMapping("EPackage", "Schema", List.of(), List.of(), null, List.of()),
                // This should be filtered out — EAttribute is handled by ConditionalTypeMapping
                new MappingModel.TypeMapping("EAttribute", "Column", List.of(), List.of(), null, List.of())
            ),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            List.of(new MappingModel.ConditionalTypeMapping(
                "EAttribute", "EClass", "eStructuralFeatures",
                List.of(new MappingModel.ConditionalBranch(
                    null, "Column", "PARENT", null, "ownedColumns",
                    null, List.of(), null, null
                )),
                List.of(), List.of()
            )),
            List.of(),
            null, null, null, List.of(), java.util.Map.of(), null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // EPackage→Schema transform should be generated
        assertTrue(code.contains("EPackage"), "Should handle EPackage TypeMapping");

        // EAttribute→Column should NOT appear as a regular TypeMapping transform
        // (it would produce a duplicate transformEAttribute method)
        // Check that Phase 1 doesn't have an EAttribute branch alongside EPackage
        // The Phase 1f handles EAttribute, not the regular createAndMapObjects loop
        assertFalse(code.contains("instanceof ecore.EAttribute _child") &&
                    code.contains("createColumn()") && code.contains("transformEAttribute"),
            "EAttribute should not appear as a regular TypeMapping child (filtered by CTM)");
    }
}
