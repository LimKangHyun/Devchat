-- V4__remove_indexed_and_created_at_column.sql
ALTER TABLE chat_message_index_status DROP COLUMN indexed;
ALTER TABLE chat_message_index_status DROP COLUMN created_at;