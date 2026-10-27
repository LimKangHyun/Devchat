-- 코멘트를 만든 프롬프트 버전(프롬프트 파일 내용·모델 해시 앞 8자리). 프롬프트별 채택률을 비교하는 데 쓴다.
-- 이 컬럼이 생기기 전 코멘트는 NULL이다.
ALTER TABLE ai_review_comment ADD COLUMN prompt_version VARCHAR(20) NULL;

-- 리뷰 품질 집계(기간별 코멘트, 코멘트별 최신 상태)에 쓰는 인덱스
CREATE INDEX idx_ai_review_comment_created_at ON ai_review_comment (created_at);
CREATE INDEX idx_ai_comment_mod_comment_id_id ON ai_comment_mod (comment_id, id);
