package project.api.domain.aireview.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.aireview.app.AiReviewDiffParser;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.aireview.entity.AiReviewStatus;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.github.client.GitHubBotClient;
import project.api.global.outbox.OutboxEvent;
import project.api.global.outbox.OutboxEventType;
import project.api.global.outbox.OutboxService;
import project.common.message.aireview.AiReviewRequestMessage;

class AiReviewPrepareHandlerTest {

    AiReviewService aiReviewService = mock(AiReviewService.class);
    AiReviewDiffParser diffParser = mock(AiReviewDiffParser.class);
    GitHubBotClient gitHub = mock(GitHubBotClient.class);
    AiReviewOutbox aiReviewOutbox = mock(AiReviewOutbox.class);
    OutboxService outboxService = mock(OutboxService.class);
    AiReviewPrepareHandler handler = new AiReviewPrepareHandler(
            aiReviewService, diffParser, gitHub, aiReviewOutbox, outboxService, new SimpleMeterRegistry());

    AiReview review;
    OutboxEvent event = OutboxEvent.pending(OutboxEventType.AI_REVIEW_PREPARE, 1L, "{}", LocalDateTime.now());

    @BeforeEach
    void setUp() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(9L);
        given(room.getRepositoryUrl()).willReturn("https://github.com/owner/repo");
        review = AiReview.builder().prNumber(3).commitSha("head").status(AiReviewStatus.PENDING)
                .prDiff("diff").chatRoom(room).build();
        ReflectionTestUtils.setField(review, "id", 1L);
        given(aiReviewService.findByIdWithChatRoom(1L)).willReturn(review);
    }

    private void payload(Set<String> reused) {
        given(outboxService.readPayload(event, AiReviewPreparePayload.class))
                .willReturn(new AiReviewPreparePayload(1L, "head", "base", reused));
    }

    @Test
    @DisplayName("다시 리뷰할 파일만 요청을 만들고, 총 파일 수·요청을 한 번에 저장한다")
    void preparesOnlyChangedFiles() {
        payload(Set.of("B.java"));
        Map<String, String> diffs = new LinkedHashMap<>();
        diffs.put("A.java", "+a");
        diffs.put("B.java", "+b");
        diffs.put("README.md", "+r");
        given(diffParser.parseFileDiffs("diff")).willReturn(diffs);
        given(gitHub.getPrFileStatuses("owner", "repo", 3)).willReturn(Map.of("A.java", "modified", "B.java", "modified"));
        given(gitHub.getFileContent(eq("owner"), eq("repo"), anyString(), anyString())).willReturn("class X {}");

        handler.handle(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiReviewRequestMessage>> requests = ArgumentCaptor.forClass(List.class);
        verify(aiReviewOutbox).savePrepared(eq(1L), requests.capture(), any(), eq(Set.of("B.java")));
        assertThat(requests.getValue()).extracting(AiReviewRequestMessage::filePath).containsExactly("A.java");
        assertThat(requests.getValue().get(0).commitSha()).isEqualTo("head");
        assertThat(requests.getValue().get(0).changedFilesInPr()).containsExactly("A.java", "B.java");
    }

    @Test
    @DisplayName("이미 준비한 리뷰면(총 파일 수가 있음) 다시 처리돼도 요청을 또 만들지 않는다")
    void skipWhenAlreadyPrepared() {
        payload(Set.of());
        ReflectionTestUtils.setField(review, "totalFiles", 2);

        handler.handle(event);

        verifyNoInteractions(gitHub, aiReviewOutbox);
    }

    @Test
    @DisplayName("그사이 새 커밋으로 리셋됐으면 예전 커밋의 준비는 버린다")
    void skipWhenStaleCommit() {
        payload(Set.of());
        ReflectionTestUtils.setField(review, "commitSha", "newer");

        handler.handle(event);

        verifyNoInteractions(gitHub, aiReviewOutbox);
    }

    @Test
    @DisplayName("다시 리뷰할 파일이 없으면 요청 없이 바로 끝낸다")
    void completeWhenNothingToReview() {
        payload(Set.of("A.java"));
        given(diffParser.parseFileDiffs("diff")).willReturn(Map.of("A.java", "+a"));
        given(gitHub.getPrFileStatuses("owner", "repo", 3)).willReturn(Map.of("A.java", "modified"));
        given(gitHub.getFileContent(eq("owner"), eq("repo"), anyString(), anyString())).willReturn("class A {}");

        handler.handle(event);

        verify(aiReviewService).completeWithoutNewFiles(1L, true);
        verify(aiReviewOutbox, never()).savePrepared(any(), any(), any(), any());
    }

    @Test
    @DisplayName("끝내 준비하지 못하면 리뷰를 실패로 끝낸다")
    void giveUpFailsReview() {
        payload(Set.of());

        handler.onGiveUp(event, "timeout");

        verify(aiReviewService).failPendingReview(eq(1L), anyString());
    }
}
