package project.backend.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.aireview.entity.AiReview;

import java.util.Optional;

public interface AiReviewRepository extends JpaRepository<AiReview, Long> {

    Optional<AiReview> findByChatRoom_IdAndPrNumber(Long roomId, Integer prNumber);

    void deleteByChatRoom_Id(Long roomId);
}