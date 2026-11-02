package project.ai.client.internal;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 서버 내부 호출 주소(internal.api.url, 환경 변수 INTERNAL_API_URL).
 *
 * 주소가 잘못돼 있으면 서버가 뜨지 않게 한다. 예전에는 값이 틀려도 그대로 떠서,
 * 인덱싱·리뷰 요청이 들어올 때마다 "Host is not specified"로 실패하고서야 드러났다.
 * (호스트 이름에 '_'가 있으면 java.net.URI가 호스트를 읽지 못한다. 예: http://nginx_proxy/...)
 */
@ConfigurationProperties(prefix = "internal.api")
public record InternalApiProperties(String url) {

    static final String ENV_NAME = "INTERNAL_API_URL";

    public InternalApiProperties {
        url = validate(url);
    }

    /** 요청 실패 로그에 남길 호스트(비밀값 없음). */
    public String host() {
        URI uri = URI.create(url);
        return uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
    }

    /** 검증을 통과하면 끝의 '/'를 뗀 주소를 돌려준다. 경로는 "/internal/..."처럼 '/'로 시작해 붙인다. */
    static String validate(String url) {
        if (url == null || url.isBlank() || url.contains("${")) {
            throw invalid(url, "값이 비어 있거나 설정되지 않았습니다");
        }
        String trimmed = url.strip();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw invalid(url, "URL 형식이 아닙니다(" + e.getMessage() + ")");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalid(url, "http:// 또는 https://로 시작해야 합니다");
        }
        if (uri.getHost() == null) {
            String reason = uri.getRawAuthority() != null && uri.getRawAuthority().contains("_")
                ? "호스트 이름에 '_'가 있어 호스트로 읽히지 않습니다. 컨테이너 이름 대신 '-'를 쓴 네트워크 별칭을 쓰세요(예: http://nginx-proxy:8088/internal-api)"
                : "호스트를 읽을 수 없습니다";
            throw invalid(url, reason);
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static IllegalArgumentException invalid(String url, String reason) {
        return new IllegalArgumentException(
            "내부 API 주소 " + ENV_NAME + "(internal.api.url)가 잘못됐습니다: " + reason + ". 현재 값=[" + url + "]");
    }
}
