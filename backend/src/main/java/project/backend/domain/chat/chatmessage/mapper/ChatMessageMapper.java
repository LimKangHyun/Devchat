package project.backend.domain.chat.chatmessage.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchProjection;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.dto.event.ChatMessageBroadcastEvent;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.ChatMessageIndexStatus;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatsearch.entity.ChatMessageSearch;
import project.backend.domain.chat.chatroom.dto.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.github.dto.GitMessageDto;
import project.backend.domain.github.entity.AiReview;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.member.entity.Member;

@Slf4j
@Component
public class ChatMessageMapper {

    private final ObjectMapper objectMapper;
    private final String githubProfile;

    public ChatMessageMapper(
            ObjectMapper objectMapper,
            @Value("${file.images.profile.github}") String githubProfile) {
        this.objectMapper = objectMapper;
        this.githubProfile = githubProfile;
    }

    public ChatMessage toEntityWithText(ChatRoom room, Member sender, ChatMessageRequest request) {
        return ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .content(request.getContent())
                .type(MessageType.TEXT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public ChatMessage toEntityWithCode(ChatRoom room, Member sender, ChatMessageRequest request) {
        return ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .content(request.getContent())
                .type(MessageType.CODE)
                .createdAt(LocalDateTime.now())
                .codeLanguage(request.getLanguage())
                .build();
    }

    public ChatMessage toEntityWithImage(ChatRoom room, Member sender, ImageFile chatImage) {
        return ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .type(MessageType.IMAGE)
                .createdAt(LocalDateTime.now())
                .chatImage(chatImage)
                .build();
    }

    public ChatMessage toEntityWithGit(GitMessageDto gitMessage, Member githubBot) {
        return ChatMessage.builder()
                .chatRoom(gitMessage.getRoom())
                .type(MessageType.GIT)
                .content(gitMessage.getContent())
                .createdAt(LocalDateTime.now())
                .sender(githubBot)
                .build();
    }

    public ChatMessage toEntityWithJoinEvent(ChatRoom room, Member sender, LocalDateTime now) {
        return ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .content(sender.getNickname() + "님이 입장했습니다.")
                .type(MessageType.EVENT)
                .createdAt(now)
                .build();
    }

    public ChatMessage toEntityWithLeaveEvent(ChatRoom room, Member sender, LeaveChatRoomEvent leaveEvent) {
        return ChatMessage.builder()
                .chatRoom(room)
                .sender(sender)
                .content(leaveEvent.nickname() + "님이 나갔습니다.")
                .type(MessageType.EVENT)
                .createdAt(leaveEvent.leaveAt())
                .build();
    }

    public ChatMessageSearch toSearchEntity(ChatMessageSearchProjection projection) {
        return ChatMessageSearch.builder()
                .id(projection.getId())
                .roomId(projection.getChatRoomId())
                .content(projection.getContent())
                .build();
    }

    public ChatMessageResponse toResponse(ChatMessage message) {
        log.info("aiReview: {}", message.getAiReview());
        log.info("prNumber: {}", message.getPrNumber());
        return ChatMessageResponse.builder()
                .senderName(message.getSender().getNickname())
                .content(message.getContent())
                .type(message.getType())
                .createdAt(message.getCreatedAt())
                .language(message.getCodeLanguage())
                .profileImageUrl(message.getSender().getProfileImage())
                .chatImageUrl(
                        Optional.ofNullable(message.getChatImage())
                                .map(ImageFile::getStoreFileName)
                                .orElse(null)
                )
                .senderId(message.getSender().getId())
                .messageId(message.getId())
                .status(message.getStatus())
                .prNumber(message.getPrNumber())
                .aiReviewId(message.getAiReview() != null ? message.getAiReview().getId() : null)
                .aiReviewStatus(message.getAiReview() != null ? message.getAiReview().getStatus().name() : null)
                .build();
    }

    public ChatMessageResponse toResponse(ChatMessage message, MemberDetails memberDetails, String profileImage) {
        return ChatMessageResponse.builder()
                .senderName(memberDetails.getNickname())
                .content(message.getContent())
                .type(message.getType())
                .createdAt(message.getCreatedAt())
                .language(message.getCodeLanguage())
                .profileImageUrl(profileImage)
                .chatImageUrl(
                        Optional.ofNullable(message.getChatImage())
                                .map(ImageFile::getStoreFileName)
                                .orElse(null)
                )
                .senderId(memberDetails.getId())
                .messageId(message.getId())
                .status(message.getStatus())
                .build();
    }

    public ChatMessageIndexStatus toIndexStatus(ChatMessage message) {
        return ChatMessageIndexStatus.builder()
                .messageId(message.getId())
                .roomId(message.getChatRoom().getId())
                .build();
    }

    public ChatMessageSearchResponse toSearchResponse(ChatMessage message) {
        return ChatMessageSearchResponse.builder()
                .messageId(message.getId())
                .content(message.getContent())
                .senderName(message.getSender().getNickname())
                .profileImageUrl(message.getSender().getProfileImage())
                .sendAt(message.getCreatedAt())
                .type(message.getType())
                .build();
    }

    public ChatMessageResponse toGitResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .senderName(message.getSender().getNickname())
                .content(message.getContent())
                .type(message.getType())
                .createdAt(message.getCreatedAt())
                .messageId(message.getId())
                .profileImageUrl(githubProfile)
                .build();
    }

    public ChatMessageResponse toBroadcastResponse(ChatMessageBroadcastEvent event, String profileImage) {
        return ChatMessageResponse.builder()
                .senderName(event.senderNickname())
                .senderId(event.senderId())
                .profileImageUrl(profileImage)
                .content(event.message().getContent())
                .type(event.message().getType())
                .createdAt(event.message().getCreatedAt())
                .language(event.message().getCodeLanguage())
                .chatImageUrl(event.message().getChatImage() != null
                        ? event.message().getChatImage().getStoreFileName() : null)
                .messageId(event.message().getId())
                .status(event.message().getStatus())
                .build();
    }

    // ChatMessage에 aiReview FK만 연결하는 단순한 엔티티 생성
    public ChatMessage toAiReviewMessageEntity(AiReview aiReview, Member githubBot, ChatRoom room) {
        return ChatMessage.builder()
                .chatRoom(room)
                .sender(githubBot)
                .content("AI 코드 리뷰")
                .prNumber(aiReview.getPrNumber())
                .aiReview(aiReview)
                .type(MessageType.AI_REVIEW)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public ChatMessageResponse toAiReviewResponse(ChatMessage message) {
        AiReview aiReview = message.getAiReview();
        return ChatMessageResponse.builder()
                .senderName("AI 리뷰봇")
                .content(message.getContent())
                .type(message.getType())
                .createdAt(message.getCreatedAt())
                .messageId(message.getId())
                .profileImageUrl(githubProfile)
                .prNumber(message.getPrNumber())
                .aiReviewId(aiReview != null ? aiReview.getId() : null)
                .aiReviewStatus(aiReview != null ? aiReview.getStatus().name() : null)
                .build();
    }
}