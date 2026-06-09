package project.backend.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.aireview.entity.AiReviewComment;
import java.util.List;

public interface AiReviewCommentRepository extends JpaRepository<AiReviewComment, Long> {
    List<AiReviewComment> findByAiReview_Id(Long aiReviewId);

    List<AiReviewComment> findByAiReview_IdAndFilePath(Long aiReviewId, String filePath);

    void deleteByAiReview_Id(Long aiReviewId);

    void deleteByAiReview_ChatRoom_Id(Long roomId);
}