CREATE INDEX idx_chat_message_room_created
    ON chat_message (room_id, created_at, message_id);