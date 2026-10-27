package project.ai.indexing.chunking;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 타입 이름을 "바깥클래스.내부클래스" 형태로 정규화한다. 패키지는 붙이지 않는다.
 *
 * 내부 클래스를 이름만으로 저장하면 OAuth2TokenStatus.Builder와 RegisteredClient.Builder가
 * 같은 "Builder"가 되어, 한쪽의 build() 호출자를 찾을 때 모든 Builder의 build() 호출부가
 * 확정 호출자로 잡힌다. 선언과 참조를 같은 규칙으로 정규화해야 저장값과 검색값이 맞는다.
 *
 * 다른 파일의 정보 없이 판단할 수 있는 범위만 정규화한다.
 *  - 같은 파일에 선언된 내부 클래스: 바깥 클래스 이름을 붙인다.
 *  - import pkg.Outer.Inner: Inner를 Outer.Inner로 바꾼다.
 *  - Outer.Inner처럼 명시적으로 쓴 참조: 앞쪽 패키지 경로만 뗀다.
 * 부모 클래스에서 상속받은 내부 클래스나 와일드카드 import는 알 수 없어 이름만 남는다.
 */
public final class TypeNameResolver {

    /** import로 들여온 내부 클래스. 단순 이름 → Outer.Inner */
    private final Map<String, String> importedNestedTypes = new HashMap<>();

    public TypeNameResolver(CompilationUnit cu) {
        cu.getImports().stream()
                .filter(i -> !i.isAsterisk() && !i.isStatic())
                .forEach(i -> {
                    String typePath = stripPackage(i.getNameAsString());
                    if (typePath.contains(".")) {
                        importedNestedTypes.put(typePath.substring(typePath.lastIndexOf('.') + 1), typePath);
                    }
                });
    }

    /** 선언된 타입의 정규화 이름. 바깥 타입들을 바깥쪽부터 이어 붙인다. */
    public static String declaredName(TypeDeclaration<?> declaration) {
        Deque<String> names = new ArrayDeque<>();
        Node current = declaration;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) names.push(type.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        return String.join(".", names);
    }

    /** 노드를 감싸는 가장 가까운 타입 선언의 정규화 이름. 없으면 빈 문자열. */
    public static String enclosingTypeName(Node node) {
        return nearestType(node).map(TypeNameResolver::declaredName).orElse("");
    }

    /**
     * 타입 참조의 정규화 이름. java.util.List → List, OAuth2Authorization.Token → OAuth2Authorization.Token,
     * OAuth2TokenStatus 안에서 쓴 Builder → OAuth2TokenStatus.Builder.
     */
    public String resolve(ClassOrInterfaceType type) {
        List<String> parts = new ArrayList<>();
        ClassOrInterfaceType current = type;
        while (current != null) {
            parts.add(0, current.getNameAsString());
            current = current.getScope().orElse(null);
        }
        return resolvePath(String.join(".", parts), type);
    }

    /**
     * 점으로 이어진 이름(정적 호출의 리시버 등)을 정규화한다. 맨 앞 단순 이름만 사용 위치 기준으로
     * 바깥 이름을 붙이고, 나머지는 그대로 둔다.
     */
    public String resolvePath(String dottedName, Node context) {
        String path = stripPackage(dottedName);
        if (path.isEmpty()) return dottedName;
        int dot = path.indexOf('.');
        String head = dot < 0 ? path : path.substring(0, dot);
        String rest = dot < 0 ? "" : path.substring(dot);
        return resolveSimple(head, context) + rest;
    }

    /**
     * 단순 이름을 사용 위치에서 보이는 타입으로 바꾼다. 안쪽 타입부터 바깥쪽으로 올라가며
     * 그 타입 자신 또는 그 타입의 내부 타입 중 같은 이름을 찾는다(자바의 이름 가림 규칙과 같은 순서).
     */
    private String resolveSimple(String name, Node context) {
        Optional<TypeDeclaration<?>> type = nearestType(context);
        while (type.isPresent()) {
            TypeDeclaration<?> t = type.get();
            if (t.getNameAsString().equals(name)) return declaredName(t);
            for (BodyDeclaration<?> member : t.getMembers()) {
                if (member instanceof TypeDeclaration<?> nested && nested.getNameAsString().equals(name)) {
                    return declaredName(nested);
                }
            }
            type = t.getParentNode().flatMap(TypeNameResolver::nearestTypeIncludingSelf);
        }
        return importedNestedTypes.getOrDefault(name, name);
    }

    @SuppressWarnings("unchecked")
    private static Optional<TypeDeclaration<?>> nearestType(Node node) {
        return node.findAncestor(TypeDeclaration.class).map(t -> (TypeDeclaration<?>) t);
    }

    private static Optional<TypeDeclaration<?>> nearestTypeIncludingSelf(Node node) {
        if (node instanceof TypeDeclaration<?> t) return Optional.of(t);
        return nearestType(node);
    }

    /** 소문자로 시작하는 앞쪽 경로(패키지)를 뗀다. org.example.Outer.Inner → Outer.Inner */
    private static String stripPackage(String dottedName) {
        String[] parts = dottedName.split("\\.");
        int first = 0;
        while (first < parts.length - 1 && !parts[first].isEmpty()
                && Character.isLowerCase(parts[first].charAt(0))) {
            first++;
        }
        return String.join(".", java.util.Arrays.copyOfRange(parts, first, parts.length));
    }
}
