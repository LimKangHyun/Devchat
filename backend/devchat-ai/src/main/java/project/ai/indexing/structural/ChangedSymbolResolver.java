package project.ai.indexing.structural;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * diff가 바꾼 라인들을, 그 라인이 속한 코드 심볼(메서드/필드/클래스 헤더)로 변환한다.
 *
 * 이것이 영향 분석의 출발점이다. 파일 전체가 아니라 "실제로 바뀐 지점"에서
 * 검색을 시작해야, 무관한 관계가 슬롯을 차지하는 recall 손실을 막을 수 있다.
 *
 * 심볼 해석(SymbolSolver)을 쓰지 않고 라인 범위 교집합만으로 판별하므로,
 * 클래스패스 없이 동작하고 인덱스도 건드리지 않는다 (검색 시점에 fileContent를 파싱).
 */
@Slf4j
@Component
public class ChangedSymbolResolver {

    private final JavaParser javaParser;

    public ChangedSymbolResolver(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    /**
     * @param fileContent  현재(변경 후) 파일 전체
     * @param changedLines diff가 바꾼 라인 번호 집합 (1-base, 변경 후 기준)
     */
    public List<ChangedSymbol> resolve(String fileContent, Set<Integer> changedLines) {
        if (fileContent == null || fileContent.isBlank() || changedLines.isEmpty()) {
            return List.of();
        }

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(fileContent);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                return List.of();
            }
            CompilationUnit cu = parseResult.getResult().get();

            Set<ChangedSymbol> symbols = new LinkedHashSet<>();

            // 1) 클래스 헤더 변경 (extends/implements 라인이 바뀌었나)
            for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (headerRange(clazz).map(r -> overlaps(r, changedLines)).orElse(false)) {
                    symbols.add(new ChangedSymbol(ChangedSymbol.Kind.CLASS_HEADER,
                            clazz.getNameAsString()));
                }
            }

            // 2) 메서드 변경 — 시그니처인지 본문인지 구분
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                // 중첩 메서드(람다 등)는 스킵. 최상위 메서드만.
                if (method.findAncestor(MethodDeclaration.class).isPresent()) continue;

                Range full = method.getRange().orElse(null);
                if (full == null || !overlaps(full, changedLines)) continue;

                if (overlaps(signatureRange(method), changedLines)) {
                    symbols.add(new ChangedSymbol(ChangedSymbol.Kind.METHOD_SIGNATURE,
                            method.getNameAsString()));
                } else {
                    symbols.add(new ChangedSymbol(ChangedSymbol.Kind.METHOD_BODY,
                            method.getNameAsString()));
                }
            }

            // 3) 필드 변경
            for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                Range range = field.getRange().orElse(null);
                if (range == null || !overlaps(range, changedLines)) continue;

                field.getVariables().forEach(v ->
                        symbols.add(new ChangedSymbol(ChangedSymbol.Kind.FIELD, v.getNameAsString())));
            }

            log.info("[변경심볼] {}개: {}", symbols.size(), symbols);
            return new ArrayList<>(symbols);

        } catch (Exception e) {
            log.warn("[변경심볼] 해석 실패, 파일 전체 기준으로 폴백", e);
            return List.of();
        }
    }

    /**
     * 메서드 시그니처 영역의 라인 범위.
     * 메서드 시작 라인부터 본문 여는 '{' 라인까지를 시그니처로 본다.
     * abstract/interface 메서드는 본문이 없으므로 선언 전체가 시그니처.
     */
    private Range signatureRange(MethodDeclaration method) {
        int begin = method.getRange().get().begin.line;
        int sigEnd = method.getBody()
                .flatMap(body -> body.getRange())
                .map(r -> r.begin.line)   // 본문 '{'가 있는 라인
                .orElse(method.getRange().get().end.line);
        return rangeOfLines(begin, sigEnd);
    }

    /**
     * 클래스 헤더(선언부) 라인 범위.
     * 클래스 시작 라인부터 본문 여는 '{' 라인까지 — extends/implements가 여기 있다.
     */
    private java.util.Optional<Range> headerRange(ClassOrInterfaceDeclaration clazz) {
        return clazz.getRange().map(full -> {
            int begin = full.begin.line;
            // 멤버가 있으면 첫 멤버 직전까지, 없으면 클래스 시작 라인만
            int headerEnd = clazz.getMembers().isEmpty()
                    ? begin
                    : clazz.getMembers().get(0).getRange()
                    .map(r -> r.begin.line - 1).orElse(begin);
            return rangeOfLines(begin, Math.max(begin, headerEnd));
        });
    }

    private boolean overlaps(Range range, Set<Integer> changedLines) {
        for (int line = range.begin.line; line <= range.end.line; line++) {
            if (changedLines.contains(line)) return true;
        }
        return false;
    }

    /** 라인 번호 두 개로 Range를 만든다 (컬럼은 판별에 안 쓰므로 1로 고정). */
    private Range rangeOfLines(int beginLine, int endLine) {
        return new Range(
                new com.github.javaparser.Position(beginLine, 1),
                new com.github.javaparser.Position(endLine, 1));
    }
}