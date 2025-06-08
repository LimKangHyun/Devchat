package project.backend.domain.chat.chatmessage.app;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dao.ChatMessageSearchRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.ChatMessageSearch;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.domain.chat.chatmessage.dto.ScrollPaginationCollection;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.errorcode.ChatMessageErrorCode;
import project.backend.global.exception.ex.AuthException;
import project.backend.global.exception.ex.ChatMessageException;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

	private final ChatMessageRepository chatMessageRepository;
	private final ChatRoomService chatRoomService;
	private final MemberService memberService;
	private final ImageFileService imageFileService;
	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageSearchRepository chatMessageSearchRepository;
	private final ChatParticipantRepository chatParticipantRepository;

	private final ChatMessageMapper messageMapper;

	@Transactional
	public ChatMessageResponse save(Long roomId, ChatMessageRequest request, String username) {

		Member sender = memberService.getMemberByUsername(username);

		ChatRoom room = chatRoomService.getRoomById(roomId);

		chatRoomService.validateNotParticipant(sender.getId(), roomId);

		ChatMessage message;

		switch (request.getType()) {
			case IMAGE -> {
				ImageFile findImage = imageFileService.getImageById(request.getImageFileId());
				message = messageMapper.toEntityWithImage(room, sender, findImage);
			}
			case TEXT -> message = messageMapper.toEntityWithText(room, sender, request);
			case CODE -> message = messageMapper.toEntityWithCode(room, sender, request);
			default -> throw new ChatMessageException(ChatMessageErrorCode.INVALID_ROUTE);
		}

		chatMessageRepository.save(message);

		// 검색용 테이블에도 저장된 메시지의 id(pk), roomId, content를 다시 뽑아서 저장
		if (isSearchable(message)) {
			ChatMessageSearch searchMessage = messageMapper.toSearchEntity(message);
			chatMessageSearchRepository.save(searchMessage);
		}

		return messageMapper.toResponse(message);
	}

	private boolean isSearchable(ChatMessage message) {
		return message.getType() != MessageType.IMAGE;
	}

	@Transactional(readOnly = true)
	public Page<ChatMessageSearchResponse> searchMessages(Long memberId, Long roomId,
		@Valid ChatMessageSearchRequest request) {

		String keyword = request.getKeyword();
		int page = request.getPage();
		int size = request.getPageSize();
		int offset = page * size;

		chatRoomService.validateNotParticipant(memberId, roomId);
		// messageIds는 DESC 정렬 보장
		List<Long> messageIds = chatMessageSearchRepository.searchIdsByKeywordAndRoomId(keyword,
			roomId, size, offset);

		long totalCount = chatMessageSearchRepository.countByKeywordAndRoomId(keyword, roomId);

		// findByIdIn은 정렬 보장이 안되므로, chatMessages에 대한 정렬 필요
		List<ChatMessage> chatMessages = chatMessageRepository.findByIdIn(
			messageIds);

		// chatMessage의 빠른 정렬 수행을 위해 Map으로 변환
		Map<Long, ChatMessage> messageMap = chatMessages.stream()
			.collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

		// messageIds의 정렬순서에 맞춰서 chatMessages 정렬 수행
		List<ChatMessageSearchResponse> resultList = messageIds.stream()
			.map(messageMap::get)
			.filter(Objects::nonNull)
			.map(messageMapper::toSearchResponse)
			.collect(Collectors.toList());

		// PageImpl 구체 클래스로 담아서 반환
		return new PageImpl<>(resultList, PageRequest.of(page, size), totalCount);
	}

	@Transactional
	public ChatMessageResponse editMessage(Long roomId, ChatMessageEditRequest request,
		String username) {

		//유효성 확인
		memberService.getMemberByUsername(username);
		chatRoomService.getRoomById(roomId);

		ChatMessage message = chatMessageRepository.findById(request.messageId())
			.orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

		if (!message.getSender().getUsername().equals(username)) {
			throw new AuthException(AuthErrorCode.FORBIDDEN_MESSAGE_EDIT);
		}

		message.updateContent(request.content());

		//현재 코드 언어 변경은 받지 않고 있음 (확장성 고려)
		if (message.getType().equals(MessageType.CODE)) {
			message.updateLanguage(request.language());
		}

		if (isSearchable(message)) {
			chatMessageSearchRepository.findById(message.getId())
				.ifPresent(searchEntity -> {
					searchEntity.updateContent(message.getContent());
				});
		}

		return messageMapper.toResponse(message);
	}

	@Transactional
	public ChatMessageResponse deleteMessage(Long roomId, Long messageId, String username) {

		//유효성 확인
		memberService.getMemberByUsername(username);
		chatRoomService.getRoomById(roomId);

		ChatMessage message = chatMessageRepository.findById(messageId)
			.orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

		if (!message.getSender().getUsername().equals(username)) {
			throw new AuthException(AuthErrorCode.FORBIDDEN_MESSAGE_DELETE);
		}

		message.delete();

		if (isSearchable(message)) {
			chatMessageSearchRepository.findById(message.getId())
				.ifPresent(ChatMessageSearch::deleteContent);
		}

		return messageMapper.toResponse(message);
	}

	// 예외 처리
	@Transactional(readOnly = true)
	public ChatScrollResponse getMessagesByRoomId(Long memberId, Long roomId, Long cursor,
		int size) {
		chatRoomService.getRoomById(roomId);
		chatRoomService.validateNotParticipant(memberId, roomId);

		PageRequest pageRequest = PageRequest.of(0, size + 1);
		List<ChatMessage> result;

		if (cursor == null) {
			result = chatMessageRepository.findByChatRoom_IdOrderByIdDesc(
				roomId, pageRequest);
		} else {
			result = chatMessageRepository.findByChatRoom_IdAndIdLessThanOrderByIdDesc(
				roomId, cursor, pageRequest);
		}
		ScrollPaginationCollection<ChatMessage> scroll = ScrollPaginationCollection.of(result,
			size);

		List<ChatMessageResponse> responses = scroll.getCurrentScrollItems().stream()
			.map(messageMapper::toResponse).toList();

		Long nextCursor = scroll.isLastScroll() ? null : scroll.getNextCursor().getId();

		return new ChatScrollResponse(responses, nextCursor);
	}
}