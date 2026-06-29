package project.backend.domain.aireview.entity;

import jakarta.persistence.*;
import lombok.*;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    private Integer prNumber;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String prTitle;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String prBody;

    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AiReviewStatus status = AiReviewStatus.PENDING;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reviewJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String prDiff;

    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PrStatus prStatus = PrStatus.OPEN;

    @Builder.Default
    private boolean ragUsed = false;

    @Builder.Default
    private boolean githubPublished = false;

    private String publishedBy;

    private int totalFiles;
    private int completedFiles;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateSuccess(String reviewJson) {
        this.status = AiReviewStatus.SUCCESS;
        this.reviewJson = reviewJson;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFail(String errorMessage) {
        this.status = AiReviewStatus.FAIL;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSkipped(String reason) {
        this.status = AiReviewStatus.SKIPPED;
        this.errorMessage = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void resetToPending(String commitSha, String prDiff) {
        this.status = AiReviewStatus.PENDING;
        this.commitSha = commitSha;
        this.prDiff = prDiff;
        this.reviewJson = null;
        this.errorMessage = null;
        this.githubPublished = false;
        this.publishedBy = null;
        this.completedFiles = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsPublished(String username) {
        this.githubPublished = true;
        this.publishedBy = username;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePrStatus(PrStatus prStatus) {
        this.prStatus = prStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePrInfo(String prTitle, String prBody) {
        this.prTitle = prTitle;
        this.prBody = prBody;
    }

    public void markRagUsed() {
        this.ragUsed = true;
    }


    public void incrementCompletedFiles() {
        this.completedFiles++;
    }

    public boolean isAllFilesCompleted() {
        return this.totalFiles > 0 && this.completedFiles >= this.totalFiles;
    }

    public void updateSuccess() {
        this.status = AiReviewStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }
}