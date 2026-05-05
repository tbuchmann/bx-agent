package dev.bxagent.codegen;

/**
 * Represents a generated source file.
 */
public record GeneratedFile(
    String fileName,
    String content
) {
    /**
     * Returns the content for display or writing to disk.
     */
    @Override
    public String toString() {
        return "GeneratedFile{" +
            "fileName='" + fileName + '\'' +
            ", contentLength=" + content.length() +
            '}';
    }
}
