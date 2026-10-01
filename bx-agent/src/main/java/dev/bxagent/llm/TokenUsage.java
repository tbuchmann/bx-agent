package dev.bxagent.llm;

public record TokenUsage(int inputTokens, int outputTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0);

    public int total() {
        return inputTokens + outputTokens;
    }

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens);
    }
}
