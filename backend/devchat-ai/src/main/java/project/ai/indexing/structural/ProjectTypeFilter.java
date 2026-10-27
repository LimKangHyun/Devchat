package project.ai.indexing.structural;

import java.util.List;
import java.util.Set;

/**
 * 참조 타입이 이 인덱스에서 조회할 가치가 있는 "프로젝트 타입"인지 판단한다.
 *
 * 인덱스에는 프로젝트 코드만 있으므로, JDK/외부 라이브러리 타입은 조회해도 항상 0건이다.
 * 판단 근거는 하드코딩 목록이 아니라 "이 파일이 실제로 가진 import"와 "이 파일의 패키지"다.
 * import한 타입의 패키지가 리뷰 파일의 패키지와 저장소 루트(sharesProjectRoot)를 공유하면 같은 저장소의 코드로 본다.
 * 예: 리뷰 파일이 org.springframework.security.oauth2.server.authorization.authentication이면
 *     org.springframework.security.oauth2.server.authorization.OAuth2Authorization은 프로젝트 타입,
 *     org.springframework.util.Assert는 외부 타입.
 * 예전에는 DevChat 자체의 패키지(project.)로 고정 비교해서, 다른 패키지를 쓰는 사용자 저장소에서는
 * import한 프로젝트 타입이 전부 외부 타입으로 걸러졌다.
 *
 * import에 안 잡히는 건 두 가지뿐이다.
 *  1) 같은 패키지 클래스 (import 없이 참조 가능) → 프로젝트 타입일 가능성이 높다
 *  2) java.lang 자동 임포트 (String, Integer 등) → 프로젝트 타입이 아니다
 * 이 둘을 구분하기 위해 java.lang 자동 임포트 타입만 최소 목록으로 배제한다.
 * 이 목록은 언어 명세로 고정되어 있어 더 늘어나지 않는다 (하드코딩 목록의 유지보수 문제가 없다).
 */
public final class ProjectTypeFilter {

    /**
     * 도메인 역순 패키지(org.springframework.security)에서 같은 저장소로 볼 공통 앞부분의 단계 수.
     * 2(org.springframework)면 같은 회사의 다른 라이브러리(org.springframework.util)까지 프로젝트 타입이 된다.
     * 리뷰 파일 패키지가 더 짧으면(예: com.example) 그 길이까지만 비교한다.
     */
    private static final int REVERSE_DOMAIN_ROOT_DEPTH = 3;

    /**
     * 도메인 역순이 아닌 패키지(project.ai, project.common)는 첫 단계가 저장소 이름 역할을 하고,
     * 둘째 단계부터 모듈마다 갈리므로 첫 단계만 비교한다.
     */
    private static final int PLAIN_ROOT_DEPTH = 1;

    /** 도메인 역순 패키지의 첫 단계로 흔한 최상위 도메인. */
    private static final Set<String> TOP_LEVEL_DOMAINS = Set.of(
            "com", "org", "net", "io", "dev", "app", "co", "me", "edu", "gov",
            "kr", "jp", "cn", "de", "uk", "fr", "in", "us", "eu"
    );

    /**
     * import 없이 쓸 수 있는 java.lang 자동 임포트 타입.
     * import로 잡히지 않으므로 별도 배제가 필요하다. 언어 명세상 고정 목록.
     */
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Integer", "Long", "Boolean", "Double", "Float",
            "Byte", "Short", "Character", "Object", "Number", "Void",
            "Class", "Thread", "Runnable", "Math", "System", "Comparable",
            "Iterable", "CharSequence", "StringBuilder", "StringBuffer",
            "Exception", "RuntimeException", "Error", "Throwable",
            "IllegalArgumentException", "IllegalStateException",
            "NullPointerException", "Override", "Deprecated", "SuppressWarnings"
    );

    private ProjectTypeFilter() {}

    /**
     * @param typeName    참조 타입 이름. 내부 클래스면 "Outer.Inner" 형태다.
     * @param imports     리뷰 파일의 import FQN 목록
     * @param ownPackage  리뷰 파일의 패키지
     */
    public static boolean isProjectType(String typeName, List<String> imports, String ownPackage) {
        if (typeName == null || typeName.isBlank()) return false;

        // 1) import에서 이 타입의 FQN을 찾을 수 있으면, 그 패키지로 확정 판단.
        //    Outer.Inner는 import pkg.Outer.Inner로 가져왔을 수도, import pkg.Outer로 가져왔을 수도 있다.
        String head = typeName.contains(".") ? typeName.substring(0, typeName.indexOf('.')) : typeName;
        for (String imp : imports) {
            if (imp.endsWith("." + typeName) || imp.endsWith("." + head)) {
                return sharesProjectRoot(packageOf(imp), ownPackage);
            }
        }

        // 2) import에 없음 = 같은 패키지 클래스이거나 java.lang 자동 임포트.
        //    java.lang 타입이면 프로젝트 타입 아님.
        if (JAVA_LANG_TYPES.contains(head)) return false;

        // 3) 나머지는 같은 패키지의 프로젝트 클래스로 간주.
        //    제네릭 파라미터(T, E)나 소문자 시작은 타입명이 아니므로 배제.
        return head.length() > 2 && Character.isUpperCase(head.charAt(0));
    }

    /** import FQN에서 패키지 부분. 대문자로 시작하는 첫 조각부터는 타입 경로다. */
    static String packageOf(String fqn) {
        String[] parts = fqn.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) break;
            if (sb.length() > 0) sb.append('.');
            sb.append(part);
        }
        return sb.toString();
    }

    /** 두 패키지가 저장소 루트(리뷰 파일 패키지가 더 짧으면 그만큼)를 공유하는지. */
    static boolean sharesProjectRoot(String importPackage, String ownPackage) {
        if (ownPackage == null || ownPackage.isBlank() || importPackage.isBlank()) return false;
        String[] own = ownPackage.split("\\.");
        String[] other = importPackage.split("\\.");
        int rootDepth = TOP_LEVEL_DOMAINS.contains(own[0]) ? REVERSE_DOMAIN_ROOT_DEPTH : PLAIN_ROOT_DEPTH;
        int depth = Math.min(rootDepth, own.length);
        if (other.length < depth) return false;
        for (int i = 0; i < depth; i++) {
            if (!own[i].equals(other[i])) return false;
        }
        return true;
    }
}