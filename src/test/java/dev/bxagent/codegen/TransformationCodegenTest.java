package dev.bxagent.codegen;

import dev.bxagent.mapping.MappingModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransformationCodegen.
 */
class TransformationCodegenTest {

    @Test
    void testGenerateSimpleTransformation() {
        // Create a simple transformation spec
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "pdb1",
            "pdb2",
            "PersonDB1ToPersonDB2Transformation",
            List.of(
                new MappingModel.TypeMapping("Person", "Person", List.of(), List.of(), null, List.of())
            ),
            List.of(
                new MappingModel.AttributeMapping(
                    "Person", "firstName", "EString",
                    "Person", "name", "EString",
                    "source.getFirstName() + \" \" + source.getLastName()",
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        assertNotNull(file);
        // Class name derived from package names: capitalize("pdb1") + "2" + capitalize("pdb2") + "Transformation"
        assertEquals("Pdb12Pdb2Transformation.java", file.fileName());

        String code = file.content();
        assertNotNull(code);

        // Check package declaration
        assertTrue(code.contains("package dev.bxagent.generated;"));

        // Check class name
        assertTrue(code.contains("public class Pdb12Pdb2Transformation"));

        // Check imports
        assertTrue(code.contains("import pdb1.*;"));
        assertTrue(code.contains("import pdb2.*;"));

        // Check forward transformation method (deprecated, qualified with package names)
        assertTrue(code.contains("public static pdb2.Person transform(pdb1.Person source)"));

        // Check backward transformation method (deprecated, qualified with package names)
        assertTrue(code.contains("public static pdb1.Person transformBack(pdb2.Person target)"));

        // Check factory usage
        assertTrue(code.contains("Pdb2Factory.eINSTANCE.createPerson()"));
        assertTrue(code.contains("Pdb1Factory.eINSTANCE.createPerson()"));

        // Check attribute mapping
        assertTrue(code.contains("target.setName(source.getFirstName() + \" \" + source.getLastName())"));

        // Check null handling
        assertTrue(code.contains("if (source == null)"));
    }

    @Test
    void testGenerateWithReferences() {
        // Create spec with reference mappings
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source",
            "target",
            "TestTransformation",
            List.of(
                new MappingModel.TypeMapping("Container", "Container", List.of(), List.of(), null, List.of())
            ),
            List.of(),
            List.of(
                new MappingModel.ReferenceMapping(
                    "Container", "items", "Item",
                    "Container", "elements", "Element",
                    true, true,   // both are many
                    true, true,   // both are containment
                    false, false  // neither is eOpposite
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();

        // Check containment traversal in createAndMapObjects (forward) — uses reflective API for cardinality
        assertTrue(code.contains("getEStructuralFeature(\"items\")"), "Should access 'items' feature reflectively");
        assertTrue(code.contains("_rawChild instanceof source.Item child"), "Should check instanceof source.Item");
        assertTrue(code.contains("_tgtList.add(transformedChild)"), "Should add to target list");

        // Check containment traversal in createAndMapObjectsBack (backward)
        assertTrue(code.contains("getEStructuralFeature(\"elements\")"), "Should access 'elements' feature reflectively");
        assertTrue(code.contains("_rawChild instanceof target.Element child"), "Should check instanceof target.Element");
        assertTrue(code.contains("_srcList.add(transformedChild)"), "Should add to source list");
    }

    @Test
    void testGenerateWithBackwardExpressions() {
        // Create spec with backward expressions
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source",
            "target",
            "TestTransformation",
            List.of(
                new MappingModel.TypeMapping("Person", "Employee", List.of(), List.of(), null, List.of())
            ),
            List.of(
                new MappingModel.AttributeMapping(
                    "Person", "name", "EString",
                    "Employee", "fullName", "EString",
                    "source.getName()",
                    "target.getFullName()"
                ),
                new MappingModel.AttributeMapping(
                    "Person", "age", "EInt",
                    "Employee", "birthYear", "EInt",
                    "2025 - source.getAge()",
                    null  // No backward expression
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();

        // Check forward mappings
        assertTrue(code.contains("target.setFullName(source.getName())"));
        assertTrue(code.contains("target.setBirthYear(2025 - source.getAge())"));

        // Check backward mapping with expression
        assertTrue(code.contains("source.setName(target.getFullName())"));

        // Check skipped backward mapping
        assertTrue(code.contains("// birthYear → age: no backward mapping (skipped)"));
    }

    @Test
    void testGenerateTest() {
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source",
            "target",
            "TestTransformation",
            List.of(
                new MappingModel.TypeMapping("Person", "Employee", List.of(), List.of(), null, List.of())
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTest(spec);

        assertNotNull(file);
        // Class name derived: capitalize("source") + "2" + capitalize("target") + "Transformation"
        assertEquals("Source2TargetTransformationTest.java", file.fileName());

        String code = file.content();

        // Check test class structure
        assertTrue(code.contains("public class Source2TargetTransformationTest"));
        assertTrue(code.contains("@Disabled(\"Manual test data setup required\")"));
        assertTrue(code.contains("@Test"));
        assertTrue(code.contains("void testForwardTransformation()"));
        assertTrue(code.contains("void testBackwardTransformation()"));
        assertTrue(code.contains("void testRoundtripTransformation()"));
        assertTrue(code.contains("void testNullHandling()"));
    }

    /**
     * Test 1 (IMPROVEMENT_01): Cross-references are resolved incrementally.
     * Verifies that the generated code contains a resolveReferencesIncremental method
     * that uses corrIndex as lookup (not objectMap), iterates allSourceObjects, and
     * clears isMany lists before refilling.
     */
    @Test
    void testGeneratedCodeContainsResolveReferencesIncremental() {
        // Spec with a non-containment cross-reference: Employee.dept → EmployeeOut.dept
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "src",
            "tgt",
            "TestCrossRefTransformation",
            List.of(
                new MappingModel.TypeMapping("Employee", "EmployeeOut", List.of(), List.of(), null, List.of()),
                new MappingModel.TypeMapping("Department", "DeptOut", List.of(), List.of(), null, List.of())
            ),
            List.of(),
            List.of(
                new MappingModel.ReferenceMapping(
                    "Employee", "dept", "Department",
                    "EmployeeOut", "dept", "DeptOut",
                    false, false,   // singular references
                    false, false,   // not containment
                    false, false    // not eOpposite
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // Phase 3 method exists
        assertTrue(code.contains("resolveReferencesIncremental("), "Should have resolveReferencesIncremental method");
        assertTrue(code.contains("resolveReferencesIncrementalBack("), "Should have resolveReferencesIncrementalBack method");

        // Uses allSourceObjects instead of objectMap for iteration
        assertTrue(code.contains("for (EObject srcObj : allSourceObjects(sourceModel))"),
            "resolveReferencesIncremental should iterate allSourceObjects");

        // Uses corrIndex as lookup (not objectMap)
        assertTrue(code.contains("corrIndex.get(srcObj)"),
            "resolveReferencesIncremental should look up via corrIndex");

        // Handles null source reference (sets target to null)
        assertTrue(code.contains("tgtObj.eSet(_tgtFeat, null)"),
            "Should set target ref to null when source ref is null");

        // Phase 3 is called in transformIncremental
        assertTrue(code.contains("resolveReferencesIncremental(sourceModel, existingTarget, corrIndex)"),
            "transformIncremental should call resolveReferencesIncremental");

        // Phase 3 is called in transformIncrementalBack
        assertTrue(code.contains("resolveReferencesIncrementalBack(targetModel, sourceModel, corrIndex)"),
            "transformIncrementalBack should call resolveReferencesIncrementalBack");
    }

    /**
     * Test 1b: For isMany cross-references the generated incremental resolver
     * clears the target list before refilling.
     */
    @Test
    void testGeneratedCodeClearsManyRefsBeforeRefilling() {
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "src",
            "tgt",
            "TestManyRefTransformation",
            List.of(
                new MappingModel.TypeMapping("Employee", "EmployeeOut", List.of(), List.of(), null, List.of()),
                new MappingModel.TypeMapping("Skill", "SkillOut", List.of(), List.of(), null, List.of())
            ),
            List.of(),
            List.of(
                new MappingModel.ReferenceMapping(
                    "Employee", "skills", "Skill",
                    "EmployeeOut", "skills", "SkillOut",
                    true, true,     // isMany
                    false, false,   // not containment
                    false, false    // not eOpposite
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);
        String code = file.content();

        // isMany incremental: clear first, then add
        assertTrue(code.contains("_tgtList.clear()"),
            "resolveReferencesIncremental should clear isMany list before refilling");
        // Backward: clear source list
        assertTrue(code.contains("_srcList.clear()"),
            "resolveReferencesIncrementalBack should clear isMany list before refilling");
    }

    @Test
    void testCapitalization() {
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "mypackage",
            "otherpackage",
            "TestTransformation",
            List.of(
                new MappingModel.TypeMapping("Type", "Type", List.of(), List.of(), null, List.of())
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            java.util.Map.of(),
            null
        );

        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        String code = file.content();

        // Check capitalized factory names
        assertTrue(code.contains("MypackageFactory"));
        assertTrue(code.contains("OtherpackageFactory"));
    }
}
