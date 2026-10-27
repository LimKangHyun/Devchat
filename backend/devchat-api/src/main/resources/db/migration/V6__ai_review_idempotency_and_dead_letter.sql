-- PR 하나에 AI 리뷰 행은 하나다(새 커밋이 오면 같은 행을 다시 PENDING으로 돌린다).
-- 지금까지는 "있는지 확인 → 없으면 생성"이라, GitHub가 webhook을 다시 보내면 같은 PR의 행이 두 개 생길 수 있었다.
ALTER TABLE ai_review ADD CONSTRAINT uk_ai_review_room_pr UNIQUE (room_id, pr_number);

-- 최대 재시도를 넘긴 스트림 메시지. 원문을 남겨 원인을 확인하고 필요하면 다시 넣는다.
CREATE TABLE stream_dead_letter (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stream_key  VARCHAR(100) NOT NULL,
    record_id   VARCHAR(50)  NOT NULL,
    payload     MEDIUMTEXT,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_stream_dead_letter UNIQUE (stream_key, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
