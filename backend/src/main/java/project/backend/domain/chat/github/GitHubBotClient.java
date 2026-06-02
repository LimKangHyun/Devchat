package project.backend.domain.chat.github;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubBotClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key}")
    private String privateKeyPem;

    // GitHub App JWT 생성 (10분 유효)
    private String generateJwt() {
        try {
            String formattedKey = privateKeyPem.replace("\\n", "\n");
            PEMParser pemParser = new PEMParser(new StringReader(formattedKey));
            PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();
            PrivateKey privateKey = new JcaPEMKeyConverter().getKeyPair(pemKeyPair).getPrivate();

            Instant now = Instant.now();
            return JWT.create()
                    .withIssuer(appId)
                    .withIssuedAt(Date.from(now.minusSeconds(60)))
                    .withExpiresAt(Date.from(now.plusSeconds(540)))
                    .sign(Algorithm.RSA256(null, (RSAPrivateKey) privateKey));
        } catch (Exception e) {
            throw new RuntimeException("GitHub App JWT 생성 실패", e);
        }
    }

    // repo 기준으로 installation token 발급
    public String getInstallationToken(String owner, String repo) {
        String jwt = generateJwt();

        // 1. installation id 조회
        Map<String, Object> installation = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/installation")
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        Number installationId = (Number) installation.get("id");

        // 2. installation token 발급
        Map<String, Object> tokenResponse = webClientBuilder.build()
                .post()
                .uri("https://api.github.com/app/installations/" + installationId + "/access_tokens")
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        return (String) tokenResponse.get("token");
    }

    // PR diff 가져오기
    public String getPrDiff(String owner, String repo, int prNumber) {
        String token = getInstallationToken(owner, repo);

        String diff = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("PR diff: {}", diff);
        return diff;
    }

    public String getFileContent(String owner, String repo, String path, String ref) {
        String token = getInstallationToken(owner, repo);

        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        String encoded = (String) response.get("content");
        String content = new String(Base64.getDecoder().decode(encoded.replaceAll("\\s", "")));
        log.info("파일 내용 [{}]:\n{}", path, content);
        return content;
    }

    // GitHub PR에 리뷰 코멘트 등록
    public void postReviewComment(String owner, String repo, int prNumber, String body) {
        String token = getInstallationToken(owner, repo);

        webClientBuilder.build()
                .post()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .bodyValue(Map.of(
                        "body", body,
                        "event", "COMMENT"
                ))
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("GitHub PR 리뷰 등록 완료: PR #{}", prNumber);
    }
}