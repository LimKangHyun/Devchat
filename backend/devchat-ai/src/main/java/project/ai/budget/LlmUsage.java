package project.ai.budget;

/** Gemini 응답의 usageMetadata. 응답에 없으면 0 */
public record LlmUsage(long promptTokens, long outputTokens) {

    public static final LlmUsage NONE = new LlmUsage(0, 0);

    public long totalTokens() {
        return promptTokens + outputTokens;
    }
}
