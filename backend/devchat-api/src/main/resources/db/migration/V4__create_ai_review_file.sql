CREATE TABLE ai_review_file (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ai_review_id  BIGINT      NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    DATETIME(6),
    CONSTRAINT uk_ai_review_file UNIQUE (ai_review_id, file_path),
    CONSTRAINT fk_ai_review_file_review FOREIGN KEY (ai_review_id) REFERENCES ai_review (id)
) ENGINE=InnoDB;