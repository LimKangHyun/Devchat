package project.api.auth.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceNameParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\"|Chrome · Windows",
            "\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15\"|Safari · Mac",
            "\"Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1\"|Safari · iPhone",
            "\"Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36\"|Chrome · Android",
            "\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0\"|Edge · Windows",
            "\"Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0\"|Firefox · Linux",
            "\"Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1\"|Safari · iPad",
    })
    void parse(String userAgent, String expected) {
        assertThat(DeviceNameParser.parse(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parse_blank(String userAgent) {
        assertThat(DeviceNameParser.parse(userAgent)).isEqualTo("알 수 없는 기기");
    }

    @ParameterizedTest
    @ValueSource(strings = {"curl/8.0"})
    void parse_unknown(String userAgent) {
        assertThat(DeviceNameParser.parse(userAgent)).isEqualTo("브라우저 · 기타");
    }

    @ParameterizedTest
    @ValueSource(ints = {300})
    void parse_truncatesTo100(int len) {
        String ua = "Mozilla/5.0 (Windows NT 10.0) Chrome/120 " + "x".repeat(len);
        assertThat(DeviceNameParser.parse(ua)).hasSizeLessThanOrEqualTo(100);
    }
}
