package project.ai.indexing.chunking;

import java.util.List;

/**
 * 청크를 임베딩할 때 쓰는 "맥락 머리말 + 코드" 텍스트.
 * 메서드 코드만으로는 같은 이름의 메서드(authenticate 등)가 어느 클래스 것인지 벡터가 구분하지 못해서,
 * 패키지·클래스(상속/구현)·메서드 시그니처·부르는 메서드를 앞에 붙인다.
 * 인덱싱(문서)과 검색 질의가 같은 형식을 쓰도록 한곳에 둔다.
 */
public final class ChunkEmbeddingText {

    private static final int MAX_CALLS = 15;

    private ChunkEmbeddingText() {}

    /** 클래스 이름을 모르는 조각(통째로 자른 fallback 등)은 코드만 돌려준다. */
    public static String withHeader(ChunkMeta meta) {
        if (meta.className() == null || meta.className().isBlank()) return meta.chunk();

        StringBuilder sb = new StringBuilder();
        if (meta.packageName() != null && !meta.packageName().isBlank()) {
            sb.append("package: ").append(meta.packageName()).append("\n");
        }
        sb.append("class: ").append(meta.className());
        if (meta.superClassName() != null && !meta.superClassName().isBlank()) {
            sb.append(" extends ").append(meta.superClassName());
        }
        if (meta.interfaceNames() != null && !meta.interfaceNames().isEmpty()) {
            sb.append(" implements ").append(String.join(", ", meta.interfaceNames()));
        }
        sb.append("\n");
        if (meta.methodSignature() != null && !meta.methodSignature().isBlank()) {
            sb.append("method: ").append(meta.methodSignature()).append("\n");
        }
        List<String> calls = meta.calledMethodQualified() != null && !meta.calledMethodQualified().isEmpty()
            ? meta.calledMethodQualified() : meta.calledMethodNames();
        if (calls != null && !calls.isEmpty()) {
            sb.append("calls: ").append(String.join(", ", calls.stream().distinct().limit(MAX_CALLS).toList()))
                .append("\n");
        }
        return sb.append("---\n").append(meta.chunk()).toString();
    }
}
