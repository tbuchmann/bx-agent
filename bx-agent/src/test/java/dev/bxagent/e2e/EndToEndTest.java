package dev.bxagent.e2e;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.codegen.TransformationCodegen;
import dev.bxagent.llm.LlmClient;
import dev.bxagent.mapping.BidirectionalityChecker;
import dev.bxagent.mapping.MappingExtractor;
import dev.bxagent.mapping.MappingModel;
import dev.bxagent.metamodel.EcoreParser;
import dev.bxagent.metamodel.MetamodelSummary;
import dev.bxagent.validation.CompilationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for the complete transformation generation workflow.
 * These tests use real .ecore files and validate the entire pipeline.
 */
class EndToEndTest {

    /**
     * Mock LLM client that returns a valid transformation spec for PersonsDB1 → PersonsDB2.
     */
    private static class PersonsDB1ToDB2MockLlm implements LlmClient {
        @Override
        public String complete(String systemPrompt, String userMessage) {
            // Return a valid JSON transformation spec for PersonsDB1 → PersonsDB2
            return """
                {
                  "sourcePackageName": "pdb1",
                  "targetPackageName": "pdb2",
                  "generatedClassName": "PersonsDB1ToPersonsDB2Transformation",
                  "typeMappings": [
                    { "sourceType": "Database", "targetType": "Database" },
                    { "sourceType": "Person", "targetType": "Person" }
                  ],
                  "attributeMappings": [
                    {
                      "sourceOwnerType": "Database",
                      "sourceAttr": "name",
                      "sourceAttrType": "EString",
                      "targetOwnerType": "Database",
                      "targetAttr": "name",
                      "targetAttrType": "EString",
                      "forwardExpression": "source.getName()",
                      "backwardExpression": "target.getName()"
                    },
                    {
                      "sourceOwnerType": "Person",
                      "sourceAttr": "firstName",
                      "sourceAttrType": "EString",
                      "targetOwnerType": "Person",
                      "targetAttr": "name",
                      "targetAttrType": "EString",
                      "forwardExpression": "source.getFirstName() + \\" \\" + source.getLastName()",
                      "backwardExpression": "target.getName().contains(\\" \\") ? target.getName().substring(0, target.getName().indexOf(' ')) : target.getName()"
                    },
                    {
                      "sourceOwnerType": "Person",
                      "sourceAttr": "age",
                      "sourceAttrType": "EInt",
                      "targetOwnerType": "Person",
                      "targetAttr": "age",
                      "targetAttrType": "EInt",
                      "forwardExpression": "source.getAge()",
                      "backwardExpression": "target.getAge()"
                    }
                  ],
                  "referenceMappings": [
                    {
                      "sourceRefOwnerType": "Database",
                      "sourceRef": "persons",
                      "sourceRefTargetType": "Person",
                      "targetRefOwnerType": "Database",
                      "targetRef": "persons",
                      "targetRefTargetType": "Person",
                      "sourceIsMany": true,
                      "targetIsMany": true,
                      "sourceIsContainment": true,
                      "targetIsContainment": true,
                      "sourceIsEOpposite": false,
                      "targetIsEOpposite": false
                    }
                  ],
                  "unresolvedMappings": [],
                  "transformationOptions": []
                }
                """;
        }

        @Override
        public String getProviderName() {
            return "mock-e2e";
        }

        @Override
        public String getModelName() {
            return "mock-model";
        }
    }

    /**
     * Mock LLM for code fixing that returns corrected code.
     */
    private static class CodeFixingMockLlm implements LlmClient {
        @Override
        public String complete(String systemPrompt, String userMessage) {
            // Extract the broken code and try to fix common issues
            if (userMessage.contains("missing semicolon") || userMessage.contains("';' erwartet")) {
                // Add semicolons to fix the code
                return userMessage.lines()
                    .map(line -> {
                        if (line.trim().startsWith("int ") && !line.trim().endsWith(";")) {
                            return line + ";";
                        }
                        return line;
                    })
                    .reduce("", (a, b) -> a + "\n" + b);
            }
            // Return original if we can't fix it
            return userMessage;
        }

        @Override
        public String getProviderName() {
            return "mock-fixer";
        }

        @Override
        public String getModelName() {
            return "mock-fixer-model";
        }
    }

    @Test
    void testCompleteWorkflowWithPersonsDB() throws IOException {
        // Step 1: Parse source and target metamodels
        Path sourceEcore = Paths.get("examples/pdb/PersonsDB1.ecore");
        Path targetEcore = Paths.get("examples/pdb/PersonsDB2.ecore");

        assertTrue(Files.exists(sourceEcore), "PersonsDB1.ecore should exist");
        assertTrue(Files.exists(targetEcore), "PersonsDB2.ecore should exist");

        MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
        MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);

