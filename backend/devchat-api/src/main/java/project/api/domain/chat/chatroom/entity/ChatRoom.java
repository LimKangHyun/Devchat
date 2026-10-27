package project.api.domain.chat.chatroom.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.common.enums.IndexingStatus;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    private LocalDateTime createdAt;

    @Column(unique = true)
    private String repositoryUrl;

    private String inviteCode;

    private Long webhookId;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatParticipant> participants = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexingStatus indexingStatus = IndexingStatus.NONE;

    @Column(nullable = false)
    private boolean aiReviewEnabled = true;

    @Column(nullable = false)
    private boolean aiSummaryEnabled = true;

    /** 팀 리뷰 규칙. AI 리뷰 프롬프트에 함께 들어가며, 방장만 수정한다. 없으면 null. */
    @Column(columnDefinition = "TEXT")
    private String reviewConventions;

    @Builder
    public ChatRoom(String name, LocalDateTime createdAt, String repositoryUrl, String inviteCode,
        Long webhookId, List<ChatMessage> messages, List<ChatParticipant> participants) {
        this.name = name;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.repositoryUrl = repositoryUrl;
        this.inviteCode = inviteCode;
        this.webhookId = webhookId;
        if (messages != null) {
            this.messages = messages;
        }
        if (participants != null) {
            this.participants = participants;
        }
    }

    public void addParticipant(ChatParticipant chatParticipant) {
        participants.add(chatParticipant);
    }

    public int getActiveParticipantCount() {
        return (int) participants.stream()
            .filter(ChatParticipant::isActive)
            .count();
    }

    public void connectRepository(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public void updateWebhookId(Long webhookId) {
        this.webhookId = webhookId;
    }

    public void toggleAiReview() {
        this.aiReviewEnabled = !this.aiReviewEnabled;
    }

    /** 앞뒤 공백을 지우고, 비어 있으면 규칙이 없는 것으로 본다. */
    public void updateReviewConventions(String conventions) {
        this.reviewConventions = conventions == null || conventions.isBlank() ? null : conventions.strip();
    }

    public void updateIndexingStatus(IndexingStatus status) {
        this.indexingStatus = status;
    }

    public void toggleAiSummary() {
        this.aiSummaryEnabled = !this.aiSummaryEnabled;
    }
}