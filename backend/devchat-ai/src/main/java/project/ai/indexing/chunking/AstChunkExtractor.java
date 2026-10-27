package project.ai.indexing.chunking;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
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

    /**
     * @param primaryType 파일의 대표(최상위) 타입. 내부 클래스가 아니라 이 타입이 "이 파일이 바꾼 클래스"다.
     */
    public record ExtractionResult(List<ChunkMeta> chunks, List<String> imports, PrimaryType primaryType) {
        static final ExtractionResult EMPTY = new ExtractionResult(List.of(), List.of(), null);
    }

    /** 파일 대표 타입의 이름과 상속 정보. 이름은 TypeNameResolver 규칙으로 정규화돼 있다. */
    public record PrimaryType(String name, String superClassName, List<String> interfaceNames) {}

    public ExtractionResult extractWithImports(String content, String relativePath) {
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                log.warn("AST 파싱 실패. file={}", relativePath);
                return ExtractionResult.EMPTY;
            }
            CompilationUnit cu = parseResult.getResult().get();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            List<String> imports = cu.getImports().stream()
                    .filter(i -> !i.isAsterisk() && !i.isStatic())
                    .map(i -> i.getNameAsString())
                    .toList();

            Map<ClassOrInterfaceDeclaration, Map<String, Type>> fieldTypeCache =
                    new IdentityHashMap<>();
            TypeNameResolver resolver = new TypeNameResolver(cu);

            List<ChunkMeta> result = new ArrayList<>();
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (method.findAncestor(MethodDeclaration.class).isPresent()) continue;
                String code = method.toString();
                if (code.lines().count() < MIN_METHOD_LINES) continue;
                result.add(buildChunk(method, packageName, fieldTypeCache, resolver));
            }

            return new ExtractionResult(result, imports, primaryType(cu, resolver));
        } catch (Exception e) {
            log.warn("AST 파싱 실패. file={}, error={}", relativePath, e.getMessage());
            return ExtractionResult.EMPTY;
        }
    }

    /** 파일 이름과 같은 최상위 타입, 없으면 첫 최상위 타입. */
    private PrimaryType primaryType(CompilationUnit cu, TypeNameResolver resolver) {
        TypeDeclaration<?> type = cu.getPrimaryType()
                .orElseGet(() -> cu.getTypes().isEmpty() ? null : cu.getType(0));
        if (type == null) return null;
        if (!(type instanceof ClassOrInterfaceDeclaration declaration)) {
            return new PrimaryType(TypeNameResolver.declaredName(type), null, List.of());
        }
        return new PrimaryType(TypeNameResolver.declaredName(declaration),
                superClassName(declaration, resolver), interfaceNames(declaration, resolver));
    }

    private String superClassName(ClassOrInterfaceDeclaration owner, TypeNameResolver resolver) {
        return owner == null || owner.getExtendedTypes().isEmpty()
                ? null : resolver.resolve(owner.getExtendedTypes(0));
    }

    private List<String> interfaceNames(ClassOrInterfaceDeclaration owner, TypeNameResolver resolver) {
        return owner == null ? List.of() : owner.getImplementedTypes().stream()
                .map(resolver::resolve)
                .collect(Collectors.toList());
    }

    private ChunkMeta buildChunk(MethodDeclaration method, String packageName,
                                 Map<ClassOrInterfaceDeclaration, Map<String, Type>> fieldTypeCache,
                                 TypeNameResolver resolver) {
        ClassOrInterfaceDeclaration owner = method.findAncestor(ClassOrInterfaceDeclaration.class)
                .orElse(null);

        // 내부 클래스는 "바깥.내부"로 저장한다. 이름만 쓰면 여러 Builder가 한 이름으로 뭉친다.
        String className = TypeNameResolver.enclosingTypeName(method);
        String superClassName = superClassName(owner, resolver);
        List<String> interfaceNames = interfaceNames(owner, resolver);

        String methodName = method.getNameAsString();
        List<String> parameterTypes = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.toList());
        String methodSignature = methodName + "(" + String.join(", ", parameterTypes) + ")";

        List<String> annotations = method.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .collect(Collectors.toList());

        Map<String, Type> fieldTypes =
                fieldTypeCache.computeIfAbsent(owner, this::buildFieldTypeMap);
        Map<String, Type> localTypes = buildLocalTypeMap(method);
        Set<String> shadowed = localTypes.keySet();

        // 호출된 메서드 + 대상 클래스 힌트. 리시버가 지역변수나 필드면 선언 타입을 그대로 쓰고(확정),
        // 타입을 알 수 없을 때만 이름 관례(userService -> UserService)로 추정한다.
        List<CalledMethodRef> calledMethodRefs =
                extractCalledMethods(method, fieldTypes, localTypes, methodName, resolver);
        List<String> calledMethodNames = calledMethodRefs.stream()
                .map(CalledMethodRef::methodName)
                .distinct()
                .collect(Collectors.toList());

        // 대상 클래스가 확정된 호출만 "클래스.메서드"로 저장. 추정 힌트는 틀릴 수 있어 제외하며,
        // 그 케이스는 조회 시 이름만 매칭하는 폴백 검색이 담당한다.
        List<String> calledMethodQualified = calledMethodRefs.stream()
                .filter(CalledMethodRef::resolved)
                .map(ref -> ref.targetClassHint() + "." + ref.methodName())
                .distinct()
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            long withHint = calledMethodRefs.stream()
                    .filter(r -> r.targetClassHint() != null).count();
            long guessed = calledMethodRefs.stream()
                    .filter(r -> r.targetClassHint() != null && !r.resolved()).count();
            log.debug("[AST] {}.{} 힌트 {}개 중 추정 {}개", className, methodName, withHint, guessed);
        }

        // 메서드 본문에 직접 등장하는 타입 + 본문이 사용하는 "필드의 선언 타입"을 합친다.
        // this.accessTokenCustomizer.customize(...) 처럼 필드 경유 호출은
        // 본문에 타입명이 나타나지 않으므로 필드 선언부에서 역추적해야 한다.
        List<String> referencedTypeNames = new ArrayList<>(new LinkedHashSet<>(
                concat(extractReferencedTypes(method, resolver),
                        resolveFieldTypes(method, fieldTypes, shadowed, resolver))
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
     * 해당 클래스와 바깥 클래스들의 필드 선언을 모아 "필드명 -> 선언 타입" 맵을 만든다.
     * 내부 클래스는 외곽 클래스 필드도 참조할 수 있으므로 바깥쪽부터 채우고 안쪽이 덮어쓴다.
     */
    private Map<String, Type> buildFieldTypeMap(ClassOrInterfaceDeclaration owner) {
        Map<String, Type> map = new LinkedHashMap<>();
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
                    map.put(variable.getNameAsString(), variable.getType());
                }
            }
        }
        return map;
    }

    /**
     * 메서드 파라미터와 지역변수의 "이름 -> 선언 타입" 맵.
     * 자바는 정적 타입이라 선언부에 타입이 명시돼 있으므로, 리시버가 지역변수여도
     * 이름 관례 추정 없이 타입을 확정할 수 있다.
     * var로 선언된 경우는 초기화식(new, 캐스트)에서 타입을 읽고, 읽지 못하면 값을 null로 두되
     * 키는 남겨서 같은 이름의 필드가 잘못 조회되는 것(shadowing)을 막는다.
     * 타입을 명시한 람다 파라미터, catch 파라미터, 패턴 변수(instanceof Order o)도 선언 타입으로 넣는다.
     * keySet()은 기존 collectShadowedNames()와 동일한 shadowed 이름 집합이다.
     */
    private Map<String, Type> buildLocalTypeMap(MethodDeclaration method) {
        Map<String, Type> map = new LinkedHashMap<>();

        method.getParameters().forEach(p -> map.put(p.getNameAsString(), p.getType()));

        method.findAll(VariableDeclarationExpr.class).forEach(expr ->
                expr.getVariables().forEach(v -> map.put(v.getNameAsString(),
                        v.getType().isVarType() ? typeFromInitializer(v) : v.getType())));

        // (o -> ...)처럼 타입을 생략한 파라미터는 UnknownType이라 건너뛰고 이름 관례 추정에 맡긴다.
        method.findAll(LambdaExpr.class).forEach(lambda ->
                lambda.getParameters().forEach(p -> {
                    if (!p.getType().isUnknownType()) {
                        map.put(p.getNameAsString(), p.getType().isVarType() ? null : p.getType());
                    }
                }));

        method.findAll(CatchClause.class).forEach(c ->
                map.put(c.getParameter().getNameAsString(), c.getParameter().getType()));

        // 레코드 패턴(Point(Order o, int n)) 안의 변수도 TypePatternExpr라 함께 수집된다.
        method.findAll(TypePatternExpr.class).forEach(p -> map.put(p.getNameAsString(), p.getType()));

        return map;
    }

    /**
     * var 변수의 타입을 초기화식에서 읽는다. new 생성식과 캐스트만 타입이 노드에 적혀 있다.
     * 메서드 반환값 등 그 외 초기화식은 타입 추론이 필요하므로 null을 돌려준다.
     */
    private Type typeFromInitializer(VariableDeclarator variable) {
        return variable.getInitializer().map(init -> {
            if (init.isObjectCreationExpr()) return (Type) init.asObjectCreationExpr().getType();
            if (init.isCastExpr()) return init.asCastExpr().getType();
            return null;
        }).orElse(null);
    }

    /**
     * 메서드가 실제로 사용한 필드명을 찾아 선언 타입으로 치환한다.
     * 지역변수/파라미터에 가려진(shadowed) 이름은 제외한다.
     */
    private List<String> resolveFieldTypes(
            MethodDeclaration method, Map<String, Type> fieldTypes, Set<String> shadowed,
            TypeNameResolver resolver) {
        if (fieldTypes.isEmpty()) return List.of();

        Set<String> usedFieldNames = extractUsedFieldNames(method, shadowed);
        List<String> resolved = new ArrayList<>();
        for (String name : usedFieldNames) {
            Type type = fieldTypes.get(name);
            if (type != null) {
                resolved.addAll(collectTypeNames(type, resolver));
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

    /** 힌트 판별 결과. hint가 null이면 대상 특정 불가, resolved가 false면 이름 관례 추정값. */
    private record HintResult(String hint, boolean resolved) {
        static final HintResult NONE = new HintResult(null, false);

        /** type이 null이면(범용 타입 등) 힌트 없음. */
        static HintResult declared(String type) {
            return type != null ? new HintResult(type, true) : NONE;
        }

        static HintResult guessed(String receiverName) {
            return new HintResult(capitalizeFirst(receiverName), false);
        }
    }

    /**
     * 메서드 호출부에서 (호출 메서드명, 대상 클래스 힌트, 확정 여부)를 함께 뽑는다.
     */
    private List<CalledMethodRef> extractCalledMethods(
            MethodDeclaration method, Map<String, Type> fieldTypes,
            Map<String, Type> localTypes, String fromMethodName, TypeNameResolver resolver) {

        Set<CalledMethodRef> called = new LinkedHashSet<>();
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr call, Void arg) {
                super.visit(call, arg);
                HintResult hint = resolveTargetClassHint(call, fieldTypes, localTypes, resolver);
                called.add(new CalledMethodRef(
                        call.getNameAsString(), hint.hint(), hint.resolved(), fromMethodName));
            }
        }, null);
        return List.copyOf(called);
    }

    /**
     * 힌트 판별 우선순위:
     *  1) 리시버가 지역변수/파라미터면 그 선언 타입 (확정)
     *  2) 지역변수가 아니고 필드면 필드 선언 타입 (확정)
     *  3) var인데 초기화식에서도 타입을 못 읽었거나 어느 쪽도 아니면 이름 관례로 추정 (부정확할 수 있음)
     *  4) 스코프 없는 호출(this.foo(), 정적 임포트)이나 체이닝 호출은 힌트 없음
     * 1), 2)에서 선언 타입이 범용 타입(String, List 등)·타입 변수·배열이면 프로젝트 클래스가
     * 아니므로 이름으로 추정하지 않고 힌트 없음으로 둔다.
     */
    private HintResult resolveTargetClassHint(
            MethodCallExpr call, Map<String, Type> fieldTypes,
            Map<String, Type> localTypes, TypeNameResolver resolver) {

        return call.getScope().map(scope -> {
            String receiverName;
            if (scope.isNameExpr()) {
                receiverName = scope.asNameExpr().getNameAsString();
            } else if (scope.isFieldAccessExpr()) {
                receiverName = scope.asFieldAccessExpr().getNameAsString();
            } else if (scope.isMethodCallExpr()) {
                // 체이닝(a.b().c()): 앞 호출의 반환 타입을 빌더 패턴 관례로 추정한다. 못 하면 힌트 없음.
                String chained = inferReturnType(scope.asMethodCallExpr(), fieldTypes, localTypes, resolver);
                return chained != null ? new HintResult(chained, false) : HintResult.NONE;
            } else if (scope.isObjectCreationExpr()) {
                // new Foo().bar(): 생성한 타입이 곧 리시버 타입이라 확정이다.
                return HintResult.declared(hintTypeName(scope.asObjectCreationExpr().getType(), resolver));
            } else {
                return HintResult.NONE;
            }
            if (receiverName.isEmpty()) return HintResult.NONE;

            if (localTypes.containsKey(receiverName)) {
                // 지역변수가 필드를 가리므로, var여서 타입을 몰라도 필드는 조회하지 않는다.
                Type localDeclared = localTypes.get(receiverName);
                if (localDeclared == null) return HintResult.guessed(receiverName);
                return HintResult.declared(hintTypeName(localDeclared, resolver));
            }

            Type fieldDeclared = fieldTypes.get(receiverName);
            if (fieldDeclared != null) {
                return HintResult.declared(hintTypeName(fieldDeclared, resolver));
            }
            // 대문자로 시작하면 변수가 아니라 타입을 직접 쓴 정적 호출(Outer.Inner.foo())로 보고,
            // 이름 관례 추정 대신 타입 이름 규칙으로 정규화한다. 확정 여부는 기존처럼 추정으로 둔다.
            if (Character.isUpperCase(receiverName.charAt(0))) {
                return new HintResult(resolver.resolvePath(scope.toString(), call), false);
            }
            return HintResult.guessed(receiverName);
        }).orElse(HintResult.NONE);
    }

    /**
     * 체인 중간 호출의 반환 타입을 추정한다. 타입 추론 없이 알 수 있는 건 빌더 패턴뿐이라 그것만 다룬다.
     * ClientSettings.withSettings(m).requireProofKey(true).build()처럼 설정 객체를 만드는 호출부가
     * 대부분 이 형태라, 추정하지 않으면 build()의 호출자를 이름으로만 찾게 된다.
     * 관례에 기댄 값이라 호출부에서는 추정(resolved=false)으로 저장한다. 모르면 null.
     */
    private String inferReturnType(MethodCallExpr call, Map<String, Type> fieldTypes,
                                   Map<String, Type> localTypes, TypeNameResolver resolver) {
        Expression scope = call.getScope().orElse(null);
        if (scope == null) return null;

        String receiverType = null;
        boolean staticReceiver = false;
        if (scope.isMethodCallExpr()) {
            receiverType = inferReturnType(scope.asMethodCallExpr(), fieldTypes, localTypes, resolver);
        } else if (scope.isObjectCreationExpr()) {
            receiverType = hintTypeName(scope.asObjectCreationExpr().getType(), resolver);
        } else if (scope.isNameExpr() || scope.isFieldAccessExpr()) {
            String name = scope.isNameExpr()
                    ? scope.asNameExpr().getNameAsString() : scope.asFieldAccessExpr().getNameAsString();
            Type declared = localTypes.containsKey(name) ? localTypes.get(name) : fieldTypes.get(name);
            if (declared != null) {
                receiverType = hintTypeName(declared, resolver);
            } else if (!localTypes.containsKey(name) && !name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                receiverType = resolver.resolvePath(scope.toString(), call);
                staticReceiver = true;
            }
        }
        if (receiverType == null) return null;
        return returnTypeByBuilderConvention(receiverType, call.getNameAsString(), staticReceiver);
    }

    /**
     * 빌더 패턴 관례:
     *  - 타입의 정적 팩토리 builder()·with*() → 그 타입의 Builder (ClientSettings.withSettings → ClientSettings.Builder)
     *  - Builder의 build()가 아닌 메서드 → 같은 Builder (fluent setter)
     *  - Builder의 build() → 바깥 타입 (ClientSettings.Builder → ClientSettings, JwtClaimsSetBuilder → JwtClaimsSet)
     */
    static String returnTypeByBuilderConvention(String receiverType, String method, boolean staticReceiver) {
        if (staticReceiver && (method.equals("builder") || method.startsWith("with"))) {
            return receiverType + ".Builder";
        }
        String simple = receiverType.substring(receiverType.lastIndexOf('.') + 1);
        if (!simple.endsWith("Builder")) return null;
        if (!method.equals("build")) return receiverType;
        if (receiverType.endsWith(".Builder")) {
            return receiverType.substring(0, receiverType.length() - ".Builder".length());
        }
        String built = receiverType.substring(0, receiverType.length() - "Builder".length());
        return built.isEmpty() || built.endsWith(".") ? null : built;
    }

    /**
     * 대상 클래스 힌트로 쓸 바깥 타입 이름. Map&lt;String, Order&gt;는 Order가 아니라 Map이고,
     * java.util.List는 패키지(util)가 아니라 List다. 범용 타입이나 타입 변수(T)면 null.
     * 내부 클래스는 "바깥.내부"로 정규화한다(Builder → OAuth2TokenStatus.Builder).
     * collectTypeNames()는 제네릭 인자까지 모으므로 힌트에 쓰면 안 된다.
     */
    private String hintTypeName(Type type, TypeNameResolver resolver) {
        if (!type.isClassOrInterfaceType()) return null; // 원시 타입, 배열 등
        String name = resolver.resolve(type.asClassOrInterfaceType());
        return isMeaningfulTypeName(name) ? name : null;
    }

    private static String capitalizeFirst(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private List<String> extractReferencedTypes(MethodDeclaration method, TypeNameResolver resolver) {
        List<String> types = new ArrayList<>();

        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceType type, Void arg) {
                super.visit(type, arg);
                if (isScopeOfAnotherType(type)) return;
                String name = resolver.resolve(type);
                if (isMeaningfulTypeName(name)) {
                    types.add(name);
                }
            }
        }, null);

        return types.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 제네릭 인자까지 포함해 타입명을 수집한다. 내부 클래스는 "바깥.내부"로 정규화한다.
     * OAuth2TokenCustomizer&lt;OAuth2TokenClaimsContext&gt; -> [OAuth2TokenCustomizer, OAuth2TokenClaimsContext]
     */
    private List<String> collectTypeNames(Type type, TypeNameResolver resolver) {
        return type.findAll(ClassOrInterfaceType.class).stream()
                .filter(t -> !isScopeOfAnotherType(t))
                .map(resolver::resolve)
                .filter(this::isMeaningfulTypeName)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * OAuth2Authorization.Token에서 OAuth2Authorization처럼 다른 타입 참조의 앞부분인지.
     * 앞부분은 전체 이름(OAuth2Authorization.Token)에 이미 들어 있으므로 따로 세지 않는다.
     * java.util.List의 util 같은 패키지 조각이 타입으로 잡히던 것도 함께 막는다.
     */
    private static boolean isScopeOfAnotherType(ClassOrInterfaceType type) {
        return type.getParentNode()
                .filter(p -> p instanceof ClassOrInterfaceType parent
                        && parent.getScope().filter(s -> s == type).isPresent())
                .isPresent();
    }

    /** 정규화된 이름(Outer.Inner)이면 마지막 이름으로 판단한다. */
    private boolean isMeaningfulTypeName(String name) {
        String simple = name.substring(name.lastIndexOf('.') + 1);
        return simple.length() > 1 && !IGNORED_TYPE_NAMES.contains(simple);
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }
}