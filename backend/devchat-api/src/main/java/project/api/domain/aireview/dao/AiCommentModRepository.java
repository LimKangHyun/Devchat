package project.api.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.aireview.entity.AiCommentMod;
import java.util.Collection;
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

    /** 증분 리뷰: 재사용할 파일(keepPaths, 비어 있으면 안 됨)을 뺀 나머지 코멘트의 상태 이력을 지운다 */
    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM AiCommentMod m WHERE m.comment.id IN (
            SELECT c.id FROM AiReviewComment c WHERE c.aiReview.id = :aiReviewId AND c.filePath NOT IN :keepPaths
        )
    """)
    int deleteByAiReviewIdExceptFiles(@Param("aiReviewId") Long aiReviewId,
                                      @Param("keepPaths") Collection<String> keepPaths);
}
