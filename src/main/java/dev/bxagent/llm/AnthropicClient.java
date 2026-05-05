package dev.bxagent.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

import java.time.Duration;

/**
 * LLM client implementation for Anthropic Claude using Langchain4j.
 */
public class AnthropicClient implements LlmClient {

    private final ChatModel model;
    private final String modelName;

    public AnthropicClient(LlmConfig config) {
        this.modelName = config.getModel();

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                "Anthropic API key required. Set llm.api_key in config or EMT_LLM_API_KEY env var."
            );
        }

        this.model = AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeout(Duration.ofSeconds(config.getTimeout()))
            .build();
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        try {
            // Anthropic supports system prompts natively via messages API
            // For now, we combine them similar to Ollama
            String fullPrompt = systemPrompt + "\n\n" + userMessage;
            return model.chat(fullPrompt);
        } catch (Exception e) {
            throw new RuntimeException("Anthropic request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
