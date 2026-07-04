package project.api.domain.chat.chatmessage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChatMessageIndexStatus {

    @Id
    private Long messageId;

    @Column(nullable = false)
    private Long roomId;
}