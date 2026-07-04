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

import java.util.Map;

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
        Map<String, String> fileStatuses = gitHubBotClient.getPrFileStatuses(
                repo.ownerName(), repo.repoName(), event.prNumber());

        for (Map.Entry<String, String> entry : fileStatuses.entrySet()) {
            String filePath = entry.getKey();
            String status = entry.getValue();
            if (!isReindexableFile(filePath)) continue;

            String fileContent = "removed".equals(status) ? ""
                    : gitHubBotClient.getFileContent(repo.ownerName(), repo.repoName(), filePath, event.headSha());

            redisStreamClient.publishFileReindex(
                    room.getId(), room.getRepositoryUrl(), ownerId,
                    filePath, status, fileContent, event.headSha());
        }
    }

    private boolean isReindexableFile(String filePath) {
        return filePath.toLowerCase().endsWith(".java");
    }
}