package project.api.domain.github.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.github.GitRepoUrlUtils;
import project.api.domain.github.client.GitHubBotClient;
import project.api.domain.github.client.GitHubBotClient.PrFile;
import project.api.global.redis.RedisStreamClient;
import project.common.dto.github.GitRepoDto;

/**
 * 기본 브랜치가 바뀌면 바뀐 파일만 다시 인덱싱한다.
 * 처음 인덱싱이 기본 브랜치를 기준으로 하므로, 재인덱싱도 기본 브랜치에 들어온 변경만 반영해야 인덱스가 어긋나지 않는다.
 * - PR 머지: 기본 브랜치로 머지된 PR의 파일을, 머지 결과 커밋 기준으로 읽는다.
 * - 직접 push: 기본 브랜치에 PR 없이 들어온 push의 파일을, push 후 커밋 기준으로 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultBranchReindexListener {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final GitHubBotClient gitHubBotClient;
    private final RedisStreamClient redisStreamClient;

    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PrMergedEvent event) {
        ChatRoom room = chatRoomRepository.findById(event.roomId()).orElse(null);
        if (room == null) return;
        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        List<PrFile> files = gitHubBotClient.getPrFiles(repo.ownerName(), repo.repoName(), event.prNumber());
        log.info("PR 머지 재인덱싱. roomId={}, pr=#{}, ref={}, files={}",
                room.getId(), event.prNumber(), event.mergeCommitSha(), files.size());
        reindex(room, repo, files, event.mergeCommitSha());
    }

    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DefaultBranchPushedEvent event) {
        ChatRoom room = chatRoomRepository.findById(event.roomId()).orElse(null);
        if (room == null) return;
        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        // PR 머지로 생긴 push는 PrMergedEvent가 같은 변경을 처리하므로 건너뛴다(두 번 인덱싱 방지).
        if (gitHubBotClient.isFromMergedPullRequest(repo.ownerName(), repo.repoName(), event.after())) {
            log.info("PR 머지로 생긴 push, 재인덱싱 건너뜀. roomId={}, after={}", room.getId(), event.after());
            return;
        }

        List<PrFile> files = gitHubBotClient.getCompareFiles(
                repo.ownerName(), repo.repoName(), event.before(), event.after());
        log.info("기본 브랜치 직접 push 재인덱싱. roomId={}, {}..{}, files={}",
                room.getId(), event.before(), event.after(), files.size());
        reindex(room, repo, files, event.after());
    }

    private void reindex(ChatRoom room, GitRepoDto repo, List<PrFile> files, String ref) {
        Long ownerId = chatParticipantRepository.findByChatRoomIdAndIsOwnerTrue(room.getId())
                .map(cp -> cp.getParticipant().getId())
                .orElse(null);
        if (ownerId == null) {
            log.warn("채팅방 owner 없음, 재인덱싱 스킵. roomId={}", room.getId());
            return;
        }

        for (PrFile file : files) {
            String filePath = file.filename();
            String status = file.status();

            // 이름이 바뀐 파일은 옛 경로의 청크를 지워야 한다. 옛 경로가 인덱싱 대상일 때만 넘긴다.
            String previousFilePath = "renamed".equals(status) && isReindexableFile(file.previousFilename())
                    ? file.previousFilename() : null;

            if (!isReindexableFile(filePath)) {
                // 확장자가 바뀌어 새 경로는 대상이 아니어도, 옛 경로 청크는 삭제해야 한다.
                if (previousFilePath != null) {
                    redisStreamClient.publishFileReindex(
                            room.getId(), room.getRepositoryUrl(), ownerId,
                            previousFilePath, "removed", "", ref, null);
                }
                continue;
            }

            String fileContent = "removed".equals(status) ? ""
                    : gitHubBotClient.getFileContent(repo.ownerName(), repo.repoName(), filePath, ref);

            redisStreamClient.publishFileReindex(
                    room.getId(), room.getRepositoryUrl(), ownerId,
                    filePath, status, fileContent, ref, previousFilePath);
        }
    }

    private boolean isReindexableFile(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".java");
    }
}
