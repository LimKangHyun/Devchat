package project.backend.domain.chat.codereview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CodeReviewCreateRequest(
	@NotNull(message = "메세지 ID는 필수입니다")
	Long messageId,

	@NotNull(message = "줄번호는 필수입니다")
	Integer lineNumber,

	@NotNull(message = "리뷰내용은 필수입니다")
	@Size(max = 500, message = "리뷰는 500자 이하여야 합니다")
	String content
) {}
