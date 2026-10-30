-- 피드백 이슈를 누가 보냈는지. 이슈는 공개 저장소에 올라가 보낸 사람을 적지 않으므로 여기에만 남긴다.
-- 이슈에 관리자가 댓글을 달거나 이슈가 닫히면 보낸 사람에게 앱 알림을 보낸다
CREATE TABLE feedback_issue (
  issue_number int NOT NULL,
  member_id bigint NOT NULL,
  title varchar(200) NOT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (issue_number),
  KEY idx_feedback_issue_member (member_id),
  CONSTRAINT fk_feedback_issue_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 알림을 누르면 열 주소(앱 밖, 예: GitHub 이슈). 없으면 알림 종류별 화면으로 간다
ALTER TABLE notification ADD COLUMN link varchar(300) NULL;

ALTER TABLE notification MODIFY COLUMN `type`
    enum('CODE_REVIEW','FRIEND_ACCEPTED','FRIEND_REJECTED','FRIEND_REQUESTED','NEW_DM','STUDY_APPLY','STUDY_APPROVED','STUDY_REJECTED','WE_ARE_FRIEND_NOW','MENTION','SCHEDULE_ADDED','SCHEDULE_CHANGED','SCHEDULE_CANCELED','SCHEDULE_REMINDER','FEEDBACK_REPLIED','FEEDBACK_RESOLVED') DEFAULT NULL;
