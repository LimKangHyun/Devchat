-- 채팅방(= 연결된 저장소)별 팀 리뷰 규칙. AI 리뷰 프롬프트에 함께 들어간다. 방장만 수정한다.
ALTER TABLE chat_room ADD COLUMN review_conventions TEXT NULL;
