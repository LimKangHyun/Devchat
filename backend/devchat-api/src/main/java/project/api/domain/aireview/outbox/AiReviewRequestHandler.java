package project.api.domain.aireview.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import project.api.domain.aireview.app.AiReviewService;
import project.api.global.outbox.OutboxEvent;
import project.api.global.outbox.OutboxEventType;
import project.api.global.outbox.OutboxHandler;
import project.api.global.outbox.OutboxService;
import project.api.global.redis.RedisStreamClient;
import project.common.message.aireview.AiReviewRequestMessage;

/**
 * 파일 하나의 리뷰 요청을 AI 서버 스트림으로 보낸다.
 * 보낸 직후 서버가 죽으면 같은 요청이 한 번 더 갈 수 있는데, AI 서버의 claim이 같은 파일을 두 번 처리하지 않게 막는다.
 */
@Component
@RequiredArgsConstructor
public class AiReviewRequestHandler implements OutboxHandler {

    private final RedisStreamClient redisStreamClient;
    private final AiReviewService aiReviewService;
    private final OutboxService outboxService;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.AI_REVIEW_REQUEST;
    }

    @Override
    public void handle(OutboxEvent event) {
        redisStreamClient.publishAiReviewRequestJson(event.getPayload());
    }

    /** 끝내 보내지 못한 파일은 실패로 세어, 리뷰 전체가 완료 판정을 받을 수 있게 한다 */
    @Override
    public void onGiveUp(OutboxEvent event, String error) {
        AiReviewRequestMessage m = outboxService.readPayload(event, AiReviewRequestMessage.class);
        aiReviewService.saveFileReviewFail(m.aiReviewId(), m.chatRoomId(), m.filePath(), m.commitSha(),
            "리뷰 요청을 AI 서버로 보내지 못함: " + error);
    }
}
