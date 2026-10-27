package project.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import project.ai.budget.LlmUsage;

class GeminiUsageTest {

    // WebClient가 쓰는 Spring Boot ObjectMapper처럼 모르는 필드는 무시한다
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void 응답의_usageMetadata에서_토큰을_읽고_thinking_토큰은_출력에_더한다() throws Exception {
        String json = """
            {"candidates":[{"content":{"parts":[{"text":"[]"}],"role":"model"},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1200,"candidatesTokenCount":80,"thoughtsTokenCount":20,"totalTokenCount":1300},
             "modelVersion":"gemini-2.5-flash-lite"}
            """;
        GeminiClient.GeminiResponse response = mapper.readValue(json, GeminiClient.GeminiResponse.class);

        LlmUsage usage = GeminiClient.usageOf(response);
        assertThat(usage.promptTokens()).isEqualTo(1200);
        assertThat(usage.outputTokens()).isEqualTo(100);
        assertThat(usage.totalTokens()).isEqualTo(1300);
    }

    @Test
    void usageMetadata가_없으면_0() throws Exception {
        GeminiClient.GeminiResponse response =
            mapper.readValue("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"a\"}]}}]}", GeminiClient.GeminiResponse.class);
        assertThat(GeminiClient.usageOf(response)).isEqualTo(LlmUsage.NONE);
    }
}
