package project.backend.domain.github;

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
import project.backend.domain.github.dto.GitEventType;

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

    private static final String INLINE_REVIEW_PROMPT = """
        당신은 코드 리뷰 전문가입니다.
        PR의 diff와 전체 파일 코드를 분석해서 인라인 코드 리뷰를 작성해주세요.
        
        반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.
        
        [
          { "lineNumber": 5, "comment": "여기에 코멘트" },
          { "lineNumber": 12, "comment": "여기에 코멘트" }
        ]
        
        규칙:
        - [전체 파일 코드]는 PR 머지 이후의 최신 파일입니다
        - lineNumber는 [전체 파일 코드] 기준 줄 번호입니다
        - diff에서 "+" 로 시작하는 줄(추가된 코드) 위주로 리뷰하세요
        - diff에서 "-" 로 시작하는 줄(삭제된 코드)은 리뷰 대상이 아닙니다
        - 아래 항목 중 하나에 해당할 때만 리뷰하세요:
            [코드 품질]
            1. 버그 또는 NPE 발생 가능성
            2. 명백한 성능 문제 (N+1, 불필요한 전체 조회 등)
            3. 보안 취약점
            4. 잘못된 트랜잭션/동시성 처리
            
            [클린코드]
            5. 메서드명이 동작을 정확히 설명하지 못하는 경우 (예: getData → findActiveUserById)
            6. 하나의 메서드가 2가지 이상의 책임을 가진 경우 (SRP 위반)
            7. 10줄 이상이면서 단일 책임으로 분리 가능한 메서드
            
            - 클린코드 항목은 명백히 개선이 필요한 경우에만 작성하고, 애매하면 생략하세요
            - 리뷰할 내용이 없으면 빈 배열 [] 반환
            - comment: 한국어, 마크다운 금지, 문제점과 개선 방향을 한 문장으로
            - JSON 외 텍스트 절대 금지
        """;

    private static final String ISSUE_SUMMARY_PROMPT = """
        당신은 GitHub 이슈 요약 전문가입니다.
        아래 GitHub 이슈 내용을 개발자가 빠르게 이해할 수 있도록 요약해주세요.
    
        작성 규칙:
        - 반드시 "📌 [ 이슈 요약 ]"을 먼저 적고 줄바꿈을 한 뒤 시작하세요
        - 어떤 문제가 발생했는지 또는 무엇을 요청하는지 사실만 2~3줄로 작성하세요
        - "~할 수 있습니다", "~을 통해" 같은 불필요한 설명 문장은 작성하지 마세요
        - 한국어로 작성하세요
        - 마크다운 문법 사용 금지 (**, __, ## 등)
        """;

    private static final String PR_SUMMARY_PROMPT = """
        당신은 GitHub PR 요약 전문가입니다.
        아래 GitHub PR 내용을 개발자가 빠르게 이해할 수 있도록 요약해주세요.
        
        작성 규칙:
        - 반드시 "🔀 [ PR 요약 ]"을 먼저 적고 줄바꿈을 한 뒤 시작하세요
        - 핵심 변경사항만 2~3줄로 작성하세요
        - "~할 수 있습니다", "~을 통해", "이를 통해" 같은 불필요한 설명 문장은 작성하지 마세요
        - 무엇이 추가/수정/삭제되었는지 사실만 간결하게 서술하세요
        - 한국어로 작성하세요
        - 마크다운 문법 사용 금지 (**, __, ## 등)
        """;

    private static final String PR_REVIEW_SUMMARY_PROMPT = """
        당신은 GitHub PR 리뷰 요약 전문가입니다.
        아래 GitHub PR 리뷰 내용을 개발자가 빠르게 이해할 수 있도록 요약해주세요.
    
        작성 규칙:
        - 반드시 "💬 리뷰: " 로 시작하세요
        - 리뷰어가 지적한 내용과 요청 사항만 2~3줄로 작성하세요
        - "~할 수 있습니다", "~을 통해" 같은 불필요한 설명 문장은 작성하지 마세요
        - 무엇이 문제이고 어떻게 수정을 요청했는지 사실만 간결하게 서술하세요
        - 한국어로 작성하세요
        - 마크다운 문법 사용 금지 (**, __, ## 등)
        """;

    private static final String WORKFLOW_SUMMARY_PROMPT = """
        당신은 GitHub 워크플로우 결과 요약 전문가입니다.
        아래 GitHub 워크플로우 결과를 개발자가 빠르게 이해할 수 있도록 요약해주세요.
    
        작성 규칙:
        - 성공이면 "✅ 배포: ", 실패면 "❌ 배포: ", 취소면 "⚠️ 배포: " 로 시작하세요
        - 어떤 워크플로우가 어떤 결과였는지 사실만 1~2줄로 작성하세요
        - 실패한 경우 실패 원인이 있다면 포함하세요
        - "~할 수 있습니다", "~을 통해" 같은 불필요한 설명 문장은 작성하지 마세요
        - 한국어로 작성하세요
        - 마크다운 문법 사용 금지 (**, __, ## 등)
        """;

    public List<Map<String, Object>> reviewPrDiffInline(String diff, String fileContent) {
        String truncatedDiff = diff.length() > 4000 ? diff.substring(0, 4000) + "\n...(truncated)" : diff;
        String truncatedContent = fileContent.length() > 4000 ? fileContent.substring(0, 4000) + "\n...(truncated)" : fileContent;

        String prompt = INLINE_REVIEW_PROMPT
                + "\n\n[PR DIFF]\n" + truncatedDiff
                + "\n\n[전체 파일 코드 - 앞의 숫자가 lineNumber]\n" + addLineNumbers(truncatedContent);

        String response = callGemini(prompt);

        try {
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("AI 인라인 리뷰 파싱 실패: {}", response, e);
            return List.of();
        }
    }

    private String addLineNumbers(String content) {
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d: %s\n", i + 1, lines[i]));
        }
        return sb.toString();
    }

    public String summarizeGitEvent(String rawContent, GitEventType eventType) {
        String prompt = switch (eventType) {
            case ISSUE_OPEN -> ISSUE_SUMMARY_PROMPT;
            case PR_OPEN, PR_MERGED -> PR_SUMMARY_PROMPT;
            case PR_REVIEW -> PR_REVIEW_SUMMARY_PROMPT;
            case WORKFLOW_RUN -> WORKFLOW_SUMMARY_PROMPT;
        };
        try {
            return callGemini(prompt + "\n\n[이벤트 내용]\n" + rawContent);
        } catch (Exception e) {
            log.error("Gemini 요약 실패, 원본 반환", e);
            return rawContent;
        }
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

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
    }
}