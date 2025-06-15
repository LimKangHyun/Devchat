package project.backend.domain.chat.codereview.app;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.codereview.dao.CodeReviewRepository;
import project.backend.domain.chat.codereview.dto.CodeReviewRequest;
import project.backend.domain.chat.codereview.dto.CodeReviewResponse;
import project.backend.domain.chat.codereview.entity.CodeReview;
import project.backend.domain.chat.codereview.mapper.CodeReviewMapper;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatMessageErrorCode;
import project.backend.global.exception.ex.ChatMessageException;

@Service
@RequiredArgsConstructor
public class CodeReviewService {

	private final CodeReviewRepository codeReviewRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final MemberService memberService;
	private final CodeReviewMapper codeReviewMapper;

	@Transactional
	public CodeReviewResponse createReview(CodeReviewRequest request, Long authorId) {
		Member author = memberService.getMemberById(authorId);

		ChatMessage message = chatMessageRepository.findById(request.messageId())
			.orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

		if (message.getType() != MessageType.CODE) {
			throw new IllegalArgumentException("코드 메시지에만 리뷰 작성 가능");
		}

		//참여자가 아닌 멤버가 리뷰남기는거 막는 거 필요?

		CodeReview codeReview = codeReviewMapper.toEntity(request, message, author);

		CodeReview savedReview = codeReviewRepository.save(codeReview);

		return codeReviewMapper.toResponse(savedReview);
	}

	@Transactional
	public List<CodeReviewResponse> getReviewsByMessageId(Long messageId, Long memberId) {
		ChatMessage message = chatMessageRepository.findById(messageId)
			.orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

		Member member = memberService.getMemberById(memberId);

		List<CodeReview> reviews = codeReviewRepository
			.findByMessageIdOrderByLineNumberAscCreatedAtAsc(messageId);

		return reviews.stream()
			.map(codeReviewMapper::toResponse)
			.toList();
	}

	@Transactional
	public void deleteReview(Long reviewId, Long authorId) {
		CodeReview codeReview = codeReviewRepository.findById(reviewId)
			.orElseThrow(() -> new IllegalArgumentException("해당 코드리뷰 존재하지 않음."));

		if (!codeReview.getAuthor().getId().equals(authorId)) {
			throw new IllegalArgumentException("본인이 작성한 코드리뷰만 삭제할 수 있습니다");
		}

		codeReviewRepository.delete(codeReview);
	}
}
