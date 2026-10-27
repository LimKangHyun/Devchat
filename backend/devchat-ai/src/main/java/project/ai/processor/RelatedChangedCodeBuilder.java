package project.ai.processor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;
import project.ai.indexing.chunking.TypeNameResolver;
import project.ai.indexing.structural.StructuralInfoExtractor;
import project.common.message.aireview.ChangedFileSnapshot;

/**
 * 리뷰 대상 파일이 호출하는 메서드 중, 같은 PR에서 함께 바뀐 다른 파일의 메서드를 골라 변경 후 코드를 만든다.
 *
 * 리뷰는 파일마다 따로 돌고 인덱스에는 변경 전 코드만 있어서, 파일 A가 같은 PR에서 바뀐 B.foo()를 호출하면
 * A의 리뷰는 foo의 새 코드를 diff로도 RAG로도 받지 못한다. 그 빈틈을 채운다.
 *
 * 고르는 기준
 *  - B에서 diff가 건드린 메서드/생성자일 것 (변경 후 라인 기준)
 *  - A가 그 메서드를 호출할 것: 호출 이름이 같고, 리시버 타입 힌트가 B의 클래스(또는 B가 구현/상속한 타입)이거나 힌트가 없을 것
 *  - 생성자는 A에 new B(...)가 있을 것
 * 타입 힌트까지 확정된 호출을 먼저 넣고, 개수와 길이에 상한을 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelatedChangedCodeBuilder {

    static final int MAX_METHODS = 5;
    static final int MAX_CHARS = 6000;
    static final int MAX_METHOD_LINES = 60;

    private final JavaParser javaParser;
    private final DiffLineParser diffLineParser;
    private final StructuralInfoExtractor structuralInfoExtractor;

    private record Picked(String filePath, String ownerClass, String name, String code, int priority) {}

    public String build(String filePath, String fileContent, List<ChangedFileSnapshot> relatedChangedFiles) {
        if (relatedChangedFiles == null || relatedChangedFiles.isEmpty()
            || filePath == null || !filePath.endsWith(".java") || fileContent == null || fileContent.isBlank()) {
            return "";
        }
        try {
            List<CalledMethodRef> refs = structuralInfoExtractor.extract(fileContent, filePath).calledMethodRefs();
            List<Picked> picked = new ArrayList<>();
            for (ChangedFileSnapshot other : relatedChangedFiles) {
                picked.addAll(pickFrom(other, refs, fileContent));
            }
            if (picked.isEmpty()) return "";

            picked.sort(Comparator.comparingInt(Picked::priority));
            log.info("[같은PR변경] reviewFile={}, 선택 {}개: {}", filePath, picked.size(),
                picked.stream().map(p -> p.ownerClass() + "#" + p.name()).toList());
            return format(picked);
        } catch (Exception e) {
            // 보조 컨텍스트라서 실패해도 리뷰는 그대로 진행한다.
            log.warn("[같은PR변경] 컨텍스트 생성 실패, 없이 진행. reviewFile={}", filePath, e);
            return "";
        }
    }

    private List<Picked> pickFrom(ChangedFileSnapshot other, List<CalledMethodRef> refs, String reviewContent) {
        if (other.filePath() == null || !other.filePath().endsWith(".java")
            || other.fileContent() == null || other.fileDiff() == null) {
            return List.of();
        }
        Set<Integer> changedLines = diffLineParser.parseValidLines(other.fileDiff());
        if (changedLines.isEmpty()) return List.of();

        ParseResult<CompilationUnit> parsed = javaParser.parse(other.fileContent());
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) return List.of();

        String[] lines = other.fileContent().split("\n", -1);
        List<Picked> picked = new ArrayList<>();
        for (CallableDeclaration<?> decl : parsed.getResult().get().findAll(CallableDeclaration.class)) {
            // 람다·익명 클래스 안의 선언은 건너뛴다.
            if (decl.findAncestor(CallableDeclaration.class).isPresent()) continue;
            Range range = decl.getRange().orElse(null);
            if (range == null || !overlaps(range, changedLines)) continue;

            String owner = TypeNameResolver.enclosingTypeName(decl);
            String name = decl.getNameAsString();
            int priority;
            if (decl instanceof ConstructorDeclaration) {
                if (!Pattern.compile("\\bnew\\s+" + Pattern.quote(name) + "\\s*[(<]").matcher(reviewContent).find()) continue;
                priority = 0;
            } else if (decl instanceof MethodDeclaration) {
                Set<String> ownerTypes = ownerTypeNames(decl, owner);
                List<CalledMethodRef> calls = refs.stream()
                    .filter(r -> r.methodName().equals(name) && hintMatches(r.targetClassHint(), ownerTypes))
                    .toList();
                if (calls.isEmpty()) continue;
                boolean typed = calls.stream().anyMatch(r -> r.targetClassHint() != null && !r.targetClassHint().isBlank());
                priority = typed ? 0 : 1;
            } else {
                continue;
            }
            picked.add(new Picked(other.filePath(), owner, name,
                sliceLines(lines, range.begin.line, range.end.line), priority));
        }
        return picked;
    }

    /** 메서드가 속한 타입의 이름과, 그 타입이 상속/구현한 타입 이름. 인터페이스 타입으로 호출해도 잡기 위해서다. */
    private Set<String> ownerTypeNames(Node decl, String owner) {
        Set<String> names = new HashSet<>();
        if (!owner.isBlank()) {
            names.add(owner);
            names.add(owner.substring(owner.lastIndexOf('.') + 1));
        }
        decl.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(type -> {
            type.getExtendedTypes().forEach(t -> names.add(simpleName(t)));
            type.getImplementedTypes().forEach(t -> names.add(simpleName(t)));
        });
        return names;
    }

    private static String simpleName(ClassOrInterfaceType type) {
        return type.getNameAsString();
    }

    /** 힌트가 없으면 이름만으로 맞춘다. 있으면 바깥.내부 형태도 고려해 마지막 이름까지 비교한다. */
    private static boolean hintMatches(String hint, Set<String> ownerTypes) {
        if (hint == null || hint.isBlank()) return true;
        return ownerTypes.contains(hint) || ownerTypes.contains(hint.substring(hint.lastIndexOf('.') + 1));
    }

    private static boolean overlaps(Range range, Set<Integer> changedLines) {
        for (int line = range.begin.line; line <= range.end.line; line++) {
            if (changedLines.contains(line)) return true;
        }
        return false;
    }

    private static String sliceLines(String[] lines, int begin, int end) {
        int last = Math.min(end, begin + MAX_METHOD_LINES - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = begin; i <= last && i <= lines.length; i++) {
            sb.append(lines[i - 1]).append('\n');
        }
        if (last < end) sb.append("    // ...(생략)\n");
        return sb.toString();
    }

    private static String format(List<Picked> picked) {
        StringBuilder sb = new StringBuilder("[같은 PR에서 함께 변경된 코드 (변경 후)]\n");
        int count = 0;
        for (Picked p : picked) {
            if (count >= MAX_METHODS) break;
            String block = "// " + p.filePath() + " (class: " + p.ownerClass() + ", method: " + p.name() + ")\n"
                + p.code() + "\n";
            if (count > 0 && sb.length() + block.length() > MAX_CHARS) break;
            sb.append(block);
            count++;
        }
        return sb.toString().trim();
    }
}
