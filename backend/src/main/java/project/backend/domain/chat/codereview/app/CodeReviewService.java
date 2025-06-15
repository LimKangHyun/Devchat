package project.backend.domain.chat.codereview.app;

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

}
