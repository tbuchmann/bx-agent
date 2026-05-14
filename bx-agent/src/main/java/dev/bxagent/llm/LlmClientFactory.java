package dev.bxagent.llm;

/**
 * Factory for creating LLM client instances based on configuration.
 */
public class LlmClientFactory {

    /**
     * Creates an LLM client based on the provider specified in the config.
     *
     * @param config LLM configuration
     * @return LlmClient implementation
     * @throws IllegalArgumentException if provider is unknown
     */
    public static LlmClient create(LlmConfig config) {
        String provider = config.getProvider().toLowerCase();

        return switch (provider) {
            case "ollama" -> new OllamaClient(config);
            case "anthropic" -> new AnthropicClient(config);
            case "openai" -> new OpenAiClient(config);
            default -> throw new IllegalArgumentException(
                "Unknown LLM provider: " + provider + ". Supported: ollama, anthropic, openai"
            );
        };
    }

    /**
     * Creates an LLM client using default configuration.
     */
    public static LlmClient createDefault() {
        return create(new LlmConfig());
    }
}
