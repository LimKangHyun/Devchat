CREATE TABLE ai_comment_mod (
                                id          BIGINT NOT NULL AUTO_INCREMENT,
                                comment_id  BIGINT,
                                active      BOOLEAN NOT NULL,
                                reason      VARCHAR(50),
                                other_reason VARCHAR(255),
                                changed_by  VARCHAR(255),
                                created_at  DATETIME(6),
                                PRIMARY KEY (id),
                                CONSTRAINT fk_ai_comment_mod_comment
                                    FOREIGN KEY (comment_id) REFERENCES ai_review_comment (id)
);