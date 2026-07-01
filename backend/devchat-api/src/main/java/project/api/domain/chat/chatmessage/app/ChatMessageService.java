package project.api.domain.chat.chatmessage.app;

import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatsearch.dao.ChatMessageSearchRepository;
import project.api.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.api.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.api.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.api.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.api.domain.chat.chatmessage.dto.ChatMessageSearchSlice;
import project.api.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.api.domain.chat.chatmessage.event.ChatMessageBroadcastEvent;
import project.api.domain.chat.chatmessage.event.ChatMessageSavedEvent;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatsearch.entity.ChatMessageSearch;
import project.api.domain.chat.chatmessage.entity.MessageType;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatroom.app.ChatRoomParticipantService;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.imagefile.ImageFile;
import project.api.domain.imagefile.ImageFileService;
import project.api.domain.member.entity.Member;
import project.api.domain.chat.chatmessage.dto.ScrollPaginationCollection;
import project.api.global.exception.errorcode.AuthErrorCode;
import project.api.global.exception.errorcode.ChatMessageErrorCode;
import project.api.global.exception.ex.AuthException;
import project.api.global.exception.ex.ChatMessageException;
import project.api.global.metric.TimeTrace;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSearchRepository chatMessageSearchRepository;

    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ImageFileService imageFileService;

    private final ApplicationEventPublisher eventPublisher;

    private final EntityManager entityManager;
    private final ChatMessageMapper messageMapper;

    @Transactional
    public void save(Long roomId, ChatMessageRequest request,
                                    MemberDetails memberDetails) {

        Member sender = entityManager.getReference(Member.class, memberDetails.getId());
        ChatRoom room = entityManager.getReference(ChatRoom.class, roomId);

        ChatMessage message = createMessage(room, sender, request);
        chatMessageRepository.save(message);

        if (isSearchable(message)) {
            entityManager.persist(messageMapper.toIndexStatus(message));
        }
        publishEvents(message, memberDetails);
    }

    private ChatMessage createMessage(ChatRoom room, Member sender,
                                      ChatMessageRequest request) {
        return switch (request.getType()) {
            case IMAGE -> {
                ImageFile findImage = imageFileService.getImageById(request.getImageFileId());
                yield messageMapper.toEntityWithImage(room, sender, findImage);
            }
            case TEXT -> messageMapper.toEntityWithText(room, sender, request);
            case CODE -> messageMapper.toEntityWithCode(room, sender, request);
            default -> throw new ChatMessageException(ChatMessageErrorCode.INVALID_ROUTE);
        };
    }

    private void publishEvents(ChatMessage message, MemberDetails memberDetails) {

        eventPublisher.publishEvent(ChatMessageSavedEvent.from(message));
        eventPublisher.publishEvent(ChatMessageBroadcastEvent.from(message, memberDetails));
    }

    private boolean isSearchable(ChatMessage message) {
        return message.getType() != MessageType.IMAGE;
    }

    @Transactional(readOnly = true)
    public Slice<ChatMessageSearchResponse> searchMessages(Long memberId, Long roomId,
                                                           String keyword, Long lastMessageId, int pageSize) {
        chatRoomParticipantService.validateParticipant(memberId, roomId);

        List<Long> messageIds = chatMessageSearchRepository.searchIdsByKeywordAndRoomIdWithCursor(
                keyword,
                roomId,
                lastMessageId,
                pageSize + 1
        );

        boolean hasNext = messageIds.size() > pageSize;
        if (hasNext) {
            messageIds.remove(messageIds.size() - 1);
        }

        Long totalCount = null;
        if (lastMessageId == null) {
            totalCount = chatMessageSearchRepository.countByKeywordAndRoomId(keyword, roomId);
        }

        List<ChatMessageSearchResponse> resultList = chatMessageRepository.findByIdIn(messageIds)
                .stream()
                .sorted(Comparator.comparingInt(cm -> messageIds.indexOf(cm.getId())))
                .map(messageMapper::toSearchResponse)
                .toList();

        return new ChatMessageSearchSlice(resultList,
                PageRequest.of(0, pageSize), hasNext, totalCount);
    }

    @Transactional
    public void editMessage(Long roomId, ChatMessageEditRequest request, MemberDetails memberDetails) {

        ChatMessage message = chatMessageRepository.findById(request.messageId())
                .orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new AuthException(AuthErrorCode.FORBIDDEN_MESSAGE_EDIT);
        }

        if (!message.getSender().getId().equals(memberDetails.getId())) {
            throw new AuthException(AuthErrorCode.FORBIDDEN_MESSAGE_EDIT);
        }

        message.updateContent(request.content());

        if (message.getType().equals(MessageType.CODE)) {
            message.updateLanguage(request.language());
        }

        if (isSearchable(message)) {
            chatMessageSearchRepository.findById(message.getId())
                    .ifPresent(searchEntity -> {
                        searchEntity.updateContent(message.getContent());
                    });
        }
        eventPublisher.publishEvent(ChatMessageBroadcastEvent.from(message, memberDetails));
    }

    @Transactional
    public void deleteMessage(Long roomId, Long messageId, MemberDetails memberDetails) {

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ChatMessageException(ChatMessageErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new AuthException(AuthErrorCode.FORBIDDEN_MESSAGE_DELETE);
        }

        if (!message.getSender().getId().equals(memberDetails.getId())) {
            throw new AuthException(AuthErrorCode.FORBIDDEN_MESSAGE_DELETE);
        }

        message.delete();

        if (isSearchable(message)) {
            chatMessageSearchRepository.findById(message.getId())
                    .ifPresent(ChatMessageSearch::deleteContent);
        }
        eventPublisher.publishEvent(ChatMessageBroadcastEvent.from(message, memberDetails));
    }

    @TimeTrace
    @Transactional(readOnly = true)
    public ChatScrollResponse getMessagesByRoomId(Long memberId, Long roomId, Long cursor,
                                                  int size) {
        chatRoomParticipantService.validateParticipant(memberId, roomId);

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

    public ChatMessage saveJoinEvent(ChatRoom room, Member member) {
        ChatMessage message = messageMapper.toEntityWithJoinEvent(room, member, LocalDateTime.now());
        return chatMessageRepository.save(message);
    }

    @Transactional
    public void deleteByRoomId(Long roomId) {
        chatMessageRepository.deleteByChatRoom_Id(roomId);
        chatMessageSearchRepository.deleteByRoomId(roomId);
    }
}