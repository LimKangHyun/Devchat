package project.api.domain.aireview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.aireview.entity.AiReviewStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiReviewRepository extends JpaRepository<AiReview, Long> {

    Optional<AiReview> findByChatRoom_IdAndPrNumber(Long roomId, Integer prNumber);

    void deleteByChatRoom_Id(Long roomId);

    boolean existsByChatRoom_IdAndPrNumber(Long roomId, int prNumber);

    @Query("SELECT a FROM AiReview a JOIN FETCH a.chatRoom WHERE a.id = :id")
    Optional<AiReview> findByIdWithChatRoom(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AiReview a SET a.status = :status, a.updatedAt = :now " +
            "WHERE a.id = :id AND a.status = 'PENDING'")
    int markFinalStatusIfPending(@Param("id") Long id,
                                 @Param("status") AiReviewStatus status,
                                 @Param("now") LocalDateTime now);
}