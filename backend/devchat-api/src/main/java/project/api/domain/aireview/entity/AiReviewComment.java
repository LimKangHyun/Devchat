package project.api.domain.aireview.entity;

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
    private Integer diffLine;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private LocalDateTime createdAt;

    /** 이 코멘트를 만든 프롬프트 버전(프롬프트·모델 해시 앞 8자리). 프롬프트별 채택률 비교용. 예전 코멘트는 null */
    @Column(length = 20)
    private String promptVersion;
}