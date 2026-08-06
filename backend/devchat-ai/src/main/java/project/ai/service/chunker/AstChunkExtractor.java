package project.ai.service.chunker;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AstChunkExtractor {

    private static final int MIN_METHOD_LINES = 3;

    static {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(configuration);
    }

    public List<ChunkMeta> extract(String content, String relativePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            List<ChunkMeta> result = new ArrayList<>();

            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (method.findAncestor(MethodDeclaration.class).isPresent()) {
                    continue;
                }

                String code = method.toString();
                if (code.lines().count() < MIN_METHOD_LINES) continue;

                result.add(buildChunk(method, packageName));
            }
            return result;
        } catch (Exception e) {
            log.warn("AST 파싱 실패. file={}, error={}", relativePath, e.getMessage());
            return List.of();
        }
    }

    private ChunkMeta buildChunk(MethodDeclaration method, String packageName) {
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
        String returnType = method.getType().asString();

        List<String> annotations = method.getAnnotations().stream()
            .map(AnnotationExpr::getNameAsString)
            .collect(Collectors.toList());

        List<String> calledMethodNames = extractCalledMethodNames(method);
        List<String> referencedTypeNames = extractReferencedTypes(parameterTypes, returnType);

        return new ChunkMeta(
            null, null, -1, method.toString(),
            className, methodName, methodSignature,
            packageName, superClassName, interfaceNames,
            calledMethodNames, referencedTypeNames, annotations
        );
    }

    private List<String> extractCalledMethodNames(MethodDeclaration method) {
        List<String> called = new ArrayList<>();
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr call, Void arg) {
                super.visit(call, arg);
                called.add(call.getNameAsString());
            }
        }, null);
        return called.stream().distinct().collect(Collectors.toList());
    }

    private List<String> extractReferencedTypes(List<String> parameterTypes, String returnType) {
        List<String> types = new ArrayList<>(parameterTypes);
        if (returnType != null && !returnType.equals("void")) {
            types.add(returnType);
        }
        return types.stream().distinct().collect(Collectors.toList());
    }
}