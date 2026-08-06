package project.api.global.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.common.message.aireview.AiReviewRequestMessage;
import project.common.message.git.GitSummaryRequestMessage;
import project.common.message.index.DeleteIndexMessage;
import project.common.message.index.FileReindexMessage;
import project.common.message.index.RepoIndexRequestMessage;

@Component
@RequiredArgsConstructor
public class RedisStreamClient {

    private static final String GIT_SUMMARY_REQUEST_STREAM = "stream:git-summary:request";
    private static final String AI_REVIEW_REQUEST_STREAM = "stream:ai-review:request";
    private static final String DELETE_INDEX_STREAM = "stream:delete-index:request";
    private static final String INDEXING_STREAM = "stream:repo-index:request";
    private static final String FILE_REINDEX_STREAM = "stream:file-reindex:request";

    @Qualifier("streamStringRedisTemplate")
    private final StringRedisTemplate streamStringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publishGitSummaryRequest(Long roomId, Long messageId, String eventType, String prStatus, String fullContent) {
        try {
            String json = objectMapper.writeValueAsString(
                    new GitSummaryRequestMessage(roomId, messageId, eventType, prStatus, fullContent));
            publish(GIT_SUMMARY_REQUEST_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    public void publishAiReviewRequest(Long aiReviewId, Long chatRoomId, Long repoId,
        String filePath, String fileDiff, String fileContent,
        String baseContent, String prTitle, String prBody, List<String> changedFilesInPr) {
        try {
            String json = objectMapper.writeValueAsString(
                new AiReviewRequestMessage(aiReviewId, chatRoomId, repoId, filePath, fileDiff, fileContent, baseContent, prTitle, prBody, changedFilesInPr));
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
            String json = objectMapper.writeValueAsString(new RepoIndexRequestMessage(roomId, repositoryUrl, memberId));
            publish(INDEXING_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    public void publishFileReindex(Long roomId, String repoUrl, Long memberId,
        String filePath, String status, String fileContent, String headSha) {
        try {
            String json = objectMapper.writeValueAsString(
                new FileReindexMessage(roomId, repoUrl, memberId, filePath, status, fileContent, headSha));
            publish(FILE_REINDEX_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    private void publish(String streamKey, String json) {
        ObjectRecord<String, String> record = StreamRecords.newRecord()
            .ofObject(json)
            .withStreamKey(streamKey);
        streamStringRedisTemplate.opsForStream().add(record);
    }
}