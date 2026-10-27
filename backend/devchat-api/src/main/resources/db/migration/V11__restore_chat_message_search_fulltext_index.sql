    -- 채팅 메시지 검색(MATCH ... AGAINST)에 필요한 ngram FULLTEXT 인덱스 복구.
-- #66에서 V1을 직접 고치며 인덱스 정의가 빠져, 이후 새로 만든 DB에서는 검색이 ERROR 1191로 실패한다.
-- 예전 V1로 만든 DB에는 이미 있으므로 없을 때만 만든다.
SET @stmt = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message_search' AND INDEX_TYPE = 'FULLTEXT') = 0,
    'ALTER TABLE chat_message_search ADD FULLTEXT INDEX idx_content_ngram (content) WITH PARSER ngram',
    'DO 0');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
