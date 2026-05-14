package dev.bxagent.llm;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LlmClientFactory.
 */
class LlmClientFactoryTest {

    @Test
    void testCreateOllamaClient() {
        Properties props = new Properties();
        props.setProperty("llm.provider", "ollama");
        props.setProperty("llm.model", "codellama");

        LlmConfig config = LlmConfig.fromProperties(props);
        LlmClient client = LlmClientFactory.create(config);

        assertNotNull(client);
        assertInstanceOf(OllamaClient.class, client);
        assertEquals("ollama", client.getProviderName());
        assertEquals("codellama", client.getModelName());
    }

    @Test
    void testCreateAnthropicClientWithoutApiKey() {
        Properties props = new Properties();
        props.setProperty("llm.provider", "anthropic");
        props.setProperty("llm.model", "claude-opus-4-6");
        props.setProperty("llm.api_key", ""); // Explicitly set to empty

        LlmConfig config = LlmConfig.fromProperties(props);

        // Should throw if API key is empty (unless env var EMT_LLM_API_KEY is set)
        // Skip this test if environment variable is set
        if (System.getenv("EMT_LLM_API_KEY") != null && !System.getenv("EMT_LLM_API_KEY").isEmpty()) {
            System.out.println("[Test skipped] EMT_LLM_API_KEY environment variable is set");
            return;
        }

        assertThrows(IllegalArgumentException.class, () -> {
            LlmClientFactory.create(config);
        });
    }

    @Test
    void testCreateAnthropicClientWithApiKey() {
        Properties props = new Properties();
        props.setProperty("llm.provider", "anthropic");
        props.setProperty("llm.model", "claude-opus-4-6");
        props.setProperty("llm.api_key", "sk-ant-test-key");

        LlmConfig config = LlmConfig.fromProperties(props);
        LlmClient client = LlmClientFactory.create(config);

        assertNotNull(client);
        assertInstanceOf(AnthropicClient.class, client);
        assertEquals("anthropic", client.getProviderName());
        assertEquals("claude-opus-4-6", client.getModelName());
    }

    @Test
    void testCreateOpenAiClientWithApiKey() {
        Properties props = new Properties();
        props.setProperty("llm.provider", "openai");
        props.setProperty("llm.model", "gpt-4");
        props.setProperty("llm.api_key", "sk-test-key");

        LlmConfig config = LlmConfig.fromProperties(props);
        LlmClient client = LlmClientFactory.create(config);

        assertNotNull(client);
        assertInstanceOf(OpenAiClient.class, client);
        assertEquals("openai", client.getProviderName());
        assertEquals("gpt-4", client.getModelName());
    }

    @Test
    void testUnknownProvider() {
        Properties props = new Properties();
        props.setProperty("llm.provider", "unknown");

        LlmConfig config = LlmConfig.fromProperties(props);

        assertThrows(IllegalArgumentException.class, () -> {
            LlmClientFactory.create(config);
        });
    }
}
