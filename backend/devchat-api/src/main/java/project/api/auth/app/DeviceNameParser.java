package project.api.auth.app;

/** User-Agent를 "브라우저 · OS" 형태의 기기 이름으로 바꾼다. */
public final class DeviceNameParser {

    private static final String UNKNOWN = "알 수 없는 기기";
    private static final int MAX_LENGTH = 100;

    private DeviceNameParser() {
    }

    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String name = browser(userAgent) + " · " + os(userAgent);
        // device_name 컬럼(varchar 100)을 넘지 않게 하는 안전장치
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }

    // Edge, Chrome UA에도 Safari/가 들어 있어서 순서가 중요하다
    private static String browser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Safari/")) return "Safari";
        return "브라우저";
    }

    // iPhone, iPad UA에도 "Mac OS X"가 들어 있어서 먼저 검사한다
    private static String os(String ua) {
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "Mac";
        if (ua.contains("Linux")) return "Linux";
        return "기타";
    }
}
