ALTER TABLE chat_message_search
    ADD FULLTEXT INDEX idx_content_ngram (content)
        WITH PARSER ngram;