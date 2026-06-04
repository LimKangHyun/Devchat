package project.backend.domain.github.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.github.app.GitMessageService;
import project.backend.domain.github.dto.AiReviewResponse;

@RestController
@RequestMapping("/ai-reviews")
@RequiredArgsConstructor
public class AiReviewController {

    private final GitMessageService gitMessageService;

    @GetMapping("/{aiReviewId}")
    public AiReviewResponse getAiReview(@PathVariable Long aiReviewId) {
        return gitMessageService.getAiReview(aiReviewId);
    }
}