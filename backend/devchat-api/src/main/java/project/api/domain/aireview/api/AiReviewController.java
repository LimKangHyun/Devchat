package project.api.domain.aireview.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.api.auth.dto.MemberDetails;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.aireview.dto.AiReviewResponse;
import project.api.domain.aireview.dto.ToggleReviewRequest;

@RestController
@RequestMapping("/ai-reviews")
@RequiredArgsConstructor
public class AiReviewController {

    private final AiReviewService aiReviewService;

    @GetMapping("/{aiReviewId}")
    public AiReviewResponse getAiReview(@PathVariable Long aiReviewId) {
        return aiReviewService.getAiReview(aiReviewId);
    }

    @PatchMapping("/{aiReviewId}/reviews/{commentId}/toggle")
    public ResponseEntity<Void> toggleReview(
            @PathVariable Long aiReviewId,
            @PathVariable Long commentId,
            @Valid @RequestBody ToggleReviewRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {
        aiReviewService.toggleComment(commentId, request.reason(), request.otherReason(), memberDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}