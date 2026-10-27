package project.api.domain.github.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.github.event.DefaultBranchPushedEvent;
import project.api.domain.github.event.PrMergedEvent;
import project.api.domain.github.mapper.GitMessageMapper;

/** 재인덱싱은 기본 브랜치에 들어온 변경만 대상으로 한다. 처음 인덱싱이 기본 브랜치 기준이기 때문이다. */
@ExtendWith(MockitoExtension.class)
class GitMessageServiceReindexTest {

    private static final long ROOM_ID = 5L;

    @InjectMocks private GitMessageService gitMessageService;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private AiReviewService aiReviewService;
    @Mock private GitMessageMapper gitMessageMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gitMessageService, "githubBotUsername", "GitHubBot");
        ReflectionTestUtils.setField(gitMessageService, "aiReviewBotUsername", "ReviewBot");
        ChatRoom room = ChatRoom.builder().name("room").repositoryUrl("https://github.com/o/r").build();
        ReflectionTestUtils.setField(room, "id", ROOM_ID); // 저장하지 않은 엔티티라 ID를 직접 넣는다
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
    }

    private static Map<String, Object> mergedPr(String baseRef) {
        Map<String, Object> pr = new HashMap<>();
        pr.put("number", 12);
        pr.put("merged", true);
        pr.put("base", Map.of("ref", baseRef));
        pr.put("head", Map.of("sha", "head-sha"));
        pr.put("merge_commit_sha", "merge-sha");
        return Map.of("action", "closed", "pull_request", pr, "repository", Map.of("default_branch", "main"));
    }

    private static Map<String, Object> push(String ref) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ref", ref);
        payload.put("before", "aaa111");
        payload.put("after", "bbb222");
        payload.put("created", false);
        payload.put("deleted", false);
        payload.put("repository", Map.of("default_branch", "main"));
        return payload;
    }

    @Test
    @DisplayName("기본 브랜치로 머지된 PR은 머지 결과 커밋 기준으로 재인덱싱한다")
    void mergedIntoDefault_publishesWithMergeCommit() {
        gitMessageService.handleEvent(ROOM_ID, "pull_request", mergedPr("main"));

        then(eventPublisher).should().publishEvent(new PrMergedEvent(ROOM_ID, 12, "merge-sha"));
    }

    @Test
    @DisplayName("다른 브랜치로 머지된 PR은 재인덱싱하지 않는다")
    void mergedIntoOtherBranch_skips() {
        gitMessageService.handleEvent(ROOM_ID, "pull_request", mergedPr("dev"));

        then(eventPublisher).should(never()).publishEvent(any(PrMergedEvent.class));
    }

    @Test
    @DisplayName("기본 브랜치 push는 재인덱싱 이벤트를 발행하고, 채팅 메시지는 만들지 않는다")
    void pushToDefault_publishesReindex() {
        gitMessageService.handleEvent(ROOM_ID, "push", push("refs/heads/main"));

        then(eventPublisher).should().publishEvent(new DefaultBranchPushedEvent(ROOM_ID, "aaa111", "bbb222"));
        then(gitMessageMapper).should(never()).fromPush(any());
    }

    @Test
    @DisplayName("다른 브랜치 push와 기본 브랜치를 새로 만든 push는 재인덱싱하지 않는다")
    void pushToOtherBranchOrCreated_skips() {
        gitMessageService.handleEvent(ROOM_ID, "push", push("refs/heads/feature/x"));
        Map<String, Object> created = push("refs/heads/main");
        created.put("created", true);
        created.put("before", "0000000000000000000000000000000000000000");
        gitMessageService.handleEvent(ROOM_ID, "push", created);

        then(eventPublisher).should(never()).publishEvent(any(DefaultBranchPushedEvent.class));
    }
}
