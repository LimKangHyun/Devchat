package project.api.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.aireview.entity.AiReviewFile;

public interface AiReviewFileRepository extends JpaRepository<AiReviewFile, Long> {

    void deleteByAiReview_Id(Long aiReviewId);

    void deleteByAiReview_ChatRoom_Id(Long roomId);
}