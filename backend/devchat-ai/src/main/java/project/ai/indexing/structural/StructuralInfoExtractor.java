package project.ai.indexing.structural;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import project.ai.indexing.chunking.AstChunkExtractor;
import project.ai.indexing.chunking.ChunkMeta;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 파일 원문을 AST로 파싱해 구조 정보(클래스/상속/호출관계/import)를 뽑아낸다. */
@Component
@RequiredArgsConstructor
public class StructuralInfoExtractor {

    private final AstChunkExtractor astChunkExtractor;

    public StructuralInfo extract(String fileContent, String filePath) {
        if (fileContent == null || fileContent.isBlank()) return StructuralInfo.EMPTY;

        // 청킹 결과와 import를 한 번의 파싱으로 함께 얻는다.
        // import는 Pinecone에 저장되지 않는다 — 이 파일 하나를 검색어로 만드는
        // 이 요청 안에서만 쓰이고, ProjectTypeFilter가 참조 타입을 판별하는 데만 사용된다.
        AstChunkExtractor.ExtractionResult extraction = astChunkExtractor.extractWithImports(fileContent, filePath);
        List<ChunkMeta> chunks = extraction.chunks();
        if (chunks.isEmpty()) return StructuralInfo.EMPTY;

        String className = null;
        String superClassName = null;
        Set<String> interfaceNames = new LinkedHashSet<>();
        Set<String> methodNames = new LinkedHashSet<>();
        Set<String> referencedTypes = new LinkedHashSet<>();
        Set<String> calledMethods = new LinkedHashSet<>();
        Set<CalledMethodRef> calledMethodRefs = new LinkedHashSet<>();

        for (ChunkMeta chunk : chunks) {
            if (chunk.className() != null && !chunk.className().isEmpty()) {
                className = chunk.className();
            }
            if (chunk.superClassName() != null && !chunk.superClassName().isEmpty()) {
                superClassName = chunk.superClassName();
            }
            if (chunk.interfaceNames() != null) interfaceNames.addAll(chunk.interfaceNames());
            if (chunk.methodName() != null && !chunk.methodName().isEmpty()) {
                methodNames.add(chunk.methodName());
            }
            if (chunk.referencedTypeNames() != null) referencedTypes.addAll(chunk.referencedTypeNames());
            if (chunk.calledMethodNames() != null) calledMethods.addAll(chunk.calledMethodNames());
            if (chunk.calledMethodRefs() != null) calledMethodRefs.addAll(chunk.calledMethodRefs());
        }

        return new StructuralInfo(className, superClassName,
                List.copyOf(interfaceNames), List.copyOf(methodNames),
                List.copyOf(referencedTypes), List.copyOf(calledMethods),
                List.copyOf(calledMethodRefs), extraction.imports());
    }
}