package project.backend.global.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import project.common.message.AiReviewRequestMessage;
import project.common.message.DeleteIndexMessage;
import project.common.message.RepoIndexingMessage;

@Component
@RequiredArgsConstructor
public class RedisStreamClient {

    private static final String AI_REVIEW_REQUEST_STREAM = "stream:ai-review:request";
    private static final String DELETE_INDEX_STREAM = "stream:delete-index:request";
    private static final String INDEXING_STREAM = "stream:repo-indexing:request";

    private final RedisTemplate<String, String> streamRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publishAiReviewRequest(Long aiReviewId, Long chatRoomId, Long repoId,
                                       String filePath, String fileDiff, String fileContent,
                                       String baseContent, String prTitle, String prBody) {
        try {
            String json = objectMapper.writeValueAsString(
                    new AiReviewRequestMessage(aiReviewId, chatRoomId, repoId, filePath, fileDiff, fileContent, baseContent, prTitle, prBody));
            publish(AI_REVIEW_REQUEST_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    public void publishDeleteIndex(Long roomId) {
        try {
            String json = objectMapper.writeValueAsString(new DeleteIndexMessage(roomId));
            publish(DELETE_INDEX_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    public void publishRepoIndexing(Long roomId, String repositoryUrl, Long memberId) {
        try {
            String json = objectMapper.writeValueAsString(new RepoIndexingMessage(roomId, repositoryUrl, memberId));
            publish(INDEXING_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    private void publish(String streamKey, String json) {
        ObjectRecord<String, String> record = StreamRecords.newRecord()
                .ofObject(json)
                .withStreamKey(streamKey);
        streamRedisTemplate.opsForStream().add(record);
    }
}