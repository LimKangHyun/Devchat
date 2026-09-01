CREATE INDEX idx_chat_message_created_room
    ON chat_message (created_at, room_id);