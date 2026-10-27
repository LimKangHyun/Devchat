package project.api.domain.aireview.dao;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.aireview.entity.AiReviewComment;

public interface AiReviewCommentRepository extends JpaRepository<AiReviewComment, Long> {
    List<AiReviewComment> findByAiReview_Id(Long aiReviewId);

    void deleteByAiReview_Id(Long aiReviewId);

    void deleteByAiReview_ChatRoom_Id(Long roomId);

    /** 증분 리뷰: 재사용할 파일(keepPaths, 비어 있으면 안 됨)을 뺀 나머지 코멘트를 지운다 */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AiReviewComment c WHERE c.aiReview.id = :aiReviewId AND c.filePath NOT IN :keepPaths")
    int deleteByAiReviewIdExceptFiles(@Param("aiReviewId") Long aiReviewId,
                                      @Param("keepPaths") Collection<String> keepPaths);
}
