package project.api.domain.aireview.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import project.api.domain.aireview.entity.AiReviewStatus;
import project.api.domain.aireview.entity.FileReviewStatus;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;

/** 새 커밋이 오면 바뀌지 않은 파일의 리뷰 결과만 남기고 나머지를 다시 리뷰하는지. */
@ExtendWith(MockitoExtension.class)
class AiReviewIncrementalResetTest {

    private static final long ROOM_ID = 9L;
    private static final int PR = 3;

    @InjectMocks private AiReviewService service;

    @Mock private AiReviewRepository aiReviewRepository;
    @Mock private AiReviewFileRepository aiReviewFileRepository;
    @Mock private AiReviewCommentRepository aiReviewCommentRepository;
    @Mock private AiCommentModRepository aiCommentModRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private AiReviewDiffParser diffParser;

    private AiReview review;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        review = AiReview.builder().prNumber(PR).commitSha("sha-1").status(AiReviewStatus.SUCCESS).build();
        ReflectionTestUtils.setField(review, "id", 1L);
        ReflectionTestUtils.setField(review, "failedFiles", 1);
        room = mock(ChatRoom.class, org.mockito.Mockito.withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        given(room.getId()).willReturn(ROOM_ID);
        given(aiReviewRepository.findByChatRoom_IdAndPrNumber(ROOM_ID, PR)).willReturn(Optional.of(review));
    }

    @Test
    @DisplayName("끝낸 파일 중 바뀌지 않았고 PR에 남아 있는 파일만 재사용한다")
    void reuseUnchangedCompletedFiles() {
        given(aiReviewFileRepository.findFilePathsByStatus(1L, FileReviewStatus.COMPLETED))
                .willReturn(List.of("A.java", "B.java", "Gone.java"));
        // C.java는 이전에 실패(COMPLETED 아님), Gone.java는 PR에서 빠짐
        given(diffParser.parseFileDiffs("diff")).willReturn(Map.of("A.java", "", "B.java", "", "C.java", ""));

        AiReviewService.ResetResult result =
                service.resetForNewCommit(room, PR, "sha-2", "diff", Set.of("B.java"));

        assertThat(result.reusedFiles()).containsExactly("A.java");
        then(aiReviewCommentRepository).should().deleteByAiReviewIdExceptFiles(1L, Set.of("A.java"));
        then(aiReviewFileRepository).should().deleteByAiReviewIdExceptFiles(1L, Set.of("A.java"));
        then(aiCommentModRepository).should().deleteByAiReviewIdExceptFiles(1L, Set.of("A.java"));
        assertThat(review.getCommitSha()).isEqualTo("sha-2");
        assertThat(review.getStatus()).isEqualTo(AiReviewStatus.PENDING);
        assertThat(review.getFailedFiles()).isZero();
    }

    @Test
    @DisplayName("비교 결과가 없으면(null) 전체를 다시 리뷰한다")
    void fullReviewWhenNoComparison() {
        AiReviewService.ResetResult result = service.resetForNewCommit(room, PR, "sha-2", "diff", null);

        assertThat(result.reusedFiles()).isEmpty();
        then(aiReviewCommentRepository).should().deleteByAiReview_Id(1L);
        then(aiReviewFileRepository).should(never()).findFilePathsByStatus(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("리뷰가 진행 중이면 재사용할 커밋이 없다")
    void noReviewedShaWhilePending() {
        ReflectionTestUtils.setField(review, "status", AiReviewStatus.PENDING);
        assertThat(service.findReviewedCommitSha(ROOM_ID, PR)).isNull();

        ReflectionTestUtils.setField(review, "status", AiReviewStatus.FAIL);
        assertThat(service.findReviewedCommitSha(ROOM_ID, PR)).isEqualTo("sha-1");
    }
}
