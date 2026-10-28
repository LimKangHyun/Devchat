package project.api.domain.chat.typing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;

/**
 * "○○님이 입력 중…" 신호. 저장하지 않고 같은 방 구독자에게 바로 전달만 한다.
 * 브라우저가 입력하는 동안 몇 초마다 보내고, 받는 쪽은 몇 초 동안 소식이 없으면 표시를 지운다.
 */
@Slf4j
@Service
public class TypingService {

    public static String topic(Long roomId) {
        return "/topic/chat/" + roomId + "/typing";
    }

    /** 같은 사람이 "입력 중"을 이보다 자주 보내면 전달하지 않는다 */
    static final long TYPING_INTERVAL_MS = 2_000;
    /** 어떤 신호든 이보다 자주 오면 버린다 (도배 방지) */
    static final long MIN_INTERVAL_MS = 300;
    /** 방 참여 확인 결과를 이만큼 기억해 매번 DB를 보지 않는다 */
    static final long MEMBERSHIP_TTL_MS = 60_000;

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatParticipantRepository participantRepository;
    private final LongSupplier clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    @Autowired
    public TypingService(SimpMessagingTemplate messagingTemplate, ChatParticipantRepository participantRepository) {
        this(messagingTemplate, participantRepository, System::currentTimeMillis);
    }

    TypingService(SimpMessagingTemplate messagingTemplate, ChatParticipantRepository participantRepository,
                  LongSupplier clock) {
        this.messagingTemplate = messagingTemplate;
        this.participantRepository = participantRepository;
        this.clock = clock;
    }

    public record TypingEvent(Long memberId, String nickname, boolean typing) {}

    private static final class State {
        long lastSentAt = Long.MIN_VALUE / 2;
        boolean lastTyping;
        long memberCheckedAt = Long.MIN_VALUE / 2;
        boolean member;
    }

    public void signal(Long memberId, String nickname, Long roomId, boolean typing) {
        long now = clock.getAsLong();
        State st = states.computeIfAbsent(memberId + ":" + roomId, k -> new State());
        synchronized (st) {
            long sinceLast = now - st.lastSentAt;
            if (sinceLast < MIN_INTERVAL_MS) return;
            if (typing && st.lastTyping && sinceLast < TYPING_INTERVAL_MS) return;
            if (!typing && !st.lastTyping) return; // 이미 멈췄다고 알렸다

            if (now - st.memberCheckedAt >= MEMBERSHIP_TTL_MS) {
                st.member = participantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(memberId, roomId);
                st.memberCheckedAt = now;
            }
            if (!st.member) return;

            st.lastSentAt = now;
            st.lastTyping = typing;
        }
        messagingTemplate.convertAndSend(topic(roomId), new TypingEvent(memberId, nickname, typing));
    }

    // 오래 조용한 기록은 지운다
    @Scheduled(fixedDelay = 60_000)
    void cleanUp() {
        long now = clock.getAsLong();
        states.values().removeIf(st -> now - Math.max(st.lastSentAt, st.memberCheckedAt) > MEMBERSHIP_TTL_MS);
    }

    int trackedCount() {
        return states.size();
    }
}