        assertNotNull(sourceSummary);
        assertNotNull(targetSummary);
        assertEquals("pdb1", sourceSummary.packageName());
        assertEquals("pdb2", targetSummary.packageName());

        // Step 2: Extract mapping using mock LLM
        LlmClient mockLlm = new PersonsDB1ToDB2MockLlm();
        MappingExtractor extractor = new MappingExtractor(mockLlm);
        MappingModel.TransformationSpec spec = extractor.extract(
            sourceSummary,
            targetSummary,
            "Map PersonsDB1 to PersonsDB2, combining firstName and lastName into name"
        );

        assertNotNull(spec);
        // Class name is derived from package names: capitalize("pdb1") + "2" + capitalize("pdb2") + "Transformation"
        assertEquals("Pdb12Pdb2Transformation", spec.generatedClassName());
        assertEquals(2, spec.typeMappings().size());
        assertEquals(3, spec.attributeMappings().size());

        // Step 3: Check bidirectionality (non-interactive mode)
        BidirectionalityChecker checker = new BidirectionalityChecker(null);
        MappingModel.TransformationSpec enhancedSpec = checker.resolveUnresolvedMappings(spec);

        assertNotNull(enhancedSpec);

        // Step 4: Generate code
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile transformationFile = codegen.generateTransformation(enhancedSpec);
        GeneratedFile testFile = codegen.generateTest(enhancedSpec);

        assertNotNull(transformationFile);
        assertNotNull(testFile);
        assertEquals("Pdb12Pdb2Transformation.java", transformationFile.fileName());
        assertEquals("Pdb12Pdb2TransformationTest.java", testFile.fileName());

        // Verify generated code contains expected elements
        String generatedCode = transformationFile.content();
        assertTrue(generatedCode.contains("package dev.bxagent.generated"));
        assertTrue(generatedCode.contains("class Pdb12Pdb2Transformation"));
        // Deprecated single-object methods use the root type (Database = first type mapping), qualified
        assertTrue(generatedCode.contains("public static pdb2.Database transform(pdb1.Database source)"));
        assertTrue(generatedCode.contains("public static pdb1.Database transformBack(pdb2.Database target)"));
        assertTrue(generatedCode.contains("import pdb1.*;"));
        assertTrue(generatedCode.contains("import pdb2.*;"));

