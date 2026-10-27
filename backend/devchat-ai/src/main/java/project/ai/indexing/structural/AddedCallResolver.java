package project.ai.indexing.structural;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;

/**
 * diff에서 추가된 줄에 있는 메서드 호출만 뽑는다.
 *
 * 기존 "사용호출" 검색은 바뀐 메서드 본문 전체의 호출을 모아 앞에서 몇 개만 검색해서, 호출이 많은
 * 메서드에서는 정작 이번에 새로 부른 메서드가 잘려 나갔다. 새 코드가 의존하는 메서드는 리뷰에 꼭 필요하므로
 * 추가된 줄의 호출을 따로 뽑아 먼저 조회한다.
 *
 * 대상 클래스 힌트
 *  - Foo.bar()처럼 리시버가 클래스 이름(대문자로 시작하고 소문자를 포함)이면 정적 호출로 보고 확정한다.
 *    기존 힌트 판별은 이를 이름 관례 추정으로 분류해 우선순위가 밀렸다.
 *  - 그 밖에는 청킹 단계가 구한 힌트(선언 타입 등)를 같은 메서드 안의 같은 이름 호출에서 가져온다.
 */
@Slf4j
@Component
public class AddedCallResolver {

    private final JavaParser javaParser;

    private static final int MAX_INHERITED_OWNERS = 3;

    /** 대상 없는 호출(get())을 부모 클래스의 메서드로 보고 조회할지. */
    @Value("${rag.structural.inherited-call-search:true}")
    private boolean inheritedCallEnabled;

    public AddedCallResolver(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    /**
     * @param changedLines diff가 추가한 라인 번호 (변경 후 기준)
     * @param knownRefs    파일 전체의 호출 정보. 정적 호출이 아닐 때 힌트를 빌려온다.
     * @return 대상 클래스 힌트가 있는 호출만. 중복 제거.
     */
    public List<CalledMethodRef> resolve(String fileContent, Set<Integer> changedLines,
                                         List<CalledMethodRef> knownRefs, StructuralInfo info) {
        if (fileContent == null || fileContent.isBlank() || changedLines.isEmpty()) return List.of();
        try {
            ParseResult<CompilationUnit> parsed = javaParser.parse(fileContent);
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) return List.of();

            Set<CalledMethodRef> calls = new LinkedHashSet<>();
            for (MethodCallExpr call : parsed.getResult().get().findAll(MethodCallExpr.class)) {
                int line = call.getRange().map(r -> r.begin.line).orElse(-1);
                if (!changedLines.contains(line)) continue;

                String name = call.getNameAsString();
                if (MethodNameFilter.isCommonObjectMethod(name)) continue;
                String fromMethod = call.findAncestor(MethodDeclaration.class)
                    .map(MethodDeclaration::getNameAsString).orElse(null);

                // get()처럼 대상이 없는 호출: 자기 클래스에 없으면 부모 클래스에서 물려받은 메서드다.
                // 후보가 없으면(자기 클래스 메서드, static import 등) 기존처럼 청킹 힌트를 쓴다.
                if (inheritedCallEnabled && isUnqualified(call)) {
                    List<String> owners = inheritedOwners(call, name, info);
                    owners.forEach(owner -> calls.add(new CalledMethodRef(name, owner, false, fromMethod)));
                    if (!owners.isEmpty()) continue;
                }

                Optional<String> staticOwner = staticReceiver(call)
                    .filter(type -> ProjectTypeFilter.isProjectType(type, info.imports(), info.packageName()));
                if (staticOwner.isPresent()) {
                    calls.add(new CalledMethodRef(name, staticOwner.get(), true, fromMethod));
                    continue;
                }
                knownRefs.stream()
                    .filter(r -> r.methodName().equals(name) && r.targetClassHint() != null
                        && (fromMethod == null || fromMethod.equals(r.fromMethodName()))
                        && ProjectTypeFilter.isProjectType(r.targetClassHint(), info.imports(), info.packageName()))
                    .findFirst()
                    .ifPresent(r -> calls.add(new CalledMethodRef(name, r.targetClassHint(), r.resolved(), fromMethod)));
            }
            return new ArrayList<>(calls);
        } catch (Exception e) {
            log.warn("[추가호출] 해석 실패, 건너뜀", e);
            return List.of();
        }
    }

    private static boolean isUnqualified(MethodCallExpr call) {
        return call.getScope().map(s -> s.isThisExpr() || s.isSuperExpr()).orElse(true);
    }

    /**
     * 대상 없는 호출이 물려받은 메서드일 때 그 부모 클래스 이름 후보.
     * 인덱스는 중첩 클래스를 Outer.Inner로 저장하므로, 부모를 이름만 적은 경우(AbstractBuilder)
     * 바깥 클래스가 상속·구현한 타입에 중첩된 것으로도 본다(OAuth2AuthenticationContext.AbstractBuilder).
     * 자기 클래스에 같은 이름 메서드가 있으면 같은 파일 안의 호출이라 비운다.
     */
    private static List<String> inheritedOwners(MethodCallExpr call, String name, StructuralInfo info) {
        Optional<ClassOrInterfaceDeclaration> enclosing = call.findAncestor(ClassOrInterfaceDeclaration.class);
        if (enclosing.isEmpty() || !enclosing.get().getMethodsByName(name).isEmpty()) return List.of();

        List<ClassOrInterfaceDeclaration> outers = new ArrayList<>();
        Optional<ClassOrInterfaceDeclaration> outer = enclosing.get().findAncestor(ClassOrInterfaceDeclaration.class);
        while (outer.isPresent()) {
            outers.add(outer.get());
            outer = outer.get().findAncestor(ClassOrInterfaceDeclaration.class);
        }

        Set<String> owners = new LinkedHashSet<>();
        for (ClassOrInterfaceType parent : enclosing.get().getExtendedTypes()) {
            String written = parent.getNameWithScope();
            owners.add(written);
            for (ClassOrInterfaceDeclaration o : outers) {
                o.getExtendedTypes().forEach(t -> owners.add(t.getNameWithScope() + "." + written));
                o.getImplementedTypes().forEach(t -> owners.add(t.getNameWithScope() + "." + written));
            }
        }
        return owners.stream()
            .filter(type -> ProjectTypeFilter.isProjectType(type, info.imports(), info.packageName()))
            .limit(MAX_INHERITED_OWNERS)
            .toList();
    }

    /** Foo.bar() / a.b.Foo.bar()의 Foo. 클래스 이름 관례(대문자 시작 + 소문자 포함)가 아니면 빈 값. */
    private static Optional<String> staticReceiver(MethodCallExpr call) {
        return call.getScope().flatMap(scope -> {
            String name;
            if (scope.isNameExpr()) name = scope.asNameExpr().getNameAsString();
            else if (scope.isFieldAccessExpr()) name = scope.asFieldAccessExpr().getNameAsString();
            else return Optional.empty();
            boolean typeLike = !name.isEmpty() && Character.isUpperCase(name.charAt(0))
                && name.chars().anyMatch(Character::isLowerCase);
            return typeLike ? Optional.of(name) : Optional.empty();
        });
    }
}
