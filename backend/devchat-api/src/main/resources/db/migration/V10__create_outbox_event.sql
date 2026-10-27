-- Transactional Outbox: DB 변경과 같은 트랜잭션에 "보낼 메시지"를 적어 두고, 릴레이(OutboxRelay)가 Redis로 보낸다.
-- 커밋 직후 서버가 죽거나 Redis·GitHub가 실패해도 메시지가 사라지지 않고 다시 시도된다.
CREATE TABLE outbox_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_type      VARCHAR(40)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    payload         MEDIUMTEXT   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)  NOT NULL,
    last_error      VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    sent_at         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_status_next (status, next_attempt_at),
    KEY idx_outbox_status_sent (status, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
