package project.api.domain.aireview.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.aireview.dao.AiCommentModRepository;
import project.api.domain.aireview.dao.AiReviewCommentRepository;
import project.api.domain.aireview.dao.AiReviewFileRepository;
import project.api.domain.aireview.dao.AiReviewRepository;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.aireview.entity.FileReviewStatus;
import project.common.dto.InlineReview;
import project.common.message.aireview.FileClaimResult;

/** 파일 리뷰 처리 권한(claim)과 결과 저장이 중복·늦은 메시지에도 한 번만 반영되는지. */
@ExtendWith(MockitoExtension.class)
class AiReviewServiceIdempotencyTest {

    private static final long REVIEW_ID = 1L;
    private static final long ROOM_ID = 9L;
    private static final String FILE = "A.java";

    @InjectMocks private AiReviewService service;

    @Mock private AiReviewRepository aiReviewRepository;
    @Mock private AiReviewFileRepository aiReviewFileRepository;
    @Mock private AiReviewCommentRepository aiReviewCommentRepository;
    @Mock private AiCommentModRepository aiCommentModRepository;

    private AiReview review;

    @BeforeEach
    void setUp() {
        review = AiReview.builder().prNumber(3).commitSha("sha-new").build();
        ReflectionTestUtils.setField(review, "id", REVIEW_ID);
        ReflectionTestUtils.setField(review, "totalFiles", 2); // 이 파일 하나로는 전체 완료가 되지 않게
        given(aiReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
    }

    @Test
    @DisplayName("예전 커밋의 요청은 처리 권한을 주지 않는다")
    void claim_staleCommit() {
        assertThat(service.claimFile(REVIEW_ID, FILE, "sha-old", false)).isEqualTo(FileClaimResult.STALE_COMMIT);
        then(aiReviewFileRepository).should(never()).insertIfAbsent(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("처음 요청하면 PROCESSING 행을 만들고 처리 권한을 준다")
    void claim_first() {
        given(aiReviewFileRepository.insertIfAbsent(eq(REVIEW_ID), eq(FILE), eq("PROCESSING"), any())).willReturn(1);

        assertThat(service.claimFile(REVIEW_ID, FILE, "sha-new", false)).isEqualTo(FileClaimResult.CLAIMED);
    }

    @Test
    @DisplayName("이미 다른 Worker가 처리 중이면 일반 요청에는 권한을 주지 않는다")
    void claim_alreadyTaken() {
        given(aiReviewFileRepository.insertIfAbsent(eq(REVIEW_ID), eq(FILE), eq("PROCESSING"), any())).willReturn(0);

        assertThat(service.claimFile(REVIEW_ID, FILE, "sha-new", false)).isEqualTo(FileClaimResult.ALREADY_TAKEN);
        then(aiReviewFileRepository).should(never()).takeOverProcessing(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("재처리 요청(앞 Worker Heartbeat 만료)이면 PROCESSING으로 남은 파일의 권한을 넘겨받는다")
    void claim_reclaim() {
        given(aiReviewFileRepository.insertIfAbsent(eq(REVIEW_ID), eq(FILE), eq("PROCESSING"), any())).willReturn(0);
        given(aiReviewFileRepository.takeOverProcessing(eq(REVIEW_ID), eq(FILE), any())).willReturn(1);

        assertThat(service.claimFile(REVIEW_ID, FILE, "sha-new", true)).isEqualTo(FileClaimResult.CLAIMED);
    }

    @Test
    @DisplayName("PROCESSING을 처음 최종 상태로 바꾼 결과만 코멘트를 저장하고 완료 수를 늘린다")
    void saveResult_first() {
        given(aiReviewFileRepository.updateStatusIfProcessing(REVIEW_ID, FILE, FileReviewStatus.COMPLETED)).willReturn(1);

        service.saveFileReviewResult(REVIEW_ID, ROOM_ID, FILE, "sha-new", List.of(new InlineReview(3, 3, "comment")));

        then(aiReviewCommentRepository).should().save(any());
        assertThat(review.getCompletedFiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번째로 도착한 같은 파일 결과는 아무것도 저장하지 않는다")
    void saveResult_duplicate() {
        given(aiReviewFileRepository.updateStatusIfProcessing(REVIEW_ID, FILE, FileReviewStatus.COMPLETED)).willReturn(0);
        given(aiReviewFileRepository.insertIfAbsent(eq(REVIEW_ID), eq(FILE), eq("COMPLETED"), any())).willReturn(0);

        service.saveFileReviewResult(REVIEW_ID, ROOM_ID, FILE, "sha-new", List.of(new InlineReview(3, 3, "comment")));

        then(aiReviewCommentRepository).should(never()).save(any());
        assertThat(review.getCompletedFiles()).isZero();
    }

    @Test
    @DisplayName("새 커밋으로 다시 시작된 뒤 도착한 예전 커밋의 결과는 버린다")
    void saveResult_staleCommit() {
        service.saveFileReviewResult(REVIEW_ID, ROOM_ID, FILE, "sha-old", List.of(new InlineReview(3, 3, "comment")));

        then(aiReviewFileRepository).should(never()).updateStatusIfProcessing(anyLong(), anyString(), any());
        then(aiReviewCommentRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("claim 없이 처리한 이전 버전 AI 서버의 결과도(행이 없으면) 한 번 반영한다")
    void saveResult_legacyWithoutClaim() {
        given(aiReviewFileRepository.updateStatusIfProcessing(REVIEW_ID, FILE, FileReviewStatus.FAILED)).willReturn(0);
        given(aiReviewFileRepository.insertIfAbsent(eq(REVIEW_ID), eq(FILE), eq("FAILED"), any())).willReturn(1);

        service.saveFileReviewFail(REVIEW_ID, ROOM_ID, FILE, null, "boom");

        assertThat(review.getCompletedFiles()).isEqualTo(1);
        assertThat(review.getFailedFiles()).isEqualTo(1);
    }
}
