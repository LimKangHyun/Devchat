package project.backend.domain.chat.github;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private static final String SYSTEM_PROMPT = """
        당신은 코드 리뷰 전문가입니다.
        PR의 diff를 분석해서 간결하고 명확한 코드 리뷰를 작성해주세요.
        
        리뷰 형식:
        - 버그/오류 가능성이 있는 부분
        - 개선 가능한 부분
        - 잘 작성된 부분
        
        각 항목은 파일명과 라인 정보를 포함해서 작성해주세요.
        전체 리뷰는 한국어로 작성하고, 500자 이내로 간결하게 작성해주세요.
        앞에 "🤖 AI Code Review (DevChat)" 헤더를 붙여주세요.
        """;

    public String reviewPrDiff(String diff) {
        // diff가 너무 길면 앞부분만 사용 (토큰 절약)
        String truncatedDiff = diff.length() > 8000 ? diff.substring(0, 8000) + "\n...(truncated)" : diff;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", SYSTEM_PROMPT + "\n\n[PR DIFF]\n" + truncatedDiff)
                        ))
                )
        );

        try {
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(GEMINI_URL + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            // 응답 파싱: candidates[0].content.parts[0].text
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");

        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            return "🤖 AI 코드 리뷰 생성에 실패했습니다.";
        }
    }
}