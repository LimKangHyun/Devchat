package project.backend.domain.chat.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import project.backend.domain.chat.github.dto.GitEventType;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private static final String REVIEW_PROMPT = """
        당신은 시니어 백엔드 개발자이며 코드 리뷰 전문가입니다.
        PR의 diff를 분석하여 실제 코드 품질 개선에 도움이 되는 리뷰만 작성하세요.
        
        [리뷰 규칙]
        * 반드시 diff 기준으로 변경된 코드만 리뷰하세요
        * 추상적인 표현 금지 (예: "가독성이 좋습니다", "개선이 필요합니다")
        * 각 항목은 구체적인 이유 + 개선 방향을 포함하세요
        * 라인 정보는 "파일명:라인번호" 형식으로 작성하세요
        * 중요도가 높은 순서대로 작성하세요 (버그 > 성능 > 구조 > 스타일)
        
        [출력 형식]
        🤖 AI Code Review (DevChat)
        
        1. 버그/오류 가능성
        * 파일명:라인번호 - 문제 설명 + 이유 + 수정 제안
        
        2. 개선 사항
        * 파일명:라인번호 - 개선 이유 + 구체적인 방법
        
        3. 잘된 점 (최대 2개)
        * 파일명:라인번호 - 왜 좋은지 명확하게 설명
        
        전체 리뷰는 한국어로 작성하고, 700자 이내로 작성하세요.
        마크다운 문법 사용 금지 (**, __, ## 등 사용 금지)
        """;

    private static final String INLINE_REVIEW_PROMPT = """
        당신은 코드 리뷰 전문가입니다.
        PR의 diff와 전체 파일 코드를 분석해서 인라인 코드 리뷰를 작성해주세요.
        
        반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.
        
        [
          { "lineNumber": 5, "comment": "여기에 코멘트" },
          { "lineNumber": 12, "comment": "여기에 코멘트" }
        ]
        
        규칙:
        - lineNumber는 전체 파일 기준 줄 번호입니다
        - 변경된 줄(+/-) 위주로 리뷰하되, 관련된 주변 코드도 참고하세요
        - 각 코멘트는 한국어로 간결하게 작성하세요
        - 마크다운 문법 사용 금지
        - JSON 외 다른 텍스트 절대 금지
        """;

    private static final String ISSUE_SUMMARY_PROMPT = """
        당신은 GitHub 이벤트 분석 전문가입니다.
        아래 GitHub 이슈 내용을 개발자가 빠르게 이해할 수 있도록 요약해주세요.
        
        작성 규칙:
        - 반드시 "📌 [ 이슈 요약 ]"을 먼저적고 줄바꿈을 한 뒤 시작해주세요
        - 본문 내용이 있다면 핵심 변경사항을 2~3줄로 설명해주세요 (가독성을 위해 줄바꿈을 활용해주세요)
        - 한국어로 작성해주세요
        - 마크다운 문법 사용 금지 (**, __, ## 등 사용하지 마세요)
        """;

    private static final String PR_SUMMARY_PROMPT = """
        당신은 GitHub 이벤트 분석 전문가입니다.
        아래 GitHub PR 내용을 개발자가 빠르게 이해할 수 있도록 요약해주세요.
        
        작성 규칙:
        - 반드시 "🔀 [ PR 요약 ]"을 먼저적고 줄바꿈을 한 뒤 시작해주세요
        - 본문 내용이 있다면 핵심 변경사항을 2~3줄로 설명해주세요 (가독성을 위해 줄바꿈을 활용해주세요)
        - 한국어로 작성해주세요
        - 마크다운 문법 사용 금지 (**, __, ## 등 사용하지 마세요)
        """;

    private static final String PR_REVIEW_SUMMARY_PROMPT = """
        당신은 GitHub 이벤트 분석 전문가입니다.
        아래 GitHub PR 리뷰 내용을 개발자가 빠르게 이해할 수 있도록 요약해주세요.
        
        작성 규칙:
        - 반드시 "💬 리뷰: " 로 시작해주세요
        - 리뷰 내용을 2~3줄로 설명해주세요 (가독성을 위해 줄바꿈을 활용해주세요)
        - 한국어로 작성해주세요
        - 마크다운 문법 사용 금지 (**, __, ## 등 사용하지 마세요)
        """;

    private static final String WORKFLOW_SUMMARY_PROMPT = """
        당신은 GitHub 이벤트 분석 전문가입니다.
        아래 GitHub 워크플로우 결과를 개발자가 빠르게 이해할 수 있도록 요약해주세요.
        
        작성 규칙:
        - 성공이면 "✅ 배포: ", 실패면 "❌ 배포: ", 취소면 "⚠️ 배포: " 로 시작해주세요
        - 워크플로우 결과를 1~2줄로 설명해주세요
        - 한국어로 작성해주세요
        - 마크다운 문법 사용 금지 (**, __, ## 등 사용하지 마세요)
        """;

    public String reviewPrDiff(String diff) {
        String truncatedDiff = diff.length() > 8000 ? diff.substring(0, 8000) + "\n...(truncated)" : diff;
        return callGemini(REVIEW_PROMPT + "\n\n[PR DIFF]\n" + truncatedDiff, "🤖 AI 코드 리뷰 생성에 실패했습니다.");
    }

    public List<Map<String, Object>> reviewPrDiffInline(String diff, String fileContent) {
        String truncatedDiff = diff.length() > 4000 ? diff.substring(0, 4000) + "\n...(truncated)" : diff;
        String truncatedContent = fileContent.length() > 4000 ? fileContent.substring(0, 4000) + "\n...(truncated)" : fileContent;

        String prompt = INLINE_REVIEW_PROMPT
                + "\n\n[PR DIFF]\n" + truncatedDiff
                + "\n\n[전체 파일 코드]\n" + truncatedContent;

        String response = callGemini(prompt, "[]");

        try {
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("AI 인라인 리뷰 파싱 실패: {}", response, e);
            return List.of();
        }
    }

    public String summarizeGitEvent(String rawContent, GitEventType eventType) {
        String prompt = switch (eventType) {
            case ISSUE_OPEN -> ISSUE_SUMMARY_PROMPT;
            case PR_OPEN, PR_MERGED -> PR_SUMMARY_PROMPT;
            case PR_REVIEW -> PR_REVIEW_SUMMARY_PROMPT;
            case WORKFLOW_RUN -> WORKFLOW_SUMMARY_PROMPT;
        };
        return callGemini(prompt + "\n\n[이벤트 내용]\n" + rawContent, rawContent);
    }

    private String callGemini(String prompt, String fallback) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
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

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");

        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            return fallback;
        }
    }
}