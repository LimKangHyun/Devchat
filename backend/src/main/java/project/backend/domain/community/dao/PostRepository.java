package project.backend.domain.community.dao;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.community.entity.Post;

import java.time.LocalDate;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 최신순
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC")
    Slice<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 인기순 (조회수)
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.viewCount DESC")
    Slice<Post> findAllByOrderByViewCountDesc(Pageable pageable);

    // 최신순 (모집중만: 수동마감 X, 정원 안 찬 것, 기간 안 지난 것)
    @Query("""
        SELECT p FROM Post p JOIN FETCH p.author
        WHERE p.closed = false
          AND (p.maxCount IS NULL OR p.currentCount < p.maxCount)
          AND (p.deadline IS NULL OR p.deadline >= :today)
        ORDER BY p.createdAt DESC
        """)
    Slice<Post> findActiveByOrderByCreatedAtDesc(Pageable pageable, LocalDate today);

    // 인기순 (모집중만)
    @Query("""
        SELECT p FROM Post p JOIN FETCH p.author
        WHERE p.closed = false
          AND (p.maxCount IS NULL OR p.currentCount < p.maxCount)
          AND (p.deadline IS NULL OR p.deadline >= :today)
        ORDER BY p.viewCount DESC
        """)
    Slice<Post> findActiveByOrderByViewCountDesc(Pageable pageable, LocalDate today);

    @Query("""
        SELECT p FROM Post p JOIN FETCH p.author
        JOIN Applicant a ON a.post = p
        WHERE a.member.id = :memberId
          AND a.status = 'PENDING'
        ORDER BY a.appliedAt DESC
        """)
    Slice<Post> findAppliedByMember(@Param("memberId") Long memberId, Pageable pageable);

    boolean existsByChatRoom_Id(Long chatRoomId);

    void deleteByChatRoom_Id(Long roomId);
}