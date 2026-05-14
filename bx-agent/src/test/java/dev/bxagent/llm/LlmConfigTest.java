package dev.bxagent.llm;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LlmConfig.
 */
class LlmConfigTest {

    @Test
    void testDefaultConfig() {
        LlmConfig config = new LlmConfig();

        // Should not throw, might use defaults or local config
        assertNotNull(config.getProvider());
        assertNotNull(config.getModel());
    }

    @Test
    void testConfigFromProperties() {
        Properties props = new Properties();
        props.setProperty("llm.provider", "ollama");
        props.setProperty("llm.model", "codellama");
        props.setProperty("llm.base_url", "http://localhost:11434");
        props.setProperty("llm.temperature", "0.3");
        props.setProperty("llm.max_tokens", "2048");
        props.setProperty("llm.timeout", "30");

        LlmConfig config = LlmConfig.fromProperties(props);

        assertEquals("ollama", config.getProvider());
        assertEquals("codellama", config.getModel());
        assertEquals("http://localhost:11434", config.getBaseUrl());
        assertEquals(0.3, config.getTemperature(), 0.001);
        assertEquals(2048, config.getMaxTokens());
        assertEquals(30, config.getTimeout());
    }

    @Test
    void testDefaultBaseUrlPerProvider() {
        Properties props = new Properties();

        // Test Ollama default
        props.setProperty("llm.provider", "ollama");
        LlmConfig ollamaConfig = LlmConfig.fromProperties(props);
        assertEquals("http://localhost:11434", ollamaConfig.getBaseUrl());

        // Test Anthropic default
        props.setProperty("llm.provider", "anthropic");
        LlmConfig anthropicConfig = LlmConfig.fromProperties(props);
        assertEquals("https://api.anthropic.com/v1", anthropicConfig.getBaseUrl());

        // Test OpenAI default
        props.setProperty("llm.provider", "openai");
        LlmConfig openaiConfig = LlmConfig.fromProperties(props);
        assertEquals("https://api.openai.com/v1", openaiConfig.getBaseUrl());
    }
}
