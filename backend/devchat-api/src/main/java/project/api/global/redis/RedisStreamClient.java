package project.api.global.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.common.message.aireview.AiReviewRequestMessage;
import project.common.message.aireview.ChangedFileSnapshot;
import project.common.message.git.GitSummaryRequestMessage;
import project.common.message.index.DeleteIndexMessage;
import project.common.message.index.FileReindexMessage;
import project.common.message.index.IndexingKeys;
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
        String baseContent, String prTitle, String prBody, List<String> changedFilesInPr,
        List<ChangedFileSnapshot> relatedChangedFiles, String reviewConventions, String commitSha) {
        try {
            String json = objectMapper.writeValueAsString(
                new AiReviewRequestMessage(aiReviewId, chatRoomId, repoId, filePath, fileDiff, fileContent, baseContent,
                    prTitle, prBody, changedFilesInPr, relatedChangedFiles, reviewConventions, commitSha));
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
            // AI 서버가 요청을 받아 락을 잡기 전까지 "진행 중"으로 보이게 표시한다. AI 서버가 오래 내려가 있으면 만료된다.
            streamStringRedisTemplate.opsForValue()
                .set(IndexingKeys.requestedKey(roomId), "1", Duration.ofMinutes(10));
            publish(INDEXING_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    public void publishFileReindex(Long roomId, String repoUrl, Long memberId,
        String filePath, String status, String fileContent, String headSha, String previousFilePath) {
        try {
            String json = objectMapper.writeValueAsString(
                new FileReindexMessage(roomId, repoUrl, memberId, filePath, status, fileContent, headSha,
                    previousFilePath));
            publish(FILE_REINDEX_STREAM, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }

    /**
     * 진행 중인 인덱싱에 취소를 요청한다. AI 서버가 배치마다 이 플래그를 확인해 멈추고,
     * 저장한 청크를 지운 뒤 결과 스트림으로 "취소됨"을 알린다.
     * TTL은 AI 서버의 인덱싱 락과 같다. 진행 중이 아니어도 다음 인덱싱 시작 때 지워진다.
     */
    public void requestIndexingCancel(Long roomId) {
        streamStringRedisTemplate.opsForValue()
            .set(IndexingKeys.cancelKey(roomId), "1", Duration.ofMinutes(30));
    }

    /**
     * AI 서버가 이 저장소를 인덱싱 중인지. 요청을 보낸 직후(AI 서버가 아직 받기 전)도 진행 중으로 본다.
     * AI 서버는 진행하는 동안 락을 2분 단위로 연장하므로, 둘 다 없으면 DB의 RUNNING은 비정상 종료로 남은 상태다.
     */
    public boolean isIndexingInProgress(Long roomId) {
        return Boolean.TRUE.equals(streamStringRedisTemplate.hasKey(IndexingKeys.lockKey(roomId)))
            || Boolean.TRUE.equals(streamStringRedisTemplate.hasKey(IndexingKeys.requestedKey(roomId)));
    }

    /** AI 서버가 기록한 현재 인덱싱 진행률. 진행 중이 아니면 비어 있다. */
    public Map<Object, Object> findIndexingProgress(Long roomId) {
        return streamStringRedisTemplate.opsForHash().entries(IndexingKeys.progressKey(roomId));
    }

    private void publish(String streamKey, String json) {
        ObjectRecord<String, String> record = StreamRecords.newRecord()
            .ofObject(json)
            .withStreamKey(streamKey);
        streamStringRedisTemplate.opsForStream().add(record);
    }
}