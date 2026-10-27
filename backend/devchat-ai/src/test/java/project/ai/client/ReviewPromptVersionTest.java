package project.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewPromptVersionTest {

    private static final String MODEL = "https://example/models/gemini-2.5-flash-lite:generateContent";

    @Test
    void 같은_프롬프트와_모델이면_버전이_같고_8자리다() {
        String v1 = GeminiClient.versionOf(MODEL, "리뷰 규칙 A");
        String v2 = GeminiClient.versionOf(MODEL, "리뷰 규칙 A");

        assertThat(v1).isEqualTo(v2).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    void 프롬프트나_모델이_바뀌면_버전도_바뀐다() {
        String base = GeminiClient.versionOf(MODEL, "리뷰 규칙 A");

        assertThat(GeminiClient.versionOf(MODEL, "리뷰 규칙 B")).isNotEqualTo(base);
        assertThat(GeminiClient.versionOf(MODEL.replace("flash-lite", "pro"), "리뷰 규칙 A")).isNotEqualTo(base);
    }
}
