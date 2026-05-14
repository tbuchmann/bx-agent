package dev.bxagent.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration loader for LLM clients.
 * Reads from properties file or environment variables.
 */
public class LlmConfig {

    private static final String DEFAULT_CONFIG_PATH = System.getProperty("user.home")
        + "/.bxagent/config.properties";
    private static final String ENV_API_KEY = "EMT_LLM_API_KEY";

    private final Properties properties;

    /**
     * Loads configuration from default location or provided path.
     */
    public LlmConfig(String configPath) {
        this.properties = new Properties();

        Path path = configPath != null
            ? Paths.get(configPath)
            : Paths.get(DEFAULT_CONFIG_PATH);

        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                properties.load(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config from: " + path, e);
            }
        }

        // Also check for local config.properties in project root
        Path localConfig = Paths.get("config/agent.properties");
        if (Files.exists(localConfig)) {
            try (InputStream is = Files.newInputStream(localConfig)) {
                properties.load(is);
            } catch (IOException e) {
                // Ignore, use defaults
            }
        }
    }

    /**
     * Default constructor using standard config location.
     */
    public LlmConfig() {
        this(null);
    }

    public String getProvider() {
        return properties.getProperty("llm.provider", "ollama");
    }

    public String getModel() {
        return properties.getProperty("llm.model", "codellama");
    }

    public String getApiKey() {
        // Check environment variable first
        String envKey = System.getenv(ENV_API_KEY);
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }
        return properties.getProperty("llm.api_key", "");
    }

    public String getBaseUrl() {
        String provider = getProvider();
        String defaultUrl = switch (provider) {
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "openai" -> "https://api.openai.com/v1";
            case "ollama" -> "http://localhost:11434";
            default -> "http://localhost:11434";
        };
        return properties.getProperty("llm.base_url", defaultUrl);
    }

    public double getTemperature() {
        return Double.parseDouble(properties.getProperty("llm.temperature", "0.2"));
    }

    public int getMaxTokens() {
        return Integer.parseInt(properties.getProperty("llm.max_tokens", "4096"));
    }

    public int getTimeout() {
        return Integer.parseInt(properties.getProperty("llm.timeout", "60"));
    }

    /**
     * For testing: create config from properties object.
     * Does NOT load from files - only uses provided properties.
     */
    public static LlmConfig fromProperties(Properties props) {
        LlmConfig config = new LlmConfig();
        // Clear any loaded properties and use only the provided ones
        config.properties.clear();
        config.properties.putAll(props);
        return config;
    }
}
