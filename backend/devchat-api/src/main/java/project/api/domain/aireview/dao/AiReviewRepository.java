package project.api.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.api.domain.aireview.entity.AiReview;
import java.util.Optional;

public interface AiReviewRepository extends JpaRepository<AiReview, Long> {

    Optional<AiReview> findByChatRoom_IdAndPrNumber(Long roomId, Integer prNumber);

    void deleteByChatRoom_Id(Long roomId);

    boolean existsByChatRoom_IdAndPrNumber(Long roomId, int prNumber);

    @Query("SELECT a FROM AiReview a JOIN FETCH a.chatRoom WHERE a.id = :id")
    Optional<AiReview> findByIdWithChatRoom(Long id);
}