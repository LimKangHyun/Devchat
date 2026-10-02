package project.api.domain.github.event;

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
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;
import project.api.global.redis.RedisStreamClient;
import project.common.dto.github.GitRepoDto;

import project.api.domain.github.client.GitHubBotClient.PrFile;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrMergedEventListener {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final GitHubBotClient gitHubBotClient;
    private final RedisStreamClient redisStreamClient;

    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PrMergedEvent event) {
        ChatRoom room = chatRoomRepository.findById(event.roomId())
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

        Long ownerId = chatParticipantRepository.findByChatRoomIdAndIsOwnerTrue(event.roomId())
                .map(cp -> cp.getParticipant().getId())
                .orElse(null);

        if (ownerId == null) {
            log.warn("채팅방 owner 없음, 재인덱싱 스킵. roomId={}", event.roomId());
            return;
        }

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
        List<PrFile> files = gitHubBotClient.getPrFiles(
                repo.ownerName(), repo.repoName(), event.prNumber());

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
                            previousFilePath, "removed", "", event.headSha(), null);
                }
                continue;
            }

            String fileContent = "removed".equals(status) ? ""
                    : gitHubBotClient.getFileContent(repo.ownerName(), repo.repoName(), filePath, event.headSha());

            redisStreamClient.publishFileReindex(
                    room.getId(), room.getRepositoryUrl(), ownerId,
                    filePath, status, fileContent, event.headSha(), previousFilePath);
        }
    }

    private boolean isReindexableFile(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".java");
    }
}