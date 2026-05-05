package dev.bxagent.codegen;

import dev.bxagent.mapping.MappingModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Families2Persons transformation.
 *
 * <p>Test model: Familie "Simpson"
 * <ul>
 *   <li>father = Homer</li>
 *   <li>mother = Marge</li>
 *   <li>sons = [Bart]</li>
 *   <li>daughters = [Lisa, Maggie]</li>
 * </ul>
 *
 * <p>Expected forward result:
 * Male "Simpson, Homer", Female "Simpson, Marge",
 * Male "Simpson, Bart", Female "Simpson, Lisa", Female "Simpson, Maggie"
 *
 * <p>Expected backward (preferExistingFamily=true, preferParent=true):
 * Family "Simpson" with father=Homer, mother=Marge, sons=[Bart], daughters=[Lisa, Maggie]
 */
class Families2PersonsCodegenTest {

    /**
     * Builds the F2P TransformationSpec.
     * - FamilyRegister → PersonRegister (rootSourceType, rootTargetType)
     * - FamilyMember in roles father/sons → Male, mother/daughters → Female
     * - name = family.getName() + ", " + member.getName()
     * - backward: preferExistingFamily (reuse/create Family), preferParent (father/mother priority)
     */
    private MappingModel.TransformationSpec buildF2PSpec() {
        Map<String, String> roleToTargetType = new LinkedHashMap<>();
        roleToTargetType.put("father", "Male");
        roleToTargetType.put("mother", "Female");
        roleToTargetType.put("sons", "Male");
        roleToTargetType.put("daughters", "Female");

        Map<String, Boolean> roleIsMany = new LinkedHashMap<>();
        roleIsMany.put("father", false);
        roleIsMany.put("mother", false);
        roleIsMany.put("sons", true);
        roleIsMany.put("daughters", true);

        MappingModel.RoleBasedTypeMapping rbm = new MappingModel.RoleBasedTypeMapping(
            "FamilyMember",   // sourceType
            "Family",          // intermediateType
            roleToTargetType,
            roleIsMany,
            "name",            // targetAttr: the attribute to set on Male/Female
            "family.getName() + \", \" + member.getName()",  // nameExpression
            "families",        // sourceContainerRef: FamilyRegister.getFamilies()
            "persons",         // targetContainerRef: PersonRegister.getPersons()
            "Person",          // targetContainerElementType: declared element type of getPersons()
            "name",            // intermediateAttr: Family.setName() in backward
            "name",            // sourceAttr: FamilyMember.setName() in backward
            "targetObj.getName().split(\", \", 2)[0]",   // backwardFamilyNameExpression
            "targetObj.getName().split(\", \", 2)[1]",   // backwardMemberNameExpression
            "preferExistingFamily",  // backwardPreferExistingParam
            "preferParent",          // backwardPreferSingleRoleParam
            List.of("name"),         // sourceKeyAttributes (FamilyMember.name)
            List.of("name")          // targetKeyAttributes (Male/Female.name)
        );

        List<MappingModel.BackwardConfig> backwardConfigs = List.of(
            new MappingModel.BackwardConfig(
                "preferExistingFamily", "boolean", "true",
                "If true, reuse an existing Family with the same name instead of creating a new one"
            ),
            new MappingModel.BackwardConfig(
                "preferParent", "boolean", "true",
                "If true, assign Male to father and Female to mother when the role is still free"
            )
        );

        return new MappingModel.TransformationSpec(
            "Families",
            "Persons",
            "Families2PersonsTransformation",
            List.of(
                new MappingModel.TypeMapping("FamilyRegister", "PersonRegister", List.of("name"), List.of("name"), null, List.of())
            ),
            List.of(),   // No direct attribute mappings (all via roleBasedTypeMappings)
            List.of(),   // No reference mappings (Family structure is handled via roleBasedTypeMappings)
            List.of(),   // No unresolved mappings
            List.of(),   // No transformation options
            List.of(rbm),
            backwardConfigs,
            List.of(),   // No excluded attributes
            List.of(),   // No edge materialization mappings
            List.of(),   // No aggregation mappings
            List.of(),   // No structural deduplication mappings
            List.of(),   // No conditional type mappings
            List.of(),   // No synthetic object mappings
            null,        // No annotation container ref
            null,        // No annotation EClass
            null,        // No annotation text attr
            List.of(),   // No target link mappings
            java.util.Map.of(), // No SQL type mapping
            null         // No target link metamodel
        );
    }

    @Test
    void testGeneratedCodeContainsOptions() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();

