package project.backend.domain.github.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.github.entity.AiReview;

import java.util.Optional;

public interface AiReviewRepository extends JpaRepository<AiReview, Long> {

    Optional<AiReview> findByChatRoom_IdAndPrNumber(Long roomId, Integer prNumber);
}