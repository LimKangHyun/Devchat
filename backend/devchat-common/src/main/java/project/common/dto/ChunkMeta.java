package project.common.dto;

public record ChunkMeta(
        String id,
        String relativePath,
        int chunkIndex,
        String chunk
) {}