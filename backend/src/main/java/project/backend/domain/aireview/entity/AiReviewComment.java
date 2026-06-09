package project.backend.domain.aireview.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_review_id")
    private AiReview aiReview;

    private String filePath;
    private Integer lineNumber;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private LocalDateTime createdAt;
}