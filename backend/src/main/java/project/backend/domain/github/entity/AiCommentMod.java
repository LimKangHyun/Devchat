package project.backend.domain.github.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiCommentMod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private AiReviewComment comment;

    private boolean active;

    @Enumerated(EnumType.STRING)
    private InactiveReason reason;

    private String otherReason; // OTHER 선택 시에만 입력

    private String changedBy;
    private LocalDateTime createdAt;
}