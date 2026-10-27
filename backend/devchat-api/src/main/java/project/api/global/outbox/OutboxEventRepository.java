package project.api.global.outbox;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 보낼 차례인 행을 잠근다. SKIP LOCKED라 블루/그린 전환 중 두 서버가 함께 돌아도 같은 행을 가져가지 않는다.
     * 잠금은 호출한 트랜잭션이 끝날 때 풀리므로, 그 안에서 lease(다음 시도 시각을 미룸)를 건다.
     * 정렬은 인덱스 (status, next_attempt_at, id) 순서를 따라야 한다. ORDER BY id만 쓰면 정렬하려고 조건에 맞는 행을
     * 모두 읽어 잠가서, LIMIT과 상관없이 한 릴레이가 전부 잠그고 다른 릴레이는 아무것도 못 가져간다.
     */
    @Query(value = """
        SELECT * FROM outbox_event
        WHERE status = 'PENDING' AND next_attempt_at <= :now
        ORDER BY next_attempt_at, id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    long countByStatus(OutboxStatus status);

    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status = :status")
    LocalDateTime findOldestCreatedAt(@Param("status") OutboxStatus status);

    @Modifying
    @Query(value = "DELETE FROM outbox_event WHERE status = 'SENT' AND sent_at < :before LIMIT 1000", nativeQuery = true)
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
