package project.ai.service;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootTest
class RetrievalEvaluationTest {

    @Autowired
    private RagContextService ragContextService;

    @Test
    void evaluateTop5() throws Exception {

        Long repoId = 1L;

        String filePath =
            "oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2ClientAuthenticationProvider.java";

        String diff = Files.readString(
            Path.of("src/test/resources/pr140.diff")
        );

        List<ScoredVectorWithUnsignedIndices> results =
            ragContextService.searchForEvaluation(
                repoId,
                filePath,
                diff,
                filePath,
                5,
                null
            );

        System.out.println("=================================");
        System.out.println("Query : " + filePath);
        System.out.println("=================================");

        for (int i = 0; i < results.size(); i++) {

            ScoredVectorWithUnsignedIndices result = results.get(i);

            String path = result.getMetadata()
                .getFieldsOrThrow("filePath")
                .getStringValue();

            String className = result.getMetadata()
                .getFieldsOrThrow("className")
                .getStringValue();

            String method = result.getMetadata()
                .getFieldsOrThrow("methodSignature")
                .getStringValue();

            System.out.printf(
                "%d. %.4f%n",
                i + 1,
                result.getScore());

            System.out.println("path   : " + path);
            System.out.println("class  : " + className);
            System.out.println("method : " + method);
            System.out.println("---------------------------------");
        }
    }
}