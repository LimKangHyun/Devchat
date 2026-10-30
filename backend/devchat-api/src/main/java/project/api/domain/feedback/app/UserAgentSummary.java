package project.api.domain.feedback.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 브라우저 정보(User-Agent)를 "iPhone · iOS 17.6 · Safari 17"처럼 줄인다.
 * 피드백 이슈는 공개 저장소에 올라가서, 버그를 보는 데 필요한 만큼만 남긴다
 * (안드로이드 기기 모델명, OS 세부 버전, 빌드 번호는 뺀다).
 */
final class UserAgentSummary {

    static final String UNKNOWN = "알 수 없음";

    private static final Pattern IOS_VERSION = Pattern.compile("OS (\\d+)_(\\d+)");
    private static final Pattern ANDROID_VERSION = Pattern.compile("Android (\\d+)");

    // 위에서부터 먼저 맞는 것을 쓴다. 다른 브라우저 UA에도 Chrome/Safari가 들어 있어서 순서가 중요하다
    private static final List<Browser> BROWSERS = List.of(
            new Browser("카카오톡 인앱", "KAKAOTALK", false),
            new Browser("네이버 앱", "NAVER\\(", false),
            new Browser("Instagram 인앱", "Instagram", false),
            new Browser("Samsung Internet", "SamsungBrowser/(\\d+)", true),
            new Browser("Whale", "Whale/(\\d+)", true),
            new Browser("Edge", "Edg(?:A|iOS)?/(\\d+)", true),
            new Browser("Opera", "OPR/(\\d+)", true),
            new Browser("Chrome", "CriOS/(\\d+)", true),
            new Browser("Firefox", "FxiOS/(\\d+)", true),
            new Browser("Firefox", "Firefox/(\\d+)", true),
            new Browser("Chrome", "Chrome/(\\d+)", true),
            new Browser("Safari", "Version/(\\d+)[^ ]* (?:Mobile/\\S+ )?Safari/", true)
    );

    private UserAgentSummary() {
    }

    static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        String ua = userAgent.strip();

        List<String> parts = new ArrayList<>(platform(ua));
        String browser = browser(ua);
        if (browser != null) parts.add(browser);
        else if (ua.contains("iPhone") || ua.contains("iPad")) parts.add("홈 화면 앱·앱 내 브라우저");

        return parts.isEmpty() ? UNKNOWN : String.join(" · ", parts);
    }

    private static List<String> platform(String ua) {
        if (ua.contains("iPhone")) return List.of("iPhone", "iOS " + iosVersion(ua));
        if (ua.contains("iPad")) return List.of("iPad", "iPadOS " + iosVersion(ua));
        Matcher android = ANDROID_VERSION.matcher(ua);
        if (android.find()) return List.of("Android " + android.group(1));
        if (ua.contains("Android")) return List.of("Android");
        if (ua.contains("Windows")) return List.of("Windows");
        if (ua.contains("CrOS")) return List.of("ChromeOS");
        if (ua.contains("Macintosh") || ua.contains("Mac OS X")) return List.of("Mac");
        if (ua.contains("Linux")) return List.of("Linux");
        return List.of();
    }

    private static String iosVersion(String ua) {
        Matcher m = IOS_VERSION.matcher(ua);
        return m.find() ? m.group(1) + "." + m.group(2) : "";
    }

    private static String browser(String ua) {
        for (Browser b : BROWSERS) {
            Matcher m = b.pattern().matcher(ua);
            if (m.find()) return b.withVersion() ? b.name() + " " + m.group(1) : b.name();
        }
        return null;
    }

    private record Browser(String name, Pattern pattern, boolean withVersion) {
        Browser(String name, String regex, boolean withVersion) {
            this(name, Pattern.compile(regex), withVersion);
        }
    }
}
