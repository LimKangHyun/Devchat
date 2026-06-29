package project.backend.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.aireview.entity.AiCommentMod;
import java.util.List;
import java.util.Optional;

public interface AiCommentModRepository extends JpaRepository<AiCommentMod, Long> {

    // 특정 코멘트의 최신 상태 조회
    Optional<AiCommentMod> findTopByComment_IdOrderByCreatedAtDesc(Long commentId);

    // 특정 aiReview의 모든 코멘트 최신 상태 조회
    @Query("""
        SELECT s FROM AiCommentMod s
        WHERE s.comment.id IN (
            SELECT c.id FROM AiReviewComment c WHERE c.aiReview.id = :aiReviewId
        )
        AND s.createdAt = (
            SELECT MAX(s2.createdAt) FROM AiCommentMod s2 WHERE s2.comment.id = s.comment.id
        )
    """)
    List<AiCommentMod> findLatestStatusesByAiReviewId(@Param("aiReviewId") Long aiReviewId);

    void deleteByComment_AiReview_ChatRoom_Id(Long roomId);

    void deleteByComment_AiReview_Id(Long aiReviewId);
}