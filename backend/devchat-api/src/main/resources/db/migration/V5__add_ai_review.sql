-- V5__add_ai_review.sql

-- AI 리뷰 테이블 (chat_message FK보다 먼저 생성)
CREATE TABLE ai_review (
                           id               BIGINT NOT NULL AUTO_INCREMENT,
                           room_id          BIGINT NOT NULL,
                           pr_number        INT DEFAULT NULL,
                           commit_sha       VARCHAR(255) DEFAULT NULL,
                           status           ENUM('FAIL','PENDING','SUCCESS') NOT NULL DEFAULT 'PENDING',
                           review_json      TEXT DEFAULT NULL,
                           error_message    VARCHAR(255) DEFAULT NULL,
                           github_published BIT(1) NOT NULL DEFAULT 0,
                           created_at       DATETIME NOT NULL,
                           updated_at       DATETIME NOT NULL,
                           PRIMARY KEY (id),
                           CONSTRAINT fk_ai_review_chat_room
                               FOREIGN KEY (room_id) REFERENCES chat_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- chat_message에 AI 리뷰 관련 컬럼 추가
ALTER TABLE chat_message
    ADD COLUMN pr_number INT DEFAULT NULL,
    ADD COLUMN ai_review_id BIGINT DEFAULT NULL,
    ADD CONSTRAINT fk_chat_message_ai_review
        FOREIGN KEY (ai_review_id) REFERENCES ai_review (id);

-- chat_message type enum에 AI_REVIEW 추가
ALTER TABLE chat_message
    MODIFY COLUMN type ENUM('AI_REVIEW','CODE','EVENT','GIT','IMAGE','TEXT') DEFAULT NULL;

-- AI 리뷰 코멘트 테이블
CREATE TABLE ai_review_comment (
                                   id           BIGINT NOT NULL AUTO_INCREMENT,
                                   ai_review_id BIGINT NOT NULL,
                                   file_path    VARCHAR(500) NOT NULL,
                                   line_number  INT NOT NULL,
                                   comment      TEXT NOT NULL,
                                   created_at   DATETIME NOT NULL,
                                   PRIMARY KEY (id),
                                   CONSTRAINT fk_ai_review_comment_review
                                       FOREIGN KEY (ai_review_id) REFERENCES ai_review (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AI 코멘트 상태 테이블 (이력 누적)
CREATE TABLE ai_comment_status (
                                   id           BIGINT NOT NULL AUTO_INCREMENT,
                                   comment_id   BIGINT NOT NULL,
                                   active       BIT(1) NOT NULL DEFAULT 1,
                                   reason       ENUM('DUPLICATE','INCORRECT','OTHER','UNNECESSARY') DEFAULT NULL,
                                   other_reason VARCHAR(100) DEFAULT NULL,
                                   changed_by   VARCHAR(100) NOT NULL,
                                   created_at   DATETIME NOT NULL,
                                   PRIMARY KEY (id),
                                   CONSTRAINT fk_ai_comment_status_comment
                                       FOREIGN KEY (comment_id) REFERENCES ai_review_comment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;