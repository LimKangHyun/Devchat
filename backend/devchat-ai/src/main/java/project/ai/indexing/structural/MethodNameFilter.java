package project.ai.indexing.structural;

import java.util.Set;
import java.util.regex.Pattern;

/** getter/setter, 흔한 Object 메서드 등 "리뷰 컨텍스트로서 의미 없는" 메서드명 판별. */
public final class MethodNameFilter {

    private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)[A-Z0-9].*");
    private static final Pattern SETTER_PATTERN = Pattern.compile("^set[A-Z0-9].*");
    private static final Set<String> COMMON_OBJECT_METHODS = Set.of(
            "toString", "hashCode", "equals", "clone", "notify", "notifyAll", "wait", "getClass",
            "build", "builder", "of", "from"
    );

    private MethodNameFilter() {}

    public static boolean isMeaningful(String name) {
        if (name == null || name.isBlank()) return false;
        if (GETTER_PATTERN.matcher(name).matches()) return false;
        if (SETTER_PATTERN.matcher(name).matches()) return false;
        return !COMMON_OBJECT_METHODS.contains(name);
    }
}