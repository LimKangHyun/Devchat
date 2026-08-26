package project.ai.indexing.structural;

import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;

import java.util.List;

public record StructuralInfo(
        String className,
        String superClassName,
        List<String> interfaceNames,
        List<String> methodNames,
        List<String> referencedTypeNames,
        List<String> calledMethodNames,
        List<CalledMethodRef> calledMethodRefs,
        /**
         * 이 파일의 import FQN 목록. Pinecone에는 저장되지 않으며,
         * 검색 요청 하나 처리하는 동안만 존재한다.
         * referencedTypeNames가 프로젝트 타입인지 ProjectTypeFilter가 판별하는 데 쓰인다.
         */
        List<String> imports
) {
    public static final StructuralInfo EMPTY = new StructuralInfo(
            null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return className == null && superClassName == null
                && interfaceNames.isEmpty() && methodNames.isEmpty()
                && referencedTypeNames.isEmpty() && calledMethodNames.isEmpty();
    }
}