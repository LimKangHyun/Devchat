CREATE TABLE login_session (
  id varchar(36) NOT NULL,
  member_id bigint NOT NULL,
  refresh_token_hash char(64) NOT NULL,
  device_name varchar(100) NOT NULL,
  created_at datetime(6) NOT NULL,
  last_used_at datetime(6) NOT NULL,
  expires_at datetime(6) NOT NULL,
  revoked_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_login_session_member (member_id, revoked_at, expires_at),
  CONSTRAINT fk_login_session_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE auth_token MODIFY refresh_token varchar(512) NULL;
