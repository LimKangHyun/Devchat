package project.api.domain.chat.codereview.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.codereview.app.CodeReviewService;
import project.api.domain.chat.codereview.dto.CodeReviewCreateRequest;
import project.api.domain.chat.codereview.dto.CodeReviewEditRequest;
import project.api.domain.chat.codereview.dto.CodeReviewResponse;

@Tag(name = "Code Review", description = "코드리뷰 API")
@RestController
@RequestMapping("/code-reviews")
@RequiredArgsConstructor
public class CodeReviewController {

	private final CodeReviewService codeReviewService;

	@Operation(summary = "코드리뷰 생성", description = "특정 메시지에 대한 코드리뷰 작성")
	@PostMapping
	public CodeReviewResponse createReview(
		@Valid @RequestBody CodeReviewCreateRequest request,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		return codeReviewService.createReview(request, memberDetails.getId());
	}

	@Operation(summary = "코드리뷰 조회", description = "메시지 기준 코드리뷰 목록 조회")
	@GetMapping("/{messageId}")
	public List<CodeReviewResponse> getReviews(@PathVariable Long messageId,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		return codeReviewService.getReviewsByMessageId(messageId, memberDetails.getId());
	}

	@Operation(summary = "코드리뷰 삭제")
	@DeleteMapping("/{reviewId}")
	public void deleteReview(@PathVariable Long reviewId,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		codeReviewService.deleteReview(reviewId, memberDetails.getId());
	}

	@Operation(summary = "코드리뷰 수정")
	@PutMapping("/{reviewId}")
	public CodeReviewResponse editReview(@PathVariable Long reviewId,
		@Valid @RequestBody CodeReviewEditRequest request,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		return codeReviewService.editReview(reviewId, request, memberDetails.getId());
	}
}
