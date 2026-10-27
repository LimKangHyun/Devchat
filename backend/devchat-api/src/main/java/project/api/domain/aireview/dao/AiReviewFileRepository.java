package project.api.domain.aireview.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.aireview.entity.AiReviewFile;
import project.api.domain.aireview.entity.FileReviewStatus;

public interface AiReviewFileRepository extends JpaRepository<AiReviewFile, Long> {

    void deleteByAiReview_Id(Long aiReviewId);

    void deleteByAiReview_ChatRoom_Id(Long roomId);

    /** 이 리뷰에서 리뷰를 끝낸(COMPLETED) 파일. 증분 리뷰에서 재사용 후보가 된다 */
    @Query("SELECT f.filePath FROM AiReviewFile f WHERE f.aiReview.id = :aiReviewId AND f.status = :status")
    List<String> findFilePathsByStatus(@Param("aiReviewId") Long aiReviewId, @Param("status") FileReviewStatus status);

    /** 증분 리뷰: 재사용할 파일(keepPaths, 비어 있으면 안 됨)을 뺀 나머지 파일 처리 기록을 지운다 */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AiReviewFile f WHERE f.aiReview.id = :aiReviewId AND f.filePath NOT IN :keepPaths")
    int deleteByAiReviewIdExceptFiles(@Param("aiReviewId") Long aiReviewId,
                                      @Param("keepPaths") Collection<String> keepPaths);

    /**
     * (ai_review_id, file_path) 행이 없을 때만 넣는다. 넣었으면 1, 이미 있으면 0.
     * UNIQUE 제약 위반을 예외로 받으면 JPA 트랜잭션이 롤백 전용으로 바뀌어 커밋이 실패하므로,
     * 예외 없이 영향받은 행 수로 판단한다.
     */
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO ai_review_file (ai_review_id, file_path, status, created_at)
        VALUES (:aiReviewId, :filePath, :status, :now)
        """, nativeQuery = true)
    int insertIfAbsent(@Param("aiReviewId") Long aiReviewId,
                       @Param("filePath") String filePath,
                       @Param("status") String status,
                       @Param("now") LocalDateTime now);

    /** PROCESSING일 때만 최종 상태로 바꾼다. 두 Worker가 같은 파일 결과를 보내도 한 번만 1이 된다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE AiReviewFile f SET f.status = :status
        WHERE f.aiReview.id = :aiReviewId AND f.filePath = :filePath
          AND f.status = project.api.domain.aireview.entity.FileReviewStatus.PROCESSING
        """)
    int updateStatusIfProcessing(@Param("aiReviewId") Long aiReviewId,
                                 @Param("filePath") String filePath,
                                 @Param("status") FileReviewStatus status);

    /**
     * 아직 PROCESSING인 파일의 처리 권한을 넘겨받는다. 재처리 스케줄러가 앞 Worker의 Heartbeat 만료를 확인한 뒤에만 쓴다.
     * 이미 끝난 파일(COMPLETED/FAILED)은 바뀌지 않아 0이 된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE AiReviewFile f SET f.createdAt = :now
        WHERE f.aiReview.id = :aiReviewId AND f.filePath = :filePath
          AND f.status = project.api.domain.aireview.entity.FileReviewStatus.PROCESSING
        """)
    int takeOverProcessing(@Param("aiReviewId") Long aiReviewId,
                           @Param("filePath") String filePath,
                           @Param("now") LocalDateTime now);
}
