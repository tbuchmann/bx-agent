package dev.bxagent.mapping;

import dev.bxagent.cli.InteractivePrompter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BidirectionalityChecker.
 */
class BidirectionalityCheckerTest {

    @Test
    void testNoUnresolvedMappings() {
        // Spec with no unresolved mappings - should return unchanged
        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source", "target", "TestTransformation",
            List.of(),
            List.of(
                new MappingModel.AttributeMapping(
                    "Source", "name", "EString", "Target", "name", "EString",
                    "source.getName()", "target.getName()"
                )
            ),
            List.of(),
            List.of(),  // No unresolved mappings
            List.of(),  // No transformation options
            List.of(),  // No role-based type mappings
            List.of(),  // No backward configs
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

        BidirectionalityChecker checker = new BidirectionalityChecker();
        MappingModel.TransformationSpec result = checker.resolveUnresolvedMappings(spec);

        assertEquals(spec, result);
        assertTrue(result.unresolvedMappings().isEmpty());
    }

    @Test
    void testResolveWithCustomExpression() {
        // Simulate user choosing custom expression
        String input = "1\ntarget.getName().split(\" \")[0]\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));
        BidirectionalityChecker checker = new BidirectionalityChecker(prompter);

        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source", "target", "TestTransformation",
            List.of(),
            List.of(
                new MappingModel.AttributeMapping(
                    "Source", "firstName", "EString", "Target", "name", "EString",
                    "source.getFirstName() + ' ' + source.getLastName()",
                    null  // Unresolved backward mapping
                )
            ),
            List.of(),
            List.of("Cannot split name back into firstName and lastName"),
            List.of(),  // No transformation options
            List.of(),  // No role-based type mappings
            List.of(),  // No backward configs
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

        MappingModel.TransformationSpec result = checker.resolveUnresolvedMappings(spec);

        assertEquals(1, result.attributeMappings().size());
        MappingModel.AttributeMapping resolved = result.attributeMappings().get(0);
        assertNotNull(resolved.backwardExpression());
        assertEquals("target.getName().split(\" \")[0]", resolved.backwardExpression());
        assertTrue(result.unresolvedMappings().isEmpty());
    }

    @Test
    void testResolveWithDefaultValue() {
        // Simulate user choosing default value (option 2, then null)
        String input = "2\n1\n";  // Option 2 = default value, then option 1 = null
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));
        BidirectionalityChecker checker = new BidirectionalityChecker(prompter);

        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source", "target", "TestTransformation",
            List.of(),
            List.of(
                new MappingModel.AttributeMapping(
                    "Source", "computed", "EString", "Target", "value", "EString",
                    "computeSomething(source)",
                    null
                )
            ),
            List.of(),
            List.of("Computed value cannot be reversed"),
            List.of(),  // No transformation options
            List.of(),  // No role-based type mappings
            List.of(),  // No backward configs
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

        MappingModel.TransformationSpec result = checker.resolveUnresolvedMappings(spec);

        MappingModel.AttributeMapping resolved = result.attributeMappings().get(0);
        assertEquals("null", resolved.backwardExpression());
    }

    @Test
    void testResolveWithThrowException() {
        // Simulate user choosing throw exception (option 3)
        String input = "3\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));
        BidirectionalityChecker checker = new BidirectionalityChecker(prompter);

        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source", "target", "TestTransformation",
            List.of(),
            List.of(
                new MappingModel.AttributeMapping(
                    "Source", "age", "EInt", "Target", "birthYear", "EInt",
                    "2025 - source.getAge()",
                    null
                )
            ),
            List.of(),
            List.of("Age to birthYear is lossy"),
            List.of(),  // No transformation options
            List.of(),  // No role-based type mappings
            List.of(),  // No backward configs
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

        MappingModel.TransformationSpec result = checker.resolveUnresolvedMappings(spec);

        MappingModel.AttributeMapping resolved = result.attributeMappings().get(0);
        assertTrue(resolved.backwardExpression().contains("UnsupportedOperationException"));
        assertTrue(resolved.backwardExpression().contains("birthYear"));
    }

    @Test
    void testResolveWithSkip() {
        // Simulate user choosing skip (option 4)
        String input = "4\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractivePrompter prompter = new InteractivePrompter(in, new PrintStream(out));
        BidirectionalityChecker checker = new BidirectionalityChecker(prompter);

        MappingModel.TransformationSpec spec = new MappingModel.TransformationSpec(
            "source", "target", "TestTransformation",
            List.of(),
            List.of(
                new MappingModel.AttributeMapping(
                    "Source", "derived", "EString", "Target", "value", "EString",
                    "deriveSomething(source)",
                    null
                )
            ),
            List.of(),
            List.of("Skip this attribute in backward direction"),
            List.of(),  // No transformation options
            List.of(),  // No role-based type mappings
            List.of(),  // No backward configs
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

        MappingModel.TransformationSpec result = checker.resolveUnresolvedMappings(spec);

        MappingModel.AttributeMapping resolved = result.attributeMappings().get(0);
        assertNull(resolved.backwardExpression());  // Should remain null
    }
}
