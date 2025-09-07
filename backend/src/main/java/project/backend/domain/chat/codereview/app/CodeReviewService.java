package project.backend.domain.chat.codereview.app;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.codereview.dao.CodeReviewRepository;
import project.backend.domain.chat.codereview.dto.CodeReviewCreateRequest;
import project.backend.domain.chat.codereview.dto.CodeReviewEditRequest;
import project.backend.domain.chat.codereview.dto.CodeReviewResponse;
import project.backend.domain.chat.codereview.entity.CodeReview;
import project.backend.domain.chat.codereview.mapper.CodeReviewMapper;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatMessageErrorCode;
import project.backend.global.exception.errorcode.CodeReviewErrorCode;
import project.backend.global.exception.ex.ChatMessageException;
import project.backend.global.exception.ex.CodeReviewException;

@Service
@RequiredArgsConstructor
public class CodeReviewService {

	private final CodeReviewRepository codeReviewRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final MemberService memberService;
	private final CodeReviewMapper codeReviewMapper;
	private final ChatRoomService chatRoomService;

	@Transactional
	public CodeReviewResponse createReview(CodeReviewCreateRequest request, Long authorId) {
		Member author = memberService.getMemberById(authorId);

		ChatMessage message = chatMessageRepository.findById(request.messageId())
			.orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

		if (message.getType() != MessageType.CODE) {
			throw new CodeReviewException(CodeReviewErrorCode.INVALID_MESSAGE_TYPE);
		}

		chatRoomService.validateParticipant(authorId, message.getChatRoom().getId());

		CodeReview codeReview = codeReviewMapper.toEntity(request, message, author);

		CodeReview savedReview = codeReviewRepository.save(codeReview);

		return codeReviewMapper.toResponse(savedReview);
	}

	@Transactional(readOnly = true)
	public List<CodeReviewResponse> getReviewsByMessageId(Long messageId, Long memberId) {
		ChatMessage message = chatMessageRepository.findById(messageId)
			.orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

		chatRoomService.validateParticipant(memberId,message.getChatRoom().getId());

		List<CodeReview> reviews = codeReviewRepository
			.findByMessageIdOrderByLineNumberAscCreatedAtAsc(messageId);

		return reviews.stream()
			.map(codeReviewMapper::toResponse)
			.toList();
	}

	@Transactional
	public void deleteReview(Long reviewId, Long authorId) {
		CodeReview codeReview = validateReviewOwnership(reviewId, authorId);

		chatRoomService.validateParticipant(authorId,
			codeReview.getMessage().getChatRoom().getId());

		codeReviewRepository.delete(codeReview);
	}

	@Transactional
	public CodeReviewResponse editReview(Long reviewId, CodeReviewEditRequest request, Long authorId) {
		CodeReview editCodeReview = validateReviewOwnership(reviewId, authorId);

		chatRoomService.validateParticipant(authorId,
			editCodeReview.getMessage().getChatRoom().getId());

		editCodeReview.editReview(request.content());

		return codeReviewMapper.toResponse(editCodeReview);
	}

	private CodeReview validateReviewOwnership(Long reviewId, Long authorId) {
		CodeReview codeReview = codeReviewRepository.findById(reviewId)
			.orElseThrow(() -> new CodeReviewException(CodeReviewErrorCode.REVIEW_NOT_FOUND));

		if (!codeReview.getAuthor().getId().equals(authorId)) {
			throw new CodeReviewException(CodeReviewErrorCode.UNAUTHORIZED_ACCESS);
		}

		return codeReview;
	}
}
