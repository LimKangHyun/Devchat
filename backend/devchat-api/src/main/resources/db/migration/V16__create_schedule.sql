CREATE TABLE schedule (
  id bigint NOT NULL AUTO_INCREMENT,
  owner_member_id bigint NOT NULL,
  title varchar(100) NOT NULL,
  location varchar(200) DEFAULT NULL,
  memo text DEFAULT NULL,
  start_at datetime(6) NOT NULL,
  end_at datetime(6) NOT NULL,
  all_day bit(1) NOT NULL,
  repeat_frequency varchar(10) NOT NULL,
  repeat_days varchar(30) DEFAULT NULL,
  repeat_until date DEFAULT NULL,
  repeat_count int DEFAULT NULL,
  sequence int NOT NULL DEFAULT 0,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_owner_start (owner_member_id, start_at),
  KEY idx_schedule_start (start_at),
  CONSTRAINT fk_schedule_owner FOREIGN KEY (owner_member_id) REFERENCES member (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_participant (
  id bigint NOT NULL AUTO_INCREMENT,
  schedule_id bigint NOT NULL,
  member_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_participant (schedule_id, member_id),
  KEY idx_schedule_participant_member (member_id),
  CONSTRAINT fk_schedule_participant_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
  CONSTRAINT fk_schedule_participant_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_reminder (
  id bigint NOT NULL AUTO_INCREMENT,
  schedule_id bigint NOT NULL,
  minutes_before int NOT NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_reminder_schedule (schedule_id),
  CONSTRAINT fk_schedule_reminder_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_exclusion (
  id bigint NOT NULL AUTO_INCREMENT,
  schedule_id bigint NOT NULL,
  occurrence_date date NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_exclusion (schedule_id, occurrence_date),
  CONSTRAINT fk_schedule_exclusion_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_reminder_dispatch (
  id bigint NOT NULL AUTO_INCREMENT,
  schedule_id bigint NOT NULL,
  occurrence_start datetime(6) NOT NULL,
  minutes_before int NOT NULL,
  sent_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_dispatch (schedule_id, occurrence_start, minutes_before),
  CONSTRAINT fk_schedule_dispatch_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE calendar_feed_token (
  member_id bigint NOT NULL,
  token_hash char(64) NOT NULL,
  token_encrypted varchar(512) NOT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (member_id),
  UNIQUE KEY uk_calendar_feed_token_hash (token_hash),
  CONSTRAINT fk_calendar_feed_token_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 일정 알림은 문구를 직접 저장한다
ALTER TABLE notification ADD COLUMN message varchar(200) NULL;

ALTER TABLE notification MODIFY COLUMN `type`
    enum('CODE_REVIEW','FRIEND_ACCEPTED','FRIEND_REJECTED','FRIEND_REQUESTED','NEW_DM','STUDY_APPLY','STUDY_APPROVED','STUDY_REJECTED','WE_ARE_FRIEND_NOW','MENTION','SCHEDULE_ADDED','SCHEDULE_CHANGED','SCHEDULE_CANCELED','SCHEDULE_REMINDER') DEFAULT NULL;
