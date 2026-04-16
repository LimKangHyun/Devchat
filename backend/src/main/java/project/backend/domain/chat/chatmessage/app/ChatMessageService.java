package project.backend.domain.chat.chatmessage.app;

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
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dao.ChatMessageSearchRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchSlice;
import project.backend.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.backend.domain.chat.chatmessage.app.event.ChatMessageBroadcastEvent;
import project.backend.domain.chat.chatmessage.app.event.ChatMessageSavedEvent;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.ChatMessageSearch;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomSequenceService;
import project.backend.domain.chat.chatroom.app.ChatRoomParticipantService;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.member.app.ProfileImageCache;
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
    private final ChatMessageSearchRepository chatMessageSearchRepository;

    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ImageFileService imageFileService;

    private final ApplicationEventPublisher eventPublisher;

    private final EntityManager entityManager;
    private final ChatMessageMapper messageMapper;
    private final ChatRoomSequenceService chatRoomSequenceService;
    private final ProfileImageCache profileImageCache;

    @Transactional
    public ChatMessageResponse save(Long roomId, ChatMessageRequest request,
                                    MemberDetails memberDetails) {

        Long seq = chatRoomSequenceService.genMessageSeq(roomId, memberDetails.getId());

        Member sender = entityManager.getReference(Member.class, memberDetails.getId());
        ChatRoom room = entityManager.getReference(ChatRoom.class, roomId);

        ChatMessage message = createMessage(room, sender, request, seq);
        chatMessageRepository.save(message);

        String profileImage = profileImageCache.getProfileImage(memberDetails.getId());
        publishEvents(message, memberDetails, profileImage);

        return messageMapper.toResponse(message, memberDetails, profileImage);
    }

    private ChatMessage createMessage(ChatRoom room, Member sender,
                                      ChatMessageRequest request, Long seq) {
        return switch (request.getType()) {
            case IMAGE -> {
                ImageFile findImage = imageFileService.getImageById(request.getImageFileId());
                yield messageMapper.toEntityWithImage(room, sender, findImage, seq);
            }
            case TEXT -> messageMapper.toEntityWithText(room, sender, request, seq);
            case CODE -> messageMapper.toEntityWithCode(room, sender, request, seq);
            default -> throw new ChatMessageException(ChatMessageErrorCode.INVALID_ROUTE);
        };
    }

    private void publishEvents(ChatMessage message, MemberDetails memberDetails,
                               String profileImage) {
        if (isSearchable(message)) {
            eventPublisher.publishEvent(ChatMessageSavedEvent.from(message));
        }
        eventPublisher.publishEvent(ChatMessageBroadcastEvent.from(message, memberDetails, profileImage));
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
        String profileImage = profileImageCache.getProfileImage(memberDetails.getId());
        eventPublisher.publishEvent(ChatMessageBroadcastEvent.from(message, memberDetails, profileImage));
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
        String profileImage = profileImageCache.getProfileImage(memberDetails.getId());
        eventPublisher.publishEvent(ChatMessageBroadcastEvent.from(message, memberDetails, profileImage));
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

    public ChatMessage saveJoinEvent(ChatRoom room, Member member, Long seq) {
        ChatMessage message = messageMapper.toEntityWithJoinEvent(room, member, LocalDateTime.now(), seq);
        return chatMessageRepository.save(message);
    }

    @Transactional
    public void deleteByRoomId(Long roomId) {
        chatMessageRepository.deleteByChatRoom_Id(roomId);
        chatMessageSearchRepository.deleteByRoomId(roomId);
    }
}