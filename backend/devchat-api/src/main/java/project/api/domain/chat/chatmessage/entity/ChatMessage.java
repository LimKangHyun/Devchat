package project.api.domain.chat.chatmessage.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.imagefile.ImageFile;
import project.api.domain.member.entity.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member sender;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    private String codeLanguage;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "chat_image_id")
    private ImageFile chatImage;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.NO_CHANGE;

    private Integer prNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_review_id")
    private AiReview aiReview;

    /** 보낸 브라우저가 붙인 임시 id. 다시 보내기를 중복 저장하지 않고, 화면의 "전송 중" 메시지와 짝을 맞춘다 */
    @Column(name = "client_message_id", length = 36)
    private String clientMessageId;

    public void assignClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    /*
     * 답장: 원문 id(원문으로 이동할 때)와 답장한 순간의 원문 모습.
     * 원문을 나중에 고치거나 지워도 바꾸지 않는다 (그 내용에 답한 것이므로).
     */
    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "reply_to_sender_id")
    private Long replyToSenderId;

    @Column(name = "reply_to_sender_name", length = 50)
    private String replyToSenderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_to_type", length = 20)
    private MessageType replyToType;

    @Column(name = "reply_to_preview", length = 120)
    private String replyToPreview;

    public void replyTo(ChatMessage original, String preview) {
        this.replyToId = original.getId();
        this.replyToSenderId = original.getSender() != null ? original.getSender().getId() : null;
        this.replyToSenderName = original.getSender() != null ? original.getSender().getNickname() : null;
        this.replyToType = original.getType();
        this.replyToPreview = preview;
    }


    public Long getChatRoomId() {
        return chatRoom.getId();
    }

    public void updateContent(String newContent) {
        if (newContent != null) {
            content = newContent;
            status = MessageStatus.EDITED;
        }
    }

    public void updateLanguage(String language) {
        if (language != null) {
            codeLanguage = language;
            status = MessageStatus.EDITED;
        }
    }

    public void delete() {
        status = MessageStatus.DELETED;
    }

    public void updateToSummary(String summarizedContent) {
        if (summarizedContent != null && !summarizedContent.isBlank()) {
            this.content = this.content + summarizedContent;
        }
    }
}
