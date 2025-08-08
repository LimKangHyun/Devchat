ALTER TABLE chat_message
    ADD INDEX idx_chat_room_sendat (room_id, message_id DESC);