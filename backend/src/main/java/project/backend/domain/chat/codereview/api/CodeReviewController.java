package project.backend.domain.chat.codereview.api;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.codereview.app.CodeReviewService;
import project.backend.domain.chat.codereview.dto.CodeReviewRequest;
import project.backend.domain.chat.codereview.dto.CodeReviewResponse;

@RestController
@RequestMapping("/code-reviews")
@RequiredArgsConstructor
public class CodeReviewController {

	private final CodeReviewService codeReviewService;

	@PostMapping
	public CodeReviewResponse createReview(
		@Valid @RequestBody CodeReviewRequest request,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		return codeReviewService.createReview(request, memberDetails.getId());
	}

	@GetMapping("/{messageId}")
	public List<CodeReviewResponse> getReviews(@PathVariable Long messageId,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		return codeReviewService.getReviewsByMessageId(messageId, memberDetails.getId());
	}

	@DeleteMapping("/{reviewId}")
	public void deleteReview(@PathVariable Long reviewId,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		codeReviewService.deleteReview(reviewId, memberDetails.getId());
	}

	@PutMapping("/{reviewId}")
	public CodeReviewResponse editReview(@PathVariable Long reviewId,
		@RequestBody CodeReviewRequest request,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		return codeReviewService.editReview(reviewId, request, memberDetails.getId());
	}
}
