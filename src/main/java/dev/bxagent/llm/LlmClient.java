package dev.bxagent.llm;

/**
 * Abstraction for LLM client implementations.
 * Provides a simple interface for completion requests.
 */
public interface LlmClient {

    /**
     * Sends a system prompt and user message, returns the LLM's response.
     *
     * @param systemPrompt The system-level instruction for the LLM
     * @param userMessage  The user's message/query
     * @return The LLM's response as a string
     * @throws RuntimeException if the request fails
     */
    String complete(String systemPrompt, String userMessage);

    /**
     * Returns the name of the LLM provider (e.g., "ollama", "anthropic", "openai").
     */
    String getProviderName();

    /**
     * Returns the model name being used (e.g., "codellama", "claude-opus-4-6").
     */
    String getModelName();
}
