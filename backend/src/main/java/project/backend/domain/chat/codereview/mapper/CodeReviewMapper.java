package project.backend.domain.chat.codereview.mapper;

import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.codereview.dto.CodeReviewRequest;
import project.backend.domain.chat.codereview.dto.CodeReviewResponse;
import project.backend.domain.chat.codereview.entity.CodeReview;
import project.backend.domain.member.entity.Member;

@Component
public class CodeReviewMapper {

	public CodeReview toEntity(CodeReviewRequest request, ChatMessage message, Member author) {
		return CodeReview.builder()
			.message(message)
			.author(author)
			.lineNumber(request.lineNumber())
			.content(request.content())
			.build();
	}

	public CodeReviewResponse toResponse(CodeReview codeReview) {
		return new CodeReviewResponse(
			codeReview.getId(),
			codeReview.getMessage().getId(),
			codeReview.getLineNumber(),
			codeReview.getContent(),
			codeReview.getCreatedAt(),
			codeReview.getAuthor().getNickname(),
			codeReview.getAuthor().getId()
		);
	}


}
