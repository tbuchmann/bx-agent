package dev.bxagent.validation;

import dev.bxagent.codegen.GeneratedFile;
import dev.bxagent.llm.LlmClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates generated Java code by compiling it with javac.
 * If compilation fails, attempts to fix the code using LLM (max 3 attempts).
 */
public class CompilationValidator {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final LlmClient llmClient;

    /**
     * Constructor with LLM client for code fixing.
     */
    public CompilationValidator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Validates generated code by compiling it.
     * If compilation fails, attempts to fix the code using LLM.
     *
     * @param generatedFile The generated file to validate
     * @return ValidationResult with success status and any error messages
     */
    public ValidationResult validate(GeneratedFile generatedFile) {
        Path tempDir = null;
        try {
            // Create temporary directory for compilation
            tempDir = Files.createTempDirectory("emt-agent-compile-");
            System.out.println("[CompilationValidator] Using temp directory: " + tempDir);

            String currentCode = generatedFile.content();
            List<String> attemptErrors = new ArrayList<>();

            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                System.out.println("[CompilationValidator] Compilation attempt " + attempt + "/" + MAX_RETRY_ATTEMPTS);

                // Write code to temp file
                Path javaFile = tempDir.resolve(generatedFile.fileName());
                Files.writeString(javaFile, currentCode);

                // Compile
                CompilationResult result = compile(javaFile);

                if (result.success()) {
                    System.out.println("[CompilationValidator] ✓ Compilation successful!");
                    return new ValidationResult(true, currentCode, attemptErrors);
                }

                System.err.println("[CompilationValidator] ✗ Compilation failed:");
                System.err.println(result.errorOutput());

                attemptErrors.add("Attempt " + attempt + ": " + result.errorOutput());

                // If we have more attempts, try to fix with LLM
                if (attempt < MAX_RETRY_ATTEMPTS && llmClient != null) {
                    System.out.println("[CompilationValidator] Requesting LLM to fix code...");
                    currentCode = requestCodeFix(currentCode, result.errorOutput());
                } else if (llmClient == null) {
                    System.err.println("[CompilationValidator] No LLM client available for code fixing");
                    break;
                }
            }

            // All attempts failed
            System.err.println("[CompilationValidator] Failed after " + MAX_RETRY_ATTEMPTS + " attempts");
            return new ValidationResult(false, currentCode, attemptErrors);

        } catch (IOException e) {
            throw new RuntimeException("Validation failed with IO error", e);
        } finally {
            // Clean up temp directory
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir);
                } catch (IOException e) {
                    System.err.println("[CompilationValidator] Failed to delete temp directory: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Compiles a Java file using javac.
     */
    private CompilationResult compile(Path javaFile) {
        try {
            // Get current classpath (includes EMF JARs)
            String classpath = System.getProperty("java.class.path");

            // Build javac command
            List<String> command = new ArrayList<>();
            command.add("javac");
            command.add("-cp");
            command.add(classpath);
            command.add("-d");
            command.add(javaFile.getParent().toString());
            command.add(javaFile.toString());

            // Execute javac
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            return new CompilationResult(exitCode == 0, output);

        } catch (IOException | InterruptedException e) {
            return new CompilationResult(false, "Failed to execute javac: " + e.getMessage());
        }
    }

    /**
     * Requests LLM to fix the code based on compiler errors.
     */
    private String requestCodeFix(String failedCode, String compilerError) {
        String systemPrompt = """
            You are a Java code fixing expert.
            Your task is to fix Java compilation errors.
            Respond with ONLY the corrected, complete Java class.
            Do not include explanations, markdown, or code fences.
            """;

        String userMessage = String.format("""
            The following Java code failed to compile:

            %s

            Compiler error:
            %s

            Fix the code and return the complete, corrected Java class.
            """, failedCode, compilerError);

        try {
            return llmClient.complete(systemPrompt, userMessage);
        } catch (Exception e) {
            System.err.println("[CompilationValidator] LLM fix request failed: " + e.getMessage());
            return failedCode; // Return original code if LLM fails
        }
    }

    /**
     * Recursively deletes a directory and its contents.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> -a.compareTo(b)) // Reverse order for deletion
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
        }
    }

    /**
     * Result of a compilation attempt.
     */
    private record CompilationResult(boolean success, String errorOutput) {}

    /**
     * Result of the validation process.
     */
    public record ValidationResult(
        boolean success,
        String finalCode,
        List<String> attemptErrors
    ) {
        public boolean hasErrors() {
            return !attemptErrors.isEmpty();
        }

        public String getErrorSummary() {
            return String.join("\n\n", attemptErrors);
        }
    }
}
