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
