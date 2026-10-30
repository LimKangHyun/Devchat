package project.api.domain.feedback.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentSummaryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // 아이폰 사파리
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"
            + "|iPhone · iOS 17.6 · Safari 17",
        // 아이폰 홈 화면 앱(PWA)은 Safari 표시가 없다
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
            + "|iPhone · iOS 18.0 · 홈 화면 앱·앱 내 브라우저",
        // 아이폰 크롬
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.6723.90 Mobile/15E148 Safari/604.1"
            + "|iPhone · iOS 17.5 · Chrome 130",
        // 아이패드
        "Mozilla/5.0 (iPad; CPU OS 16_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.3 Mobile/15E148 Safari/604.1"
            + "|iPad · iPadOS 16.3 · Safari 16",
        // 안드로이드 크롬: 기기 모델명은 빼고 안드로이드 버전만
        "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            + "|Android 14 · Chrome 130",
        // 안드로이드 삼성 인터넷
        "Mozilla/5.0 (Linux; Android 13; SM-G991N) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36"
            + "|Android 13 · Samsung Internet 25",
        // 카카오톡 인앱 브라우저
        "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36 KAKAOTALK 10.8.0"
            + "|Android 14 · 카카오톡 인앱",
        // 윈도우 엣지
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
            + "|Windows · Edge 130",
        // 윈도우 웨일
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Whale/3.28.266.14 Safari/537.36"
            + "|Windows · Whale 3",
        // 맥 사파리
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15"
            + "|Mac · Safari 18",
        // 맥 파이어폭스
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:132.0) Gecko/20100101 Firefox/132.0"
            + "|Mac · Firefox 132",
    })
    void 브라우저_정보를_기기_OS_브라우저로_줄인다(String userAgent, String expected) {
        assertThat(UserAgentSummary.of(userAgent)).isEqualTo(expected);
    }

    @Test
    void 알아볼_수_없으면_알_수_없음() {
        assertThat(UserAgentSummary.of("curl/8.4.0")).isEqualTo("알 수 없음");
    }

    @Test
    void 비어_있으면_null() {
        assertThat(UserAgentSummary.of(null)).isNull();
        assertThat(UserAgentSummary.of("  ")).isNull();
    }
}