        // Options record includes both BackwardConfig parameters
        assertTrue(code.contains("boolean preferExistingFamily"), "Options should have preferExistingFamily");
        assertTrue(code.contains("boolean preferParent"), "Options should have preferParent");
        assertTrue(code.contains("return new Options("), "Options.defaults() should exist");
        // Default values should be true
        assertTrue(code.contains("true"), "Default values should be true");
    }

    @Test
    void testGeneratedCodeContainsForwardRoleMapping() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();
        System.out.println("=== Generated Code ===\n" + code);

        // mapRoleBasedTypes method exists
        assertTrue(code.contains("mapRoleBasedTypes("), "Should have mapRoleBasedTypes method");

        // Iterates over families (reflective API since rootSourceType may be absent)
        assertTrue(code.contains("getEStructuralFeature(\"families\")"),
            "Should iterate over families from source root via reflective API");

        // Single-valued roles: father and mother (with null check)
        assertTrue(code.contains("family.getFather() != null"),
            "Should null-check father");
        assertTrue(code.contains("family.getMother() != null"),
            "Should null-check mother");

        // Multi-valued roles: sons and daughters (with for loop)
        assertTrue(code.contains("family.getSons()"),
            "Should iterate sons");
        assertTrue(code.contains("family.getDaughters()"),
            "Should iterate daughters");

        // Creates Male and Female objects
        assertTrue(code.contains("PersonsFactory.eINSTANCE.createMale()"),
            "Should create Male objects");
        assertTrue(code.contains("PersonsFactory.eINSTANCE.createFemale()"),
            "Should create Female objects");

        // Sets name using the nameExpression
        assertTrue(code.contains("family.getName() + \", \" + member.getName()"),
            "Should use nameExpression");

        // Adds to target root (reflective API)
        assertTrue(code.contains("getEStructuralFeature(\"persons\")"),
            "Should add to PersonRegister.getPersons() via reflective API");

        // Adds to objectMap
        assertTrue(code.contains("objectMap.put(member, obj)"),
            "Should add to objectMap");
    }

    @Test
    void testGeneratedCodeContainsBackwardRoleMapping() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();

        // mapRoleBasedTypesBack method exists
        assertTrue(code.contains("mapRoleBasedTypesBack("), "Should have mapRoleBasedTypesBack method");

        // familyLookup map for reusing families
        assertTrue(code.contains("familyLookup"), "Should have familyLookup map");

        // Iterates over persons (reflective API)
        assertTrue(code.contains("getEStructuralFeature(\"persons\")"),
            "Should iterate over persons from target root via reflective API");

        // Name extraction using split
        assertTrue(code.contains(".split(\", \", 2)[0]"),
            "Should extract family name from target");
        assertTrue(code.contains(".split(\", \", 2)[1]"),
            "Should extract member name from target");

        // preferExistingFamily lookup
        assertTrue(code.contains("options.preferExistingFamily()"),
            "Should use preferExistingFamily option");
        assertTrue(code.contains("familyLookup.getOrDefault(intermediateKey, null)"),
            "Should look up existing family");

        // Creates Family
        assertTrue(code.contains("FamiliesFactory.eINSTANCE.createFamily()"),
            "Should create Family objects");
        assertTrue(code.contains("family.setName(intermediateKey)"),
            "Should set family name");
        assertTrue(code.contains("getEStructuralFeature(\"families\")"),
            "Should add family to source root via reflective API");

        // Creates FamilyMember
        assertTrue(code.contains("FamiliesFactory.eINSTANCE.createFamilyMember()"),
            "Should create FamilyMember objects");
        assertTrue(code.contains("member.setName(memberName)"),
            "Should set member name");

        // preferParent logic for Male → father or sons
        assertTrue(code.contains("instanceof Persons.Male") || code.contains("instanceof persons.Male"),
            "Should check instanceof Male");
        assertTrue(code.contains("options.preferParent()"),
            "Should use preferParent option");
        assertTrue(code.contains("family.getFather() == null"),
            "Should check if father slot is free");
        assertTrue(code.contains("family.setFather(member)"),
            "Should assign to father role");
        assertTrue(code.contains("family.getSons().add(member)"),
            "Should fall back to sons");

        // preferParent logic for Female → mother or daughters
        assertTrue(code.contains("family.getMother() == null"),
            "Should check if mother slot is free");
        assertTrue(code.contains("family.setMother(member)"),
            "Should assign to mother role");
        assertTrue(code.contains("family.getDaughters().add(member)"),
            "Should fall back to daughters");
    }

    @Test
    void testGeneratedCodeCallsMappingMethodsInTransform() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();

        // The main transform() method calls mapRoleBasedTypes
        assertTrue(code.contains("mapRoleBasedTypes("),
            "transform() should call mapRoleBasedTypes");

        // The main transformBack() method calls mapRoleBasedTypesBack
        assertTrue(code.contains("mapRoleBasedTypesBack("),
            "transformBack() should call mapRoleBasedTypesBack");
    }

    @Test
    void testGeneratedCodeContainsTransformationContextOverloads() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // New context-based entry points
        assertTrue(code.contains("TransformationContext ctx"), "Should have TransformationContext parameter");
        assertTrue(code.contains("ctx.isIncremental()"), "Should dispatch on isIncremental()");
        assertTrue(code.contains("transformBatch("), "Should call transformBatch");
        assertTrue(code.contains("transformIncremental("), "Should call transformIncremental");
    }

    @Test
    void testGeneratedCodeContainsComputeFingerprint() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // computeFingerprint uses key attribute "name"
        assertTrue(code.contains("computeFingerprint("), "Should have computeFingerprint method");
        assertTrue(code.contains("typed.getName()"), "Should use getName() for key attribute");
        // Falls back to reflective API for types without key attrs
        assertTrue(code.contains("eClass().getEAllAttributes()"), "Should have reflective fallback");
    }

    @Test
    void testGeneratedCodeContainsBatchCorrespondenceCreation() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Batch path creates correspondence model
        assertTrue(code.contains("CorrespondenceModel.deriveCorrespondenceURI("), "Should derive corr URI");
        assertTrue(code.contains("CorrespondenceModel.loadOrCreate("), "Should load or create corr model");
        assertTrue(code.contains("CorrespondenceModel.addEntry("), "Should add entries");
        assertTrue(code.contains("CorrespondenceModel.saveAndUpdateTimestamp("), "Should save corr model");
    }

    @Test
    void testGeneratedCodeContainsIncrementalPath() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Incremental helpers
        assertTrue(code.contains("findInModel("), "Should have findInModel helper");
        assertTrue(code.contains("allSourceObjects("), "Should have allSourceObjects helper");
        assertTrue(code.contains("mapRoleBasedTypesIncremental("), "Should have incremental role-based method");

        // Incremental detection
        assertTrue(code.contains("corrIndex.containsKey("), "Should check corrIndex for existing entries");
        assertTrue(code.contains("ctx.getDeletionPolicy()"), "Should use DeletionPolicy");
        assertTrue(code.contains("TOMBSTONE"), "Should handle TOMBSTONE policy");
        assertTrue(code.contains("CorrespondenceModel.markOrphaned("), "Should mark orphaned entries");
    }

    /**
     * Test 2+3 (IMPROVEMENT_01): Szenario B — containment role change detected in
     * mapRoleBasedTypesIncremental. When a FamilyMember moves between roles
     * (e.g. father → sons) without a type change, the corrEntry sourceContainmentRole
     * is updated but the target object is NOT replaced.
     */
    @Test
    void testGeneratedCodeContainsScenarioBDetectionIncremental() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Szenario B detection: same target type, check containment role
        assertTrue(code.contains("// Same target type: check for containment role change (Szenario B)"),
            "Should have Szenario B comment in mapRoleBasedTypesIncremental");

        // Reads stored containment role from corrEntry
        assertTrue(code.contains("CorrespondenceModel.getSourceContainmentRole(entryOpt.get())"),
            "Should read stored sourceContainmentRole from corrEntry");

        // Updates stored containment role on role change
        assertTrue(code.contains("CorrespondenceModel.updateSourceContainmentRole(entryOpt.get(), _currentSrcRole)"),
            "Should update sourceContainmentRole in corrEntry on role change");

        // containmentRole computed from eContainmentFeature
        assertTrue(code.contains("eContainmentFeature() != null ? member.eContainmentFeature().getName() : \"\""),
            "Should read current containment role via eContainmentFeature().getName()");
    }

    /**
     * Test 4 (IMPROVEMENT_01): Szenario A (type change) is handled by existing Phase 2
     * null-reference detection — NOT by handleContainmentRoleSwitch. The generated code
     * should use findDeletedTargetEntries in the backward incremental path.
     */
    @Test
    void testGeneratedCodeSzenarioAHandledByPhase2() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Phase 2 backward: scan for null target objects (Szenario A)
        assertTrue(code.contains("CorrespondenceModel.findDeletedTargetEntries(corrResource)"),
            "transformIncrementalBack should scan for entries with null targetObject (Szenario A)");

        // Szenario B backward: target containment role tracking
        assertTrue(code.contains("// Check target containment role (Szenario B backward"),
            "Should have backward Szenario B comment");
        assertTrue(code.contains("CorrespondenceModel.getTargetContainmentRole(entryOpt.get())"),
            "Should read stored targetContainmentRole in backward path");
        assertTrue(code.contains("CorrespondenceModel.updateTargetContainmentRole(entryOpt.get(), _currentTgtRole)"),
            "Should update targetContainmentRole in corrEntry");
    }

    /**
     * Regression: Batch path stores containment roles in corrEntry (8-arg addEntry).
     */
    @Test
    void testGeneratedCodeBatchStoresContainmentRoles() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // 8-arg addEntry is called with containment roles
        assertTrue(code.contains("eContainmentFeature() != null ?"),
            "Should compute containment roles via eContainmentFeature");

        // Batch path calls addEntry with all 8 args (containmentRole params)
        assertTrue(code.contains("CorrespondenceModel.addEntry(corrResource"),
            "Should call 8-arg addEntry in batch path");
    }

    /**
     * Test SYNCH_01: generated code contains sync() method with both overloads.
     */
    @Test
    void testGeneratedCodeContainsSyncMethod() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Both overloads
        assertTrue(code.contains("public static SyncResult sync(Resource source, Resource target, Resource corrModel)"),
            "Should have 3-arg sync() convenience overload");
        assertTrue(code.contains("SyncConflictPolicy conflictPolicy"),
            "Should have full sync() overload with conflictPolicy");
        assertTrue(code.contains("DeletionPolicy deletionPolicy"),
            "Should have full sync() overload with deletionPolicy");

        // Imports
        assertTrue(code.contains("import dev.bxagent.correspondence.SyncConflictPolicy"),
            "Should import SyncConflictPolicy");
        assertTrue(code.contains("import dev.bxagent.correspondence.SyncResult"),
            "Should import SyncResult");
        assertTrue(code.contains("import dev.bxagent.correspondence.SyncConflict"),
            "Should import SyncConflict");
    }

    /**
     * Test SYNCH_01: Partition 1 conflict detection (Fall A/B/C).
     */
    @Test
    void testGeneratedCodeContainsSyncConflictDetection() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Fingerprint comparison for both sides
        assertTrue(code.contains("computeFingerprint(_srcObj)"),
            "Should compute current source fingerprint in sync");
        assertTrue(code.contains("computeFingerprintBack(_tgtObj)"),
            "Should compute current target fingerprint in sync");

        // Fall A/B/C switch
        assertTrue(code.contains("case SOURCE_WINS"),
            "Should handle SOURCE_WINS conflict policy");
        assertTrue(code.contains("case TARGET_WINS"),
            "Should handle TARGET_WINS conflict policy");
        assertTrue(code.contains("case LOG_AND_SKIP"),
            "Should handle LOG_AND_SKIP conflict policy");

        // SyncConflict creation
        assertTrue(code.contains("new SyncConflict("),
            "Should create SyncConflict records for LOG_AND_SKIP");

        // SyncResult returned
        assertTrue(code.contains("return new SyncResult("),
            "Should return SyncResult");
    }

    /**
     * Test SYNCH_01: Partition 4 (both deleted) removes corrEntry.
     */
    @Test
    void testGeneratedCodeContainsSyncPartition4() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Partition 4: both null → remove
        assertTrue(code.contains("CorrespondenceModel.removeCorrespondenceEntry(corrModel, _ce)"),
            "Should remove corrEntry when both src and tgt are null (Partition 4)");

        // getAllEntries snapshot
        assertTrue(code.contains("CorrespondenceModel.getAllEntries(corrModel)"),
            "Should use getAllEntries snapshot for safe iteration");
    }

    /**
     * Test SYNCH_01: role-based types delegated to incremental methods in sync.
     */
    @Test
    void testGeneratedCodeSyncDelegatesToRoleBasedMethods() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Incremental role-based methods called in sync
        assertTrue(code.contains("mapRoleBasedTypesIncremental("),
            "sync() should delegate to mapRoleBasedTypesIncremental");
        assertTrue(code.contains("mapRoleBasedTypesIncrementalBack("),
            "sync() should delegate to mapRoleBasedTypesIncrementalBack");

        // Javadoc with known limitation comment
        assertTrue(code.contains("KNOWN EDGE-CASE"),
            "Should contain Javadoc warning about delete-recreate edge case");
    }

    @Test
    void testGeneratedCodeClassName() {
        MappingModel.TransformationSpec spec = buildF2PSpec();
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        // Class name derived: capitalize("Families") + "2" + capitalize("Persons") + "Transformation"
        assertEquals("Families2PersonsTransformation.java", file.fileName());
        assertTrue(file.content().contains("public class Families2PersonsTransformation"));
    }
}
