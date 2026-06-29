package project.backend.domain.chat.chatsearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageSearch {

    @Id
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder
    public ChatMessageSearch(Long id, Long roomId, String content) {
        this.id = id;
        this.roomId = roomId;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void deleteContent() {
        this.content = "";
    }
}