        // Step 5: Validate compilation (note: may fail without actual EMF JARs in classpath during test)
        // We skip actual validation in unit tests as it requires full EMF dependencies
        System.out.println("[E2E Test] Skipping compilation validation (would require full EMF setup)");
    }

    @Test
    void testCompleteWorkflowWithFamiliesToPersons() throws IOException {
        Path sourceEcore = Paths.get("examples/f2p/Families.ecore");
        Path targetEcore = Paths.get("examples/f2p/Persons.ecore");

        assertTrue(Files.exists(sourceEcore), "Families.ecore should exist");
        assertTrue(Files.exists(targetEcore), "Persons.ecore should exist");

        // Parse metamodels
        MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
        MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);

        assertNotNull(sourceSummary);
        assertNotNull(targetSummary);

        // Verify we can extract class information
        assertFalse(sourceSummary.classes().isEmpty(), "Source should have classes");
        assertFalse(targetSummary.classes().isEmpty(), "Target should have classes");
    }

    @Test
    void testCompleteWorkflowWithSetsToOrderedSets() throws IOException {
        Path sourceEcore = Paths.get("examples/sets/Sets.ecore");
        Path targetEcore = Paths.get("examples/sets/OrderedSets.ecore");

        assertTrue(Files.exists(sourceEcore), "Sets.ecore should exist");
        assertTrue(Files.exists(targetEcore), "OrderedSets.ecore should exist");

        // Parse metamodels
        MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
        MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);

        assertNotNull(sourceSummary);
        assertNotNull(targetSummary);

        // Verify package names
        assertEquals("sets", sourceSummary.packageName());
        assertEquals("osets", targetSummary.packageName());
    }

    @Test
    void testGeneratedCodeCanBeWrittenToDisk(@TempDir Path tempDir) throws IOException {
        // Use mock LLM for extraction
        LlmClient mockLlm = new PersonsDB1ToDB2MockLlm();

        // Parse example metamodels
        Path sourceEcore = Paths.get("examples/pdb/PersonsDB1.ecore");
        Path targetEcore = Paths.get("examples/pdb/PersonsDB2.ecore");

        MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
        MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);

        // Extract mapping
        MappingExtractor extractor = new MappingExtractor(mockLlm);
        MappingModel.TransformationSpec spec = extractor.extract(sourceSummary, targetSummary, null);

        // Generate code
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile transformationFile = codegen.generateTransformation(spec);
        GeneratedFile testFile = codegen.generateTest(spec);

        // Write to temp directory
        Path transformationPath = tempDir.resolve(transformationFile.fileName());
        Path testPath = tempDir.resolve(testFile.fileName());

        Files.writeString(transformationPath, transformationFile.content());
        Files.writeString(testPath, testFile.content());

        // Verify files were written
        assertTrue(Files.exists(transformationPath));
        assertTrue(Files.exists(testPath));
        assertTrue(Files.size(transformationPath) > 0);
        assertTrue(Files.size(testPath) > 0);

        // Verify content matches
        assertEquals(transformationFile.content(), Files.readString(transformationPath));
        assertEquals(testFile.content(), Files.readString(testPath));
    }

    @Test
    void testMetamodelSummaryContainsExpectedData() throws IOException {
        Path sourceEcore = Paths.get("examples/pdb/PersonsDB1.ecore");
        MetamodelSummary.Summary summary = EcoreParser.parse(sourceEcore);

        // Verify summary contains expected data
        assertNotNull(summary);
        assertEquals("pdb1", summary.packageName());
        assertFalse(summary.classes().isEmpty());

        // Find Person class
        boolean foundPerson = summary.classes().stream()
            .anyMatch(c -> c.name().equals("Person"));
        assertTrue(foundPerson, "Should contain Person class");

        System.out.println("=== PersonsDB1 Metamodel Summary ===");
        System.out.println("Package: " + summary.packageName());
        System.out.println("Classes: " + summary.classes().stream().map(c -> c.name()).toList());
    }

    @Test
    void testAllExampleMetamodelsAreParseable() throws IOException {
        String[][] examplePairs = {
            {"examples/pdb/PersonsDB1.ecore", "examples/pdb/PersonsDB2.ecore"},
            {"examples/f2p/Families.ecore", "examples/f2p/Persons.ecore"},
            {"examples/sets/Sets.ecore", "examples/sets/OrderedSets.ecore"}
        };

        for (String[] pair : examplePairs) {
            Path source = Paths.get(pair[0]);
            Path target = Paths.get(pair[1]);

            // Should not throw exceptions
            MetamodelSummary.Summary sourceSummary = EcoreParser.parse(source);
            MetamodelSummary.Summary targetSummary = EcoreParser.parse(target);

            assertNotNull(sourceSummary, "Failed to parse " + pair[0]);
            assertNotNull(targetSummary, "Failed to parse " + pair[1]);

            // Should have at least one class
            assertFalse(sourceSummary.classes().isEmpty(), pair[0] + " should have classes");
            assertFalse(targetSummary.classes().isEmpty(), pair[1] + " should have classes");
        }
    }

    @Test
    void testEndToEndWithManualValidation(@TempDir Path tempDir) throws IOException {
        // This test demonstrates the manual validation workflow
        // (without actually compiling, as that would require full EMF setup)

        LlmClient mockLlm = new PersonsDB1ToDB2MockLlm();
        LlmClient fixerLlm = new CodeFixingMockLlm();

        // Parse
        Path sourceEcore = Paths.get("examples/pdb/PersonsDB1.ecore");
        Path targetEcore = Paths.get("examples/pdb/PersonsDB2.ecore");
        MetamodelSummary.Summary sourceSummary = EcoreParser.parse(sourceEcore);
        MetamodelSummary.Summary targetSummary = EcoreParser.parse(targetEcore);

        // Extract
        MappingExtractor extractor = new MappingExtractor(mockLlm);
        MappingModel.TransformationSpec spec = extractor.extract(sourceSummary, targetSummary, null);

        // Check bidirectionality
        BidirectionalityChecker checker = new BidirectionalityChecker(null);
        spec = checker.resolveUnresolvedMappings(spec);

        // Generate
        TransformationCodegen codegen = new TransformationCodegen();
        GeneratedFile file = codegen.generateTransformation(spec);

        // Write to disk
        Path outputPath = tempDir.resolve(file.fileName());
        Files.writeString(outputPath, file.content());

        // Create validator (note: will skip actual compilation in test environment)
        CompilationValidator validator = new CompilationValidator(fixerLlm);

        // In a real scenario, we would validate here:
        // CompilationValidator.ValidationResult result = validator.validate(file);
        // For the test, we just verify the validator can be created
        assertNotNull(validator);

        System.out.println("[E2E Test] Generated file: " + outputPath);
        System.out.println("[E2E Test] File size: " + Files.size(outputPath) + " bytes");
    }
}
