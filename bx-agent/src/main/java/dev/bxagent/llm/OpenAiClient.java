package dev.bxagent.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

/**
 * LLM client implementation for OpenAI using Langchain4j.
 */
public class OpenAiClient implements LlmClient {

    private final ChatModel model;
    private final String modelName;

    public OpenAiClient(LlmConfig config) {
        this.modelName = config.getModel();

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                "OpenAI API key required. Set llm.api_key in config or EMT_LLM_API_KEY env var."
            );
        }

        this.model = OpenAiChatModel.builder()
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
            // Combine system prompt and user message
            String fullPrompt = systemPrompt + "\n\n" + userMessage;
            return model.chat(fullPrompt);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
