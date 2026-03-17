package project.backend.domain.chat.chatmessage.app;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dao.ChatMessageSearchRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchSlice;
import project.backend.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.backend.domain.chat.chatmessage.dto.event.ChatMessageSavedEvent;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.ChatMessageSearch;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
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
import project.backend.global.metric.TimeTrace;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatMessageSearchRepository chatMessageSearchRepository;

    private final ChatRoomService chatRoomService;
    private final MemberService memberService;
    private final ImageFileService imageFileService;

    private final ApplicationEventPublisher eventPublisher;

    private final EntityManager entityManager;
    private final ChatMessageMapper messageMapper;

    @Transactional
    public ChatMessageResponse save(Long roomId, ChatMessageRequest request,
        MemberDetails memberDetails) {

        Member sender = entityManager.getReference(Member.class, memberDetails.getId());
        ChatRoom room = entityManager.getReference(ChatRoom.class, roomId);

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

        chatRoomRedisRepository.handleMessageDelivery(roomId);

        if (isSearchable(message)) {
            eventPublisher.publishEvent(ChatMessageSavedEvent.from(message));
        }
        return messageMapper.toResponse(message, memberDetails);
    }

    private boolean isSearchable(ChatMessage message) {
        return message.getType() != MessageType.IMAGE;
    }

    @Transactional(readOnly = true)
    public Slice<ChatMessageSearchResponse> searchMessages(Long memberId, Long roomId,
        @Valid ChatMessageSearchRequest request) {

        chatRoomService.validateParticipant(memberId, roomId);

        List<Long> messageIds = chatMessageSearchRepository.searchIdsByKeywordAndRoomIdWithCursor(
            request.getKeyword(),
            roomId,
            request.getLastMessageId(),
            request.getPageSize() + 1
        );

        boolean hasNext = messageIds.size() > request.getPageSize();
        if (hasNext) {
            messageIds.remove(messageIds.size() - 1);
        }

        Long totalCount = null;
        if (request.getLastMessageId() == null) {
            totalCount = chatMessageSearchRepository.countByKeywordAndRoomId(
                request.getKeyword(), roomId);
        }

        List<ChatMessageSearchResponse> resultList = chatMessageRepository.findByIdIn(messageIds)
            .stream()
            .sorted(Comparator.comparingInt(cm -> messageIds.indexOf(cm.getId())))
            .map(messageMapper::toSearchResponse)
            .toList();

        return new ChatMessageSearchSlice(resultList,
            PageRequest.of(0, request.getPageSize()), hasNext, totalCount);
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
    @TimeTrace
    @Transactional(readOnly = true)
    public ChatScrollResponse getMessagesByRoomId(Long memberId, Long roomId, Long cursor,
        int size) {
        chatRoomService.getRoomById(roomId);
        chatRoomService.validateParticipant(memberId, roomId);

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