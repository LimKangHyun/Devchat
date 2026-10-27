package project.ai.client.internal;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import project.common.message.aireview.FileClaimResult;

/** AI 리뷰 처리에 필요한 API 서버 내부 호출(처리 권한 요청, 데드레터 보고). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewInternalApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Value("${internal.api.url}")
    private String apiUrl;

    private final WebClient.Builder webClientBuilder;
    private final InternalJwtProvider internalJwtProvider;

    /**
     * 파일 리뷰의 처리 권한을 요청한다.
     * API 서버에 닿지 못하면 예외를 그대로 던진다. 호출한 쪽은 ACK하지 않고 메시지를 PEL에 남겨 나중에 다시 시도한다.
     * (실패를 "이미 처리 중"으로 바꿔 돌려주면, 아무도 처리하지 않은 채 ACK돼 리뷰가 사라진다)
     *
     * @param reclaim 재처리 스케줄러가 앞 Worker의 Heartbeat 만료를 확인하고 다시 보낸 요청이면 true
     */
    public FileClaimResult claim(Long aiReviewId, String filePath, String commitSha, boolean reclaim) {
        ClaimResponse response = webClientBuilder.build()
            .post()
            .uri(apiUrl + "/internal/ai-review-files/claim")
            .header("Authorization", "Bearer " + internalJwtProvider.issue(null))
            .bodyValue(new ClaimRequest(aiReviewId, filePath, commitSha, reclaim))
            .retrieve()
            .bodyToMono(ClaimResponse.class)
            .block(TIMEOUT);
        if (response == null || response.result() == null) {
            throw new IllegalStateException("claim 응답이 비어 있음 - aiReviewId=" + aiReviewId + ", filePath=" + filePath);
        }
        return response.result();
    }

    /** 최대 재시도를 넘긴 메시지를 기록한다. 기록에 실패해도 로그에 원문이 남으므로 예외는 삼킨다. */
    public void reportDeadLetter(String streamKey, String recordId, String payload) {
        try {
            webClientBuilder.build()
                .post()
                .uri(apiUrl + "/internal/dead-letters")
                .header("Authorization", "Bearer " + internalJwtProvider.issue(null))
                .bodyValue(new DeadLetterRequest(streamKey, recordId, payload))
                .retrieve()
                .toBodilessEntity()
                .block(TIMEOUT);
        } catch (Exception e) {
            log.error("데드레터 기록 실패 - recordId={}, payload={}", recordId, payload, e);
        }
    }

    private record ClaimRequest(Long aiReviewId, String filePath, String commitSha, boolean reclaim) {}
    private record ClaimResponse(FileClaimResult result) {}
    private record DeadLetterRequest(String streamKey, String recordId, String payload) {}
}
