package dev.bxagent.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM client implementation for Ollama using Langchain4j.
 */
public class OllamaClient implements LlmClient {

    private final ChatModel model;
    private final String modelName;
    private final String apiKey; // Not used by Ollama, but included for consistency

    public OllamaClient(LlmConfig config) {
        this.modelName = config.getModel();
        this.apiKey = config.getApiKey(); // Ollama doesn't require API key, but we keep it for consistency

        Map<String, String> headers = new HashMap<>();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }

        this.model = OllamaChatModel.builder()
            .baseUrl(config.getBaseUrl())            
            .modelName(modelName)
            .temperature(config.getTemperature())
            .timeout(Duration.ofSeconds(config.getTimeout()))
            .customHeaders(headers)
            .build();
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        try {
            ChatResponse response = model.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMessage)
            );
            return response.aiMessage().text();
        } catch (Exception e) {
            throw new RuntimeException("Ollama request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
