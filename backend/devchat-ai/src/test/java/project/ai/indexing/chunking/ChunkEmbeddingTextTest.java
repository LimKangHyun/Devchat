package project.ai.indexing.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import project.ai.config.JavaParserConfig;

class ChunkEmbeddingTextTest {

    private final AstChunkExtractor extractor = new AstChunkExtractor(new JavaParserConfig().javaParser());

    private static final String SOURCE = """
        package com.example.auth;

        import com.example.auth.store.TokenStore;

        public class TokenProvider extends BaseProvider implements Provider {
            private TokenStore store;

            public String issue(String clientId, List<String> scopes) {
                return store.save(clientId);
            }
        }
        """;

    @Test
    void 머리말에_패키지_클래스_시그니처_호출이_들어간다() {
        ChunkMeta chunk = extractor.extract(SOURCE, "TokenProvider.java").get(0);

        String text = ChunkEmbeddingText.withHeader(chunk);

        assertThat(text).startsWith("package: com.example.auth\n");
        assertThat(text).contains("class: TokenProvider extends BaseProvider implements Provider\n");
        assertThat(text).contains("method: issue(String, List<String>)\n");
        assertThat(text).contains("calls: ");
        assertThat(text).endsWith("---\n" + chunk.chunk());
    }

    @Test
    void 질의쪽_시그니처_규칙이_인덱싱과_같다() {
        // RagContextService.signatureOf와 같은 규칙: 이름(파라미터 타입, ...)
        ChunkMeta chunk = extractor.extract(SOURCE, "TokenProvider.java").get(0);
        assertThat(chunk.methodSignature()).isEqualTo("issue(" + String.join(", ", List.of("String", "List<String>")) + ")");
    }

    @Test
    void 클래스를_모르는_조각은_코드만() {
        assertThat(ChunkEmbeddingText.withHeader(ChunkMeta.fallback("int x = 1;"))).isEqualTo("int x = 1;");
    }
}
