-- 답장: 어떤 메시지에 답했는지와, 답장한 순간의 원문 모습(보낸 사람·한 줄 미리보기).
-- 원문을 나중에 고치거나 지워도 답장에 보이는 원문은 바뀌지 않는다 (그 내용에 답한 것이므로).
-- reply_to_id는 원문으로 이동할 때만 쓰고, 원문 행이 지워지면(방 삭제 등) 연결만 끊는다.
ALTER TABLE chat_message
    ADD COLUMN reply_to_id bigint NULL,
    ADD COLUMN reply_to_sender_id bigint NULL,
    ADD COLUMN reply_to_sender_name varchar(50) NULL,
    ADD COLUMN reply_to_type varchar(20) NULL,
    ADD COLUMN reply_to_preview varchar(120) NULL,
    ADD CONSTRAINT fk_chat_message_reply_to
        FOREIGN KEY (reply_to_id) REFERENCES chat_message (message_id) ON DELETE SET NULL;
