package project.ai.indexing.chunking;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AstChunkExtractor {

    private static final int MIN_METHOD_LINES = 3;

    /**
     * 검색 변별력이 없는 범용 타입. 기존 if-chain을 Set으로 옮긴 것으로 대상은 동일하다.
     */
    private static final Set<String> IGNORED_TYPE_NAMES = Set.of(
        "String", "List", "Map", "Set", "Optional", "Object",
        "Class", "Collection", "Integer", "Long", "Boolean", "Exception"
    );

    private final JavaParser javaParser;

    public List<ChunkMeta> extract(String content, String relativePath) {
        return extractWithImports(content, relativePath).chunks();
    }

    public record ExtractionResult(List<ChunkMeta> chunks, List<String> imports) {}

    public ExtractionResult extractWithImports(String content, String relativePath) {
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                log.warn("AST 파싱 실패. file={}", relativePath);
                return new ExtractionResult(List.of(), List.of());
            }
            CompilationUnit cu = parseResult.getResult().get();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            List<String> imports = cu.getImports().stream()
                    .filter(i -> !i.isAsterisk() && !i.isStatic())
                    .map(i -> i.getNameAsString())
                    .toList();

            Map<ClassOrInterfaceDeclaration, Map<String, List<String>>> fieldTypeCache =
                    new IdentityHashMap<>();

            List<ChunkMeta> result = new ArrayList<>();
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (method.findAncestor(MethodDeclaration.class).isPresent()) continue;
                String code = method.toString();
                if (code.lines().count() < MIN_METHOD_LINES) continue;
                result.add(buildChunk(method, packageName, fieldTypeCache));
            }

            return new ExtractionResult(result, imports);
        } catch (Exception e) {
            log.warn("AST 파싱 실패. file={}, error={}", relativePath, e.getMessage());
            return new ExtractionResult(List.of(), List.of());
        }
    }

    private ChunkMeta buildChunk(MethodDeclaration method, String packageName,
        Map<ClassOrInterfaceDeclaration, Map<String, List<String>>> fieldTypeCache) {
        ClassOrInterfaceDeclaration owner = method.findAncestor(ClassOrInterfaceDeclaration.class)
            .orElse(null);

        String className = owner != null ? owner.getNameAsString() : "";
        String superClassName = (owner != null && !owner.getExtendedTypes().isEmpty())
            ? owner.getExtendedTypes(0).getNameAsString()
            : null;
        List<String> interfaceNames = owner != null
            ? owner.getImplementedTypes().stream()
            .map(ClassOrInterfaceType::getNameAsString)
            .collect(Collectors.toList())
            : List.of();

        String methodName = method.getNameAsString();
        List<String> parameterTypes = method.getParameters().stream()
            .map(p -> p.getType().asString())
            .collect(Collectors.toList());
        String methodSignature = methodName + "(" + String.join(", ", parameterTypes) + ")";

        List<String> annotations = method.getAnnotations().stream()
            .map(AnnotationExpr::getNameAsString)
            .collect(Collectors.toList());

        Map<String, List<String>> fieldTypes =
            fieldTypeCache.computeIfAbsent(owner, this::buildFieldTypeMap);
        Set<String> shadowed = collectShadowedNames(method);

        // 호출된 메서드 + 대상 클래스 힌트. 리시버가 필드면 선언 타입을 확정 정보로 사용하고,
        // 지역변수 등 필드가 아니면 이름 관례(userService -> UserService)로 추정한다.
        List<CalledMethodRef> calledMethodRefs = extractCalledMethods(method, fieldTypes, shadowed);
        List<String> calledMethodNames = calledMethodRefs.stream()
            .map(CalledMethodRef::methodName)
            .distinct()
            .collect(Collectors.toList());

        // [추가] 대상 클래스가 추정된 호출만 "클래스.메서드" 형태로 저장 (호출자 검색용)
        List<String> calledMethodQualified = calledMethodRefs.stream()
            .filter(ref -> ref.targetClassHint() != null)
            .map(ref -> ref.targetClassHint() + "." + ref.methodName())
            .distinct()
            .collect(Collectors.toList());

        // 메서드 본문에 직접 등장하는 타입 + 본문이 사용하는 "필드의 선언 타입"을 합친다.
        // this.accessTokenCustomizer.customize(...) 처럼 필드 경유 호출은
        // 본문에 타입명이 나타나지 않으므로 필드 선언부에서 역추적해야 한다.
        List<String> referencedTypeNames = new ArrayList<>(new LinkedHashSet<>(
            concat(extractReferencedTypes(method), resolveFieldTypes(method, fieldTypes, shadowed))
        ));

        return new ChunkMeta(
            null, null, -1, method.toString(),
            className, methodName, methodSignature,
            packageName, superClassName, interfaceNames,
            calledMethodNames, calledMethodRefs, calledMethodQualified,
            referencedTypeNames, annotations
        );
    }

    /**
     * 해당 클래스와 바깥 클래스들의 필드 선언을 모아 "필드명 -> 타입명 목록" 맵을 만든다.
     * 내부 클래스는 외곽 클래스 필드도 참조할 수 있으므로 바깥쪽부터 채우고 안쪽이 덮어쓴다.
     */
    private Map<String, List<String>> buildFieldTypeMap(ClassOrInterfaceDeclaration owner) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (owner == null) return map;

        Deque<ClassOrInterfaceDeclaration> chain = new ArrayDeque<>();
        ClassOrInterfaceDeclaration current = owner;
        while (current != null) {
            chain.push(current);
            current = current.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        }

        for (ClassOrInterfaceDeclaration declaration : chain) {
            for (FieldDeclaration field : declaration.getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    List<String> typeNames = collectTypeNames(variable.getType());
                    if (!typeNames.isEmpty()) {
                        map.put(variable.getNameAsString(), typeNames);
                    }
                }
            }
        }
        return map;
    }

    /**
     * 메서드 안에서 지역변수/파라미터로 선언된 이름 집합.
     * 같은 이름의 필드가 있어도 이 안에서는 지역변수가 우선(shadow)하므로,
     * 필드 조회(선언 타입 확정) 대상에서 제외하는 데 쓴다.
     */
    private Set<String> collectShadowedNames(MethodDeclaration method) {
        Set<String> shadowed = new HashSet<>();
        method.getParameters().forEach(p -> shadowed.add(p.getNameAsString()));
        method.findAll(VariableDeclarationExpr.class)
            .forEach(expr -> expr.getVariables()
                .forEach(v -> shadowed.add(v.getNameAsString())));
        return shadowed;
    }

    /**
     * 메서드가 실제로 사용한 필드명을 찾아 선언 타입으로 치환한다.
     * 지역변수/파라미터에 가려진(shadowed) 이름은 제외한다.
     */
    private List<String> resolveFieldTypes(
        MethodDeclaration method, Map<String, List<String>> fieldTypes, Set<String> shadowed) {
        if (fieldTypes.isEmpty()) return List.of();

        Set<String> usedFieldNames = extractUsedFieldNames(method, shadowed);
        List<String> resolved = new ArrayList<>();
        for (String name : usedFieldNames) {
            List<String> types = fieldTypes.get(name);
            if (types != null) {
                resolved.addAll(types);
            }
        }
        return resolved.stream().distinct().collect(Collectors.toList());
    }

    private Set<String> extractUsedFieldNames(MethodDeclaration method, Set<String> shadowed) {
        Set<String> used = new LinkedHashSet<>();
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(FieldAccessExpr expr, Void arg) {
                super.visit(expr, arg);
                // this.field / Outer.this.field 는 지역변수에 가려지지 않는다.
                used.add(expr.getNameAsString());
            }

            @Override
            public void visit(NameExpr expr, Void arg) {
                super.visit(expr, arg);
                String name = expr.getNameAsString();
                if (!shadowed.contains(name)) {
                    used.add(name);
                }
            }
        }, null);
        return used;
    }

    /**
     * 메서드 호출부에서 (호출 메서드명, 대상 클래스 힌트)를 함께 뽑는다.
     * 힌트 판별 우선순위:
     *  1) 리시버가 필드로 확인되면 그 선언 타입을 그대로 사용 (확정 정보)
     *  2) 필드가 아니면(지역변수/파라미터 등) "변수명 -> 클래스명" 관례로 추정
     *     (예: userService -> UserService)
     *  3) 스코프가 없는 호출(this.foo(), 정적 임포트 등)이나 체이닝 호출은 힌트 없음(null)
     */
    private List<CalledMethodRef> extractCalledMethods(
        MethodDeclaration method, Map<String, List<String>> fieldTypes, Set<String> shadowed) {

        Set<CalledMethodRef> called = new LinkedHashSet<>();
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr call, Void arg) {
                super.visit(call, arg);
                String methodName = call.getNameAsString();
                String targetClassHint = resolveTargetClassHint(call, fieldTypes, shadowed);
                called.add(new CalledMethodRef(methodName, targetClassHint));
            }
        }, null);
        return List.copyOf(called);
    }

    private String resolveTargetClassHint(
        MethodCallExpr call, Map<String, List<String>> fieldTypes, Set<String> shadowed) {

        return call.getScope().map(scope -> {
            String receiverName;
            if (scope.isNameExpr()) {
                receiverName = scope.asNameExpr().getNameAsString();
            } else if (scope.isFieldAccessExpr()) {
                receiverName = scope.asFieldAccessExpr().getNameAsString();
            } else {
                return null; // 체이닝 호출(a.b().c()) 등은 대상 특정 안 함
            }
            if (receiverName.isEmpty()) return null;

            if (!shadowed.contains(receiverName)) {
                List<String> declaredTypes = fieldTypes.get(receiverName);
                if (declaredTypes != null && !declaredTypes.isEmpty()) {
                    return declaredTypes.get(0); // 필드 선언 타입 - 확정 정보
                }
            }
            return capitalizeFirst(receiverName); // 지역변수 등 - 관례 추정 (폴백)
        }).orElse(null);
    }

    private static String capitalizeFirst(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private List<String> extractReferencedTypes(MethodDeclaration method) {
        List<String> types = new ArrayList<>();

        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceType type, Void arg) {
                super.visit(type, arg);
                String name = type.getNameAsString();
                if (isMeaningfulTypeName(name)) {
                    types.add(name);
                }
            }
        }, null);

        return types.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 제네릭 인자까지 포함해 타입명을 수집한다.
     * OAuth2TokenCustomizer&lt;OAuth2TokenClaimsContext&gt; -> [OAuth2TokenCustomizer, OAuth2TokenClaimsContext]
     */
    private List<String> collectTypeNames(Type type) {
        return type.findAll(ClassOrInterfaceType.class).stream()
            .map(ClassOrInterfaceType::getNameAsString)
            .filter(this::isMeaningfulTypeName)
            .distinct()
            .collect(Collectors.toList());
    }

    private boolean isMeaningfulTypeName(String name) {
        return name.length() > 1 && !IGNORED_TYPE_NAMES.contains(name);
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }
}