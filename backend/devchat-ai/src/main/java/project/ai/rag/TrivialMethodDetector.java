package project.ai.rag;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 메서드 코드가 리뷰에 볼 내용이 없는 단순 메서드인지 판별한다.
 *
 * 새 코드가 부르는 메서드를 확정 슬롯에 넣을 때, registeredClient.getId() 같은 getter나 인터페이스 선언까지
 * 들어가 노이즈가 많았다. 이름(get~)으로는 거를 수 없다. OAuth2EndpointUtils.getFormParameters()처럼
 * 이름만 get이고 실제 로직이 있는 유틸이 정답인 경우가 많았기 때문이다. 그래서 몸통 내용으로 본다.
 *
 * 단순 메서드
 *  - 몸통 없음 (인터페이스·추상 메서드 선언)
 *  - return 필드;              예: return this.id;
 *  - return 메서드(상수만);     예: return get(RegisteredClient.class);
 * 조건 계산이 있는 return !isExpired() && ... 같은 것은 단순하지 않다.
 */
@Component
@RequiredArgsConstructor
public class TrivialMethodDetector {

    private final JavaParser javaParser;

    /** 파싱에 실패하면 단순하지 않다고 본다 (확정 슬롯 자격을 빼앗지 않는 쪽). */
    public boolean isTrivial(String methodCode) {
        if (methodCode == null || methodCode.isBlank()) return false;
        try {
            ParseResult<BodyDeclaration<?>> parsed = javaParser.parseBodyDeclaration(methodCode.trim());
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) return false;
            if (!(parsed.getResult().get() instanceof MethodDeclaration method)) return false;

            if (method.getBody().isEmpty()) return true;
            BlockStmt body = method.getBody().get();
            if (body.getStatements().size() != 1) return false;

            // setter(this.필드 = 값;)는 단순으로 보지 않는다. 값을 바꾸는 동작이라, 특히 설정 클래스에서는
            // 새 코드가 그걸 맞게 쓰는지 볼 이유가 있다. 평가에서 setter를 밀어냈다가 정답을 놓쳤다.
            Statement only = body.getStatement(0);
            return only.isReturnStmt()
                && only.asReturnStmt().getExpression().map(TrivialMethodDetector::isSimpleValue).orElse(true);
        } catch (Exception e) {
            return false;
        }
    }

    /** 필드 하나, 또는 상수만 넘기는 단순 호출. */
    private static boolean isSimpleValue(Expression e) {
        if (isConstant(e)) return true;
        if (e.isMethodCallExpr()) {
            MethodCallExpr call = e.asMethodCallExpr();
            boolean simpleScope = call.getScope().map(s -> s.isThisExpr() || s.isNameExpr()).orElse(true);
            return simpleScope && call.getArguments().stream().allMatch(TrivialMethodDetector::isConstant);
        }
        return false;
    }

    /** 이름, this.필드, 상수 경로(A.B.C), 리터럴, Foo.class */
    private static boolean isConstant(Expression e) {
        return e.isNameExpr() || e.isLiteralExpr() || e.isClassExpr() || e.isThisExpr()
            || (e.isFieldAccessExpr() && isConstant(e.asFieldAccessExpr().getScope()));
    }
}
