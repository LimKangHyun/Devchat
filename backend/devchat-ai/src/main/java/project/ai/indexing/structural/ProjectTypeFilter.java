package project.ai.indexing.structural;

import java.util.List;
import java.util.Set;

/**
 * 참조 타입이 이 인덱스에서 조회할 가치가 있는 "프로젝트 타입"인지 판단한다.
 *
 * 인덱스에는 프로젝트 코드만 있으므로, JDK/외부 라이브러리 타입은 조회해도 항상 0건이다.
 * 판단 근거는 하드코딩 목록이 아니라 "이 파일이 실제로 가진 import"다.
 * 예: ChatRoom을 참조하는데 import에 project.chat.ChatRoom이 있으면 프로젝트 타입.
 *
 * import에 안 잡히는 건 두 가지뿐이다.
 *  1) 같은 패키지 클래스 (import 없이 참조 가능) → 프로젝트 타입일 가능성이 높다
 *  2) java.lang 자동 임포트 (String, Integer 등) → 프로젝트 타입이 아니다
 * 이 둘을 구분하기 위해 java.lang 자동 임포트 타입만 최소 목록으로 배제한다.
 * 이 목록은 언어 명세로 고정되어 있어 더 늘어나지 않는다 (하드코딩 목록의 유지보수 문제가 없다).
 */
public final class ProjectTypeFilter {

    private static final String PROJECT_PACKAGE_PREFIX = "project.";

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

    public static boolean isProjectType(String typeName, List<String> imports) {
        if (typeName == null || typeName.isBlank()) return false;

        // 1) import에서 이 타입의 FQN을 찾을 수 있으면, 그 패키지로 확정 판단.
        String suffix = "." + typeName;
        for (String imp : imports) {
            if (imp.endsWith(suffix)) {
                return imp.startsWith(PROJECT_PACKAGE_PREFIX);
            }
        }

        // 2) import에 없음 = 같은 패키지 클래스이거나 java.lang 자동 임포트.
        //    java.lang 타입이면 프로젝트 타입 아님.
        if (JAVA_LANG_TYPES.contains(typeName)) return false;

        // 3) 나머지는 같은 패키지의 프로젝트 클래스로 간주.
        //    제네릭 파라미터(T, E)나 소문자 시작은 타입명이 아니므로 배제.
        return typeName.length() > 2 && Character.isUpperCase(typeName.charAt(0));
    }
}