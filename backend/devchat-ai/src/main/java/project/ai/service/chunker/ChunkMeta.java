package project.ai.service.chunker;

import java.util.List;

public record ChunkMeta(
    String id,
    String relativePath,
    int chunkIndex,
    String chunk,
    String className,
    String methodName,
    String methodSignature,
    String packageName,
    String superClassName,
    List<String> interfaceNames,
    List<String> calledMethodNames,
    List<String> referencedTypeNames,
    List<String> annotations
) {
    /** AST 파싱 실패 시 슬라이딩 윈도우 폴백용. id/relativePath/chunkIndex는 이후 withIndexingInfo()로 채운다. */
    public static ChunkMeta fallback(String code) {
        return new ChunkMeta(null, null, -1, code, null, null, null,
            null, null, List.of(), List.of(), List.of(), List.of());
    }

    /** AST 추출 직후엔 비어있는 id/relativePath/chunkIndex를 인덱싱 시점에 채워서 새 인스턴스를 반환한다. */
    public ChunkMeta withIndexingInfo(String id, String relativePath, int chunkIndex) {
        return new ChunkMeta(id, relativePath, chunkIndex, chunk, className,
            methodName, methodSignature, packageName, superClassName,
            interfaceNames, calledMethodNames, referencedTypeNames, annotations);
    }
}