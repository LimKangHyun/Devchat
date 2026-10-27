package project.api.domain.aireview.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.aireview.app.AiReviewService;
import project.common.message.aireview.FileClaimResult;

/** AI 서버 전용. 파일 리뷰를 시작하기 전에 처리 권한을 얻는다(/internal/**는 내부 JWT로 인증). */
@RestController
@RequestMapping("/internal/ai-review-files")
@RequiredArgsConstructor
public class AiReviewFileInternalController {

    private final AiReviewService aiReviewService;

    @PostMapping("/claim")
    public ClaimResponse claim(@RequestBody ClaimRequest req) {
        return new ClaimResponse(
            aiReviewService.claimFile(req.aiReviewId(), req.filePath(), req.commitSha(), req.reclaim()));
    }

    /** @param reclaim 재처리 스케줄러가 앞 Worker의 Heartbeat 만료를 확인하고 다시 보낸 요청이면 true */
    public record ClaimRequest(Long aiReviewId, String filePath, String commitSha, boolean reclaim) {}

    public record ClaimResponse(FileClaimResult result) {}
}
