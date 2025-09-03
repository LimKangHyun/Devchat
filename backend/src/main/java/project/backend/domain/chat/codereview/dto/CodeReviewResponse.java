package project.backend.domain.chat.codereview.dto;

import java.time.LocalDateTime;

public record CodeReviewResponse(
	Long reviewId,
	Long messageId,
	Integer lineNumber,
	String content,
	LocalDateTime createAt,
	String authorName,
	Long authorId
) {}
