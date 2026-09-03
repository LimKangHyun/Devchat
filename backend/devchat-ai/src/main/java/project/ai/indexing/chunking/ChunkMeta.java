package project.ai.indexing.chunking;

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
    List<CalledMethodRef> calledMethodRefs,
    /** "클래스.메서드" 조합. 호출자 검색을 이름만이 아닌 대상 클래스까지 포함해 매칭하기 위한 인덱스용 필드. */
    List<String> calledMethodQualified,
    List<String> referencedTypeNames,
    List<String> annotations
) {
    public record CalledMethodRef(String methodName, String targetClassHint, boolean resolved) {}

    public static ChunkMeta fallback(String code) {
        return new ChunkMeta(null, null, -1, code, null, null, null,
            null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public ChunkMeta withIndexingInfo(String id, String relativePath, int chunkIndex) {
        return new ChunkMeta(id, relativePath, chunkIndex, chunk, className,
            methodName, methodSignature, packageName, superClassName,
            interfaceNames, calledMethodNames, calledMethodRefs, calledMethodQualified,
            referencedTypeNames, annotations);
    }
}