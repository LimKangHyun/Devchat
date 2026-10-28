-- 보낸 브라우저가 붙인 임시 id. 연결이 끊겨 다시 보낸 메시지를 중복 저장하지 않는 데 쓴다.
-- 같은 사람의 같은 id는 한 번만 저장된다 (NULL은 여러 개 허용).
ALTER TABLE chat_message
    ADD COLUMN client_message_id varchar(36) NULL,
    ADD UNIQUE KEY uk_chat_message_sender_client_id (member_id, client_message_id);
