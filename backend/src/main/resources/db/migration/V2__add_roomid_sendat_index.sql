ALTER TABLE chat_message
    ADD INDEX idx_chat_room_messageid_desc (room_id, message_id DESC);