package dev.bxagent.mapping;

import dev.bxagent.llm.LlmClient;
import dev.bxagent.metamodel.MetamodelSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MappingExtractor.
 */
class MappingExtractorTest {

    /**
     * Mock LLM client that returns a predefined JSON response.
     */
    private static class MockLlmClient implements LlmClient {
        private final String response;
        private int callCount = 0;

        public MockLlmClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userMessage) {
            callCount++;
            return response;
        }

        @Override
        public String getProviderName() {
            return "mock";
        }

        @Override
        public String getModelName() {
            return "mock-model";
        }

        public int getCallCount() {
            return callCount;
        }
    }

    @Test
    void testExtractSimpleMapping() {
        String mockResponse = """
            {
              "sourcePackageName": "pdb1",
              "targetPackageName": "pdb2",
              "generatedClassName": "PersonDB1ToPersonDB2Transformation",
              "typeMappings": [
                { "sourceType": "Person", "targetType": "Person" }
              ],
              "attributeMappings": [
                {
                  "sourceOwnerType": "Person",
                  "sourceAttr": "firstName",
                  "sourceAttrType": "EString",
                  "targetOwnerType": "Person",
                  "targetAttr": "name",
                  "targetAttrType": "EString",
                  "forwardExpression": "source.getFirstName() + ' ' + source.getLastName()",
                  "backwardExpression": null
                }
              ],
              "referenceMappings": [],
              "unresolvedMappings": ["Backward mapping for name -> firstName/lastName is ambiguous"]
            }
            """;

        MockLlmClient mockClient = new MockLlmClient(mockResponse);
        MappingExtractor extractor = new MappingExtractor(mockClient);

        // Create dummy metamodel summaries
        MetamodelSummary.Summary sourceMM = new MetamodelSummary.Summary(
            "pdb1", "http://test", "pdb1", List.of()
        );
        MetamodelSummary.Summary targetMM = new MetamodelSummary.Summary(
            "pdb2", "http://test", "pdb2", List.of()
        );

        MappingModel.TransformationSpec spec = extractor.extract(
            sourceMM, targetMM, "Map Person to Person"
        );

        assertNotNull(spec);
        assertEquals("pdb1", spec.sourcePackageName());
        assertEquals("pdb2", spec.targetPackageName());
        // Class name is derived from package names: capitalize("pdb1") + "2" + capitalize("pdb2") + "Transformation"
        assertEquals("Pdb12Pdb2Transformation", spec.generatedClassName());
        assertEquals(1, spec.typeMappings().size());
        assertEquals(1, spec.attributeMappings().size());
        assertEquals(0, spec.referenceMappings().size());
        assertEquals(1, spec.unresolvedMappings().size());

        // Verify attribute mapping
        MappingModel.AttributeMapping attrMap = spec.attributeMappings().get(0);
        assertEquals("firstName", attrMap.sourceAttr());
        assertEquals("name", attrMap.targetAttr());
        assertNotNull(attrMap.forwardExpression());
        assertNull(attrMap.backwardExpression());

        // Should be called only once
        assertEquals(1, mockClient.getCallCount());
    }

    @Test
    void testExtractWithMarkdownCodeFence() {
        // Test that markdown code fences are properly removed
        String mockResponse = """
            ```json
            {
              "sourcePackageName": "test",
              "targetPackageName": "test",
              "generatedClassName": "TestTransformation",
              "typeMappings": [],
              "attributeMappings": [],
              "referenceMappings": [],
              "unresolvedMappings": []
            }
            ```
            """;

        MockLlmClient mockClient = new MockLlmClient(mockResponse);
        MappingExtractor extractor = new MappingExtractor(mockClient);

        MetamodelSummary.Summary sourceMM = new MetamodelSummary.Summary(
            "test", "http://test", "test", List.of()
        );
        MetamodelSummary.Summary targetMM = new MetamodelSummary.Summary(
            "test", "http://test", "test", List.of()
        );

        MappingModel.TransformationSpec spec = extractor.extract(
            sourceMM, targetMM, "Test"
        );

        assertNotNull(spec);
        // Class name is derived: capitalize("test") + "2" + capitalize("test") + "Transformation"
        assertEquals("Test2TestTransformation", spec.generatedClassName());
    }

    @Test
    void testExtractWithInvalidJsonRetry() {
        // Mock client that returns invalid JSON first, then valid JSON
        class RetryMockClient implements LlmClient {
            private int attempt = 0;

            @Override
            public String complete(String systemPrompt, String userMessage) {
                attempt++;
                if (attempt == 1) {
                    return "This is not valid JSON";
                } else {
                    return """
                        {
                          "sourcePackageName": "test",
                          "targetPackageName": "test",
                          "generatedClassName": "TestTransformation",
                          "typeMappings": [],
                          "attributeMappings": [],
                          "referenceMappings": [],
                          "unresolvedMappings": []
                        }
                        """;
                }
            }

            @Override
            public String getProviderName() { return "mock"; }

            @Override
            public String getModelName() { return "mock"; }
        }

        RetryMockClient mockClient = new RetryMockClient();
        MappingExtractor extractor = new MappingExtractor(mockClient);

        MetamodelSummary.Summary sourceMM = new MetamodelSummary.Summary(
            "test", "http://test", "test", List.of()
        );
        MetamodelSummary.Summary targetMM = new MetamodelSummary.Summary(
            "test", "http://test", "test", List.of()
        );

        MappingModel.TransformationSpec spec = extractor.extract(
            sourceMM, targetMM, "Test"
        );

        assertNotNull(spec);
        assertEquals(2, mockClient.attempt); // Should have retried
    }

    @Test
    void testExtractFailsAfterMaxRetries() {
        // Mock client that always returns invalid JSON
        MockLlmClient mockClient = new MockLlmClient("Not JSON at all!");
        MappingExtractor extractor = new MappingExtractor(mockClient);

        MetamodelSummary.Summary sourceMM = new MetamodelSummary.Summary(
            "test", "http://test", "test", List.of()
        );
        MetamodelSummary.Summary targetMM = new MetamodelSummary.Summary(
            "test", "http://test", "test", List.of()
        );

        assertThrows(RuntimeException.class, () -> {
            extractor.extract(sourceMM, targetMM, "Test");
        });

        // Should have tried 3 times
        assertEquals(3, mockClient.getCallCount());
    }
}
