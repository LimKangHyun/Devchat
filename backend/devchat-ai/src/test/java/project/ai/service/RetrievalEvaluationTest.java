package project.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import project.ai.rag.RagContextService;
import project.ai.rag.SearchHit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 실제 벡터 저장소/임베딩/Cohere를 호출하는 수동 평가용 테스트.
 * 일반 빌드에서 외부 API를 부르지 않도록 -Drag.eval=true일 때만 실행한다.
 *
 * 필요한 파일 (src/test/resources/rag-eval/pr140/):
 *  - pr.diff     : 평가 대상 파일의 diff
 *  - after.java  : 변경 후 파일 원문 (구조 정보·변경 심볼 추출에 사용)
 *  - before.java : 변경 전 파일 원문 (선택, 없으면 신규 파일로 간주해 삭제 심볼 검색 생략)
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "rag.eval", matches = "true")
class RetrievalEvaluationTest {

    private static final Path CASE_DIR = Path.of("src/test/resources/rag-eval/pr140");

    @Autowired
    private RagContextService ragContextService;

    @Test
    void evaluateTop5() throws Exception {

        Long repoId = 1L;

        String filePath =
            "oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2ClientAuthenticationProvider.java";

        String diff = Files.readString(CASE_DIR.resolve("pr.diff"));
        String fileContent = Files.readString(CASE_DIR.resolve("after.java"));

        Path basePath = CASE_DIR.resolve("before.java");
        String baseContent = Files.exists(basePath) ? Files.readString(basePath) : "";

        List<SearchHit> results =
            ragContextService.searchForEvaluation(
                repoId,
                filePath,
                diff,
                fileContent,
                baseContent,
                5,
                Set.of(filePath)
            );

        System.out.println("=================================");
        System.out.println("Query : " + filePath);
        System.out.println("=================================");

        for (int i = 0; i < results.size(); i++) {

            SearchHit result = results.get(i);

            String path = result.getString("filePath");
            String className = result.getString("className");
            String method = result.getString("methodSignature");

            System.out.printf(
                "%d. %.4f%n",
                i + 1,
                result.score());

            System.out.println("path   : " + path);
            System.out.println("class  : " + className);
            System.out.println("method : " + method);
            System.out.println("---------------------------------");
        }
    }
}
