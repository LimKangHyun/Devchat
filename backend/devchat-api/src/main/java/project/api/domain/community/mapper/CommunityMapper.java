package project.api.domain.community.mapper;

import project.api.domain.chat.chatroom.event.JoinChatRoomEvent;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.community.dto.PostCreateRequest;
import project.api.domain.community.dto.event.ApplicantResultEvent;
import project.api.domain.community.dto.event.ApplyEvent;
import project.api.domain.community.entity.Applicant;
import project.api.domain.community.entity.Post;
import project.api.domain.member.entity.Member;

import java.time.LocalDateTime;

public class CommunityMapper {

    public static Post toPost(PostCreateRequest request, Member author, ChatRoom chatRoom) {
        return Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .maxCount(request.getMaxCount())
                .tag(request.getTag())
                .mode(request.getMode())
                .deadline(request.getDeadline())
                .techStacks(request.getTechStacks() != null
                        ? String.join(",", request.getTechStacks())
                        : null)
                .author(author)
                .chatRoom(chatRoom)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static ApplyEvent toApplyEvent(Post post, Member applicant) {
        return new ApplyEvent(
                post.getAuthor().getId(),
                post.getAuthor().getUsername(),
                applicant.getId(),
                applicant.getNickname(),
                post.getId(),
                post.getTitle()
        );
    }

    public static ApplicantResultEvent toApplicantResultEvent(Post post, Applicant applicant, boolean approved) {
        return new ApplicantResultEvent(
                applicant.getMember().getId(),
                applicant.getMember().getUsername(),
                post.getAuthor().getId(),
                post.getId(),
                post.getTitle(),
                approved
        );
    }

    public static JoinChatRoomEvent toJoinChatRoomEvent(Post post, Member member, ChatMessage savedMessage) {
        return new JoinChatRoomEvent(
                post.getChatRoom().getId(),
                member.getId(),
                member.getNickname(),
                savedMessage.getId(),
                savedMessage.getCreatedAt()
        );
    }
}