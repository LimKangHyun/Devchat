-- #69(체크포인트 기반 안읽음 집계)에서 V1을 직접 고쳐 기존 DB에는 반영되지 않은 변경을 맞춘다.
-- V1을 새로 실행한 DB에는 이미 반영돼 있으므로 모든 단계는 있을 때만/없을 때만 실행한다.

-- 1. 더 이상 쓰지 않는 chat_room.last_sequence 삭제 (NOT NULL, 기본값 없음이라 방 생성 INSERT가 실패함)
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_room' AND COLUMN_NAME = 'last_sequence') > 0,
    'ALTER TABLE chat_room DROP COLUMN last_sequence',
    'DO 0');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 2. 체크포인트 테이블
CREATE TABLE IF NOT EXISTS `chat_room_checkpoint` (
    `room_id` bigint NOT NULL,
    `synced_message_id` bigint NOT NULL DEFAULT 0,
    `cumulative_count` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`),
    CONSTRAINT `fk_checkpoint_room` FOREIGN KEY (`room_id`) REFERENCES `chat_room` (`room_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. 체크포인트 집계용 인덱스
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message' AND INDEX_NAME = 'idx_chat_message_room_msg') = 0,
    'CREATE INDEX idx_chat_message_room_msg ON chat_message (room_id, message_id)',
    'DO 0');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
