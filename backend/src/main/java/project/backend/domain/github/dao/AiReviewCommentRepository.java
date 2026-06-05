package project.backend.domain.github.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.github.entity.AiReviewComment;
import java.util.List;

public interface AiReviewCommentRepository extends JpaRepository<AiReviewComment, Long> {
    List<AiReviewComment> findByAiReview_Id(Long aiReviewId);
    List<AiReviewComment> findByAiReview_IdAndFilePath(Long aiReviewId, String filePath);
}