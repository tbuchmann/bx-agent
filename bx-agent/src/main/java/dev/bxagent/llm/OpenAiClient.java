package dev.bxagent.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.List;

/**
 * LLM client implementation for OpenAI using Langchain4j.
 */
public class OpenAiClient implements LlmClient {

    private final ChatModel model;
    private final String modelName;
    private TokenUsage lastUsage        = TokenUsage.ZERO;
    private TokenUsage accumulatedUsage = TokenUsage.ZERO;

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
            ChatResponse response = model.chat(
                List.of(SystemMessage.from(systemPrompt), UserMessage.from(userMessage))
            );
            var tu = response.tokenUsage();
            int in  = (tu != null && tu.inputTokenCount()  != null) ? tu.inputTokenCount()  : 0;
            int out = (tu != null && tu.outputTokenCount() != null) ? tu.outputTokenCount() : 0;
            lastUsage        = new TokenUsage(in, out);
            accumulatedUsage = accumulatedUsage.add(lastUsage);
            System.out.printf("[tokens] %s  input=%d  output=%d  total=%d  (session: %d)%n",
                modelName, in, out, in + out, accumulatedUsage.total());
            return response.aiMessage().text();
        } catch (Exception e) {
            throw new RuntimeException("OpenAI request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() { return "openai"; }

    @Override
    public String getModelName() { return modelName; }

    @Override
    public TokenUsage getLastTokenUsage() { return lastUsage; }

    @Override
    public TokenUsage getAccumulatedTokenUsage() { return accumulatedUsage; }

    @Override
    public void resetAccumulatedTokenUsage() { accumulatedUsage = TokenUsage.ZERO; }
}
