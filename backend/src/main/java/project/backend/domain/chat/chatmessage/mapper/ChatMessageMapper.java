package project.backend.domain.chat.chatmessage.mapper;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.ChatMessageSearch;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatroom.dto.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.github.dto.GitMessageDto;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.member.entity.Member;

@Component
public class ChatMessageMapper {

    public ChatMessage toEntityWithText(ChatRoom room, Member sender,
        ChatMessageRequest request, Long sequence) {
        return ChatMessage.builder()
            .chatRoom(room)
            .sender(sender)
            .content(request.getContent())
            .type(MessageType.TEXT)
            .sendAt(LocalDateTime.now())
            .sequence(sequence)
            .build();
    }

    public ChatMessage toEntityWithCode(ChatRoom room, Member sender,
        ChatMessageRequest request, Long sequence) {
        return ChatMessage.builder()
            .chatRoom(room)
            .sender(sender)
            .content(request.getContent())
            .type(MessageType.CODE)
            .sendAt(LocalDateTime.now())
            .codeLanguage(request.getLanguage())
            .sequence(sequence)
            .build();
    }

    public ChatMessage toEntityWithImage(ChatRoom room, Member sender,
        ImageFile chatImage, Long sequence) {
        return ChatMessage.builder()
            .chatRoom(room)
            .sender(sender)
            .type(MessageType.IMAGE)
            .sendAt(LocalDateTime.now())
            .chatImage(chatImage)
            .sequence(sequence)
            .build();
    }

    public ChatMessage toEntityWithGit(GitMessageDto gitMessage, Member githubBot, Long sequence) {
        return ChatMessage.builder()
            .chatRoom(gitMessage.getRoom())
            .type(MessageType.GIT)
            .content(gitMessage.getContent())
            .sendAt(LocalDateTime.now())
            .sender(githubBot)
            .sequence(sequence)
            .build();
    }

    public ChatMessage toEntityWithJoinEvent(ChatRoom room, Member sender,
        LocalDateTime now, Long sequence) {
        return ChatMessage.builder()
            .chatRoom(room)
            .sender(sender)
            .content(sender.getNickname() + "님이 입장했습니다.")
            .type(MessageType.EVENT)
            .sendAt(now)
            .sequence(sequence)
            .build();
    }

    public ChatMessage toEntityWithLeaveEvent(ChatRoom room, Member sender,
        LeaveChatRoomEvent leaveEvent, Long sequence) {
        return ChatMessage.builder()
            .chatRoom(room)
            .sender(sender)
            .content(leaveEvent.nickname() + "님이 나갔습니다.")
            .type(MessageType.EVENT)
            .sendAt(leaveEvent.leaveAt())
            .sequence(sequence)
            .build();
    }

    // 저장된 메시지에서 ID, roomId, content만 꺼내서 저장하므로 ChatMessage 사용
    public ChatMessageSearch toSearchEntity(ChatMessage message) {
        return ChatMessageSearch.builder()
            .id(message.getId())
            .roomId(message.getChatRoom().getId())
            .content(message.getContent())
            .build();
    }

    public ChatMessageResponse toResponse(ChatMessage message) {
        String senderName = message.getSender().getNickname();

        return ChatMessageResponse.builder()
            .senderName(senderName)
            .content(message.getContent())
            .type(message.getType())
            .sendAt(message.getSendAt())
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
            .sequence(message.getSequence())
            .build();
    }

    public ChatMessageResponse toResponse(ChatMessage message, MemberDetails memberDetails, String profileImage) {
        return ChatMessageResponse.builder()
            .senderName(memberDetails.getNickname())
            .content(message.getContent())
            .type(message.getType())
            .sendAt(message.getSendAt())
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
            .sequence(message.getSequence())
            .build();
    }

    public ChatMessageSearchResponse toSearchResponse(ChatMessage message) {
        return ChatMessageSearchResponse.builder()
            .messageId(message.getId())
            .content(message.getContent())
            .senderName(message.getSender().getNickname())
            .profileImageUrl(message.getSender().getProfileImage())
            .sendAt(message.getSendAt())
            .type(message.getType())
            .build();
    }

    @Value("${file.images.profile.github}")
    private String githubProfile;

    public ChatMessageResponse toGitResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
            .senderName("깃허브봇")
            .content(message.getContent())
            .type(message.getType())
            .sendAt(message.getSendAt())
            .messageId(message.getId())
            .profileImageUrl(githubProfile)
            .build();
    }

}
