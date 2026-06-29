package project.backend.domain.github;

import java.net.URI;
import java.net.URISyntaxException;
import project.backend.global.exception.errorcode.GitHubErrorCode;
import project.backend.global.exception.ex.GitHubException;
import project.common.dto.github.GitRepoDto;

public class GitRepoUrlUtils {

    public static GitRepoDto validateAndParseUrl(String url) {
        try {
            URI uri = new URI(url);

            if (!"github.com".equals(uri.getHost())) {
                throw new GitHubException(GitHubErrorCode.INVALID_REPO_RUL);
            }

            // 2. 경로 세그먼트 체크
            String[] segments = uri.getPath().split("/");

            if (segments.length != 3 || segments[1].isBlank() || segments[2].isBlank()) {
                throw new GitHubException(GitHubErrorCode.INVALID_REPO_RUL);
            }

            return new GitRepoDto(segments[1], segments[2]);

        } catch (URISyntaxException e) {
            throw new GitHubException(GitHubErrorCode.INVALID_REPO_RUL);
        }
    }
}
