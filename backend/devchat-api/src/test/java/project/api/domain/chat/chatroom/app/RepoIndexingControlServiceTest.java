package project.api.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.dto.IndexingStatusResponse;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.chat.chatroom.event.ChatRoomCreatedEvent;
import project.api.global.exception.ex.ChatRoomException;
import project.api.global.redis.RedisStreamClient;
import project.common.enums.IndexingStatus;

@ExtendWith(MockitoExtension.class)
class RepoIndexingControlServiceTest {

    private static final long ROOM_ID = 10L;
    private static final long OWNER_ID = 1L;
    private static final long MEMBER_ID = 2L;

    @InjectMocks private RepoIndexingControlService service;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomParticipantService chatRoomParticipantService;
    @Mock private RedisStreamClient redisStreamClient;
    @Mock private IndexingStatusBroadcaster broadcaster;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = ChatRoom.builder().name("room").repositoryUrl("https://github.com/o/r").build();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
    }

    @Test
    @DisplayName("방장이 진행 중인 인덱싱을 취소하면 AI 서버에 취소를 요청하고, 상태는 결과가 올 때까지 RUNNING으로 둔다")
    void cancel_running_requestsCancel() {
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        given(chatRoomParticipantService.findOwnerId(ROOM_ID)).willReturn(OWNER_ID);
        given(redisStreamClient.isIndexingInProgress(ROOM_ID)).willReturn(true);

        service.cancel(ROOM_ID, OWNER_ID);

        then(redisStreamClient).should().requestIndexingCancel(ROOM_ID);
        assertThat(room.getIndexingStatus()).isEqualTo(IndexingStatus.RUNNING);
    }

    @Test
    @DisplayName("AI 서버가 비정상 종료돼 RUNNING만 남았으면 바로 취소 처리한다")
    void cancel_staleRunning_resetsImmediately() {
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        given(chatRoomParticipantService.findOwnerId(ROOM_ID)).willReturn(OWNER_ID);
        given(redisStreamClient.isIndexingInProgress(ROOM_ID)).willReturn(false);

        service.cancel(ROOM_ID, OWNER_ID);

        assertThat(room.getIndexingStatus()).isEqualTo(IndexingStatus.NONE);
        then(redisStreamClient).should(never()).requestIndexingCancel(anyLong());
        then(broadcaster).should().send(ROOM_ID, IndexingStatusResponse.finished("CANCELLED"));
    }

    @Test
    @DisplayName("방장이 아니면 취소할 수 없다")
    void cancel_notOwner_throws() {
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        given(chatRoomParticipantService.findOwnerId(ROOM_ID)).willReturn(OWNER_ID);

        assertThatThrownBy(() -> service.cancel(ROOM_ID, MEMBER_ID)).isInstanceOf(ChatRoomException.class);
        then(redisStreamClient).should(never()).requestIndexingCancel(anyLong());
    }

    @Test
    @DisplayName("진행 중이 아니면 취소할 수 없다")
    void cancel_notRunning_throws() {
        given(chatRoomParticipantService.findOwnerId(ROOM_ID)).willReturn(OWNER_ID);

        assertThatThrownBy(() -> service.cancel(ROOM_ID, OWNER_ID)).isInstanceOf(ChatRoomException.class);
    }

    @Test
    @DisplayName("취소된 인덱싱을 다시 시작하면 RUNNING으로 바꾸고 인덱싱 요청 이벤트를 발행한다")
    void restart_fromNone_startsIndexing() {
        given(chatRoomParticipantService.findOwnerId(ROOM_ID)).willReturn(OWNER_ID);

        IndexingStatusResponse response = service.restart(ROOM_ID, OWNER_ID);

        assertThat(room.getIndexingStatus()).isEqualTo(IndexingStatus.RUNNING);
        assertThat(response.status()).isEqualTo("RUNNING");
        then(eventPublisher).should().publishEvent(argThat((Object e) ->
            e instanceof ChatRoomCreatedEvent ev && ev.roomId() == ROOM_ID && ev.ownerId() == OWNER_ID));
    }

    @Test
    @DisplayName("이미 진행 중이면 다시 시작할 수 없다")
    void restart_running_throws() {
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        given(chatRoomParticipantService.findOwnerId(ROOM_ID)).willReturn(OWNER_ID);
        given(redisStreamClient.isIndexingInProgress(ROOM_ID)).willReturn(true);

        assertThatThrownBy(() -> service.restart(ROOM_ID, OWNER_ID)).isInstanceOf(ChatRoomException.class);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("RUNNING인데 AI 서버 락과 요청 표시가 모두 없으면 조회할 때 FAILED로 정리한다")
    void getStatus_staleRunning_marksFailed() {
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        given(redisStreamClient.isIndexingInProgress(ROOM_ID)).willReturn(false);

        IndexingStatusResponse response = service.getStatus(ROOM_ID, MEMBER_ID);

        assertThat(room.getIndexingStatus()).isEqualTo(IndexingStatus.FAILED);
        assertThat(response.status()).isEqualTo("FAILED");
        then(broadcaster).should().send(ROOM_ID, IndexingStatusResponse.finished("FAILED"));
    }

    @Test
    @DisplayName("진행 중이면 AI 서버가 기록한 단계와 진행률을 함께 준다")
    void getStatus_running_includesProgress() {
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        given(redisStreamClient.isIndexingInProgress(ROOM_ID)).willReturn(true);
        given(redisStreamClient.findIndexingProgress(ROOM_ID))
            .willReturn(Map.of("phase", "EMBEDDING", "done", "40", "total", "120"));

        IndexingStatusResponse response = service.getStatus(ROOM_ID, MEMBER_ID);

        then(chatRoomParticipantService).should().validateParticipant(MEMBER_ID, ROOM_ID);
        assertThat(response).isEqualTo(IndexingStatusResponse.of("RUNNING", "EMBEDDING", 40, 120));
    }
}
