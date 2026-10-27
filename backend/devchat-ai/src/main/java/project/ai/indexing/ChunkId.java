package project.ai.indexing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 청크(Qdrant 포인트) ID 규칙.
 *
 * Qdrant는 포인트 ID로 UUID나 정수만 받으므로, "{repoId}-{경로의 /를 _로}-{chunkIndex}"를
 * 이름 기반 UUID로 바꾼다. 같은 파일을 다시 인덱싱하면 같은 ID가 나오므로 기존 청크를 덮어쓴다.
 */
public final class ChunkId {

    private ChunkId() {}

    public static String of(Object repoId, String filePath, int chunkIndex) {
        String key = repoId + "-" + filePath.replace("/", "_") + "-" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
