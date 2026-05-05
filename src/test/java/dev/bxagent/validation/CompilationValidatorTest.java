package dev.bxagent.validation;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CompilationValidator.
 */
class CompilationValidatorTest {

    /**
     * Mock LLM client for testing code fixing.
     */
    private static class MockFixingLlmClient implements LlmClient {
        private final String fixedCode;
        private int callCount = 0;

        public MockFixingLlmClient(String fixedCode) {
            this.fixedCode = fixedCode;
        }

        @Override
        public String complete(String systemPrompt, String userMessage) {
            callCount++;
            return fixedCode;
        }

        @Override
        public String getProviderName() {
            return "mock";
        }

        @Override
        public String getModelName() {
            return "mock";
        }

        public int getCallCount() {
            return callCount;
        }
    }

    @Test
    void testValidateSimpleValidCode() {
        // Create a simple valid Java class
        String validCode = """
            package dev.bxagent.generated;

            public class SimpleTest {
                public static String transform(String input) {
                    return input != null ? input.toUpperCase() : null;
                }
            }
            """;

        GeneratedFile file = new GeneratedFile("SimpleTest.java", validCode);
        CompilationValidator validator = new CompilationValidator(null);

        CompilationValidator.ValidationResult result = validator.validate(file);

        assertTrue(result.success(), "Valid code should compile successfully");
        assertEquals(validCode, result.finalCode());
    }

    @Test
    void testValidateInvalidCodeWithoutLlm() {
        // Create invalid Java code (missing semicolon)
        String invalidCode = """
            package dev.bxagent.generated;

            public class InvalidTest {
                public static void test() {
                    int x = 5
                }
            }
            """;

        GeneratedFile file = new GeneratedFile("InvalidTest.java", invalidCode);
        CompilationValidator validator = new CompilationValidator(null);

        CompilationValidator.ValidationResult result = validator.validate(file);

        assertFalse(result.success(), "Invalid code should fail compilation");
        assertTrue(result.hasErrors(), "Should have error messages");
        assertFalse(result.attemptErrors().isEmpty());
    }

    @Test
    void testValidateInvalidCodeWithLlmFix() {
        // Create invalid code
        String invalidCode = """
            package dev.bxagent.generated;

            public class InvalidTest {
                public static void test() {
                    int x = 5
                }
            }
            """;

        // Fixed version that LLM would return
        String fixedCode = """
            package dev.bxagent.generated;

            public class InvalidTest {
                public static void test() {
                    int x = 5;
                }
            }
            """;

        GeneratedFile file = new GeneratedFile("InvalidTest.java", invalidCode);
        MockFixingLlmClient mockLlm = new MockFixingLlmClient(fixedCode);
        CompilationValidator validator = new CompilationValidator(mockLlm);

        CompilationValidator.ValidationResult result = validator.validate(file);

        // Should succeed after LLM fix
        assertTrue(result.success(), "Code should compile after LLM fix");
        assertEquals(1, mockLlm.getCallCount(), "LLM should be called once to fix");
        assertEquals(fixedCode, result.finalCode());
    }

    @Test
    void testValidateWithMultipleRetries() {
        // Create code that LLM cannot fix (always returns invalid code)
        String invalidCode = """
            package dev.bxagent.generated;

            public class PermanentlyBroken {
                public static void test() {
                    this is not valid java
                }
            }
            """;

        MockFixingLlmClient mockLlm = new MockFixingLlmClient(invalidCode);
        CompilationValidator validator = new CompilationValidator(mockLlm);

        GeneratedFile file = new GeneratedFile("PermanentlyBroken.java", invalidCode);
        CompilationValidator.ValidationResult result = validator.validate(file);

        assertFalse(result.success(), "Should fail after max retries");
        // 3 attempts means 2 LLM calls: after attempt 1 and after attempt 2
        // (no LLM call after attempt 3, since we're at max)
        assertEquals(2, mockLlm.getCallCount(), "Should call LLM twice for fixing");
        assertTrue(result.hasErrors());
        assertEquals(3, result.attemptErrors().size(), "Should have 3 error attempts");
    }

    @Test
    void testValidationResultErrorSummary() {
        var result = new CompilationValidator.ValidationResult(
            false,
            "some code",
            java.util.List.of(
                "Attempt 1: error line 5",
                "Attempt 2: error line 5",
                "Attempt 3: error line 5"
            )
        );

        assertTrue(result.hasErrors());
        String summary = result.getErrorSummary();
        assertTrue(summary.contains("Attempt 1"));
        assertTrue(summary.contains("Attempt 2"));
        assertTrue(summary.contains("Attempt 3"));
    }

    @Test
    void testNullHandlingInGeneratedCode() {
        // Test that generated code with null checks compiles
        String codeWithNullChecks = """
            package dev.bxagent.generated;

            public class NullSafeTransformation {
                public static String transform(String source) {
                    if (source == null) {
                        return null;
                    }
                    return source.trim();
                }

                public static String transformBack(String target) {
                    if (target == null) {
                        return null;
                    }
                    return target;
                }
            }
            """;

        GeneratedFile file = new GeneratedFile("NullSafeTransformation.java", codeWithNullChecks);
        CompilationValidator validator = new CompilationValidator(null);

        CompilationValidator.ValidationResult result = validator.validate(file);

        assertTrue(result.success(), "Null-safe code should compile");
    }
}
