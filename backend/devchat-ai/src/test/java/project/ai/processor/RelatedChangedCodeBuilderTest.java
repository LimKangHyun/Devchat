package project.ai.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import project.ai.config.JavaParserConfig;
import project.ai.indexing.chunking.AstChunkExtractor;
import project.ai.indexing.structural.StructuralInfoExtractor;
import project.common.message.aireview.ChangedFileSnapshot;

class RelatedChangedCodeBuilderTest {

    private final JavaParser parser = new JavaParserConfig().javaParser();
    private final RelatedChangedCodeBuilder builder = new RelatedChangedCodeBuilder(
        parser, new DiffLineParser(), new StructuralInfoExtractor(new AstChunkExtractor(parser)));

    // 변경 후 OrderService: cancel(4~6줄)은 이번 PR에서 바뀌었고, find(7~9줄)는 그대로다.
    private static final String ORDER_SERVICE = """
        package demo;
        public class OrderService implements Cancellable {
            public OrderService(Repo repo) {}
            public void cancel(Long id, String reason) {
                repo.delete(id);
            }
            public Order find(Long id) {
                return repo.find(id);
            }
        }
        """;

    // cancel 시그니처와 본문(4~5줄)만 바뀐 diff
    private static final String ORDER_SERVICE_DIFF = """
        @@ -1,9 +1,9 @@
         package demo;
         public class OrderService implements Cancellable {
             public OrderService(Repo repo) {}
        -    public void cancel(Long id) {
        +    public void cancel(Long id, String reason) {
        +        repo.delete(id);
             }
             public Order find(Long id) {
                 return repo.find(id);
        """;

    private ChangedFileSnapshot orderService() {
        return new ChangedFileSnapshot("demo/OrderService.java", ORDER_SERVICE_DIFF, ORDER_SERVICE);
    }

    private String review(String body) {
        String source = """
            package demo;
            class OrderController {
                private OrderService orderService;
                void handle(Long id) {
            %s
                }
            }
            """.formatted(body);
        return builder.build("demo/OrderController.java", source, List.of(orderService()));
    }

    @Test
    void 호출하는_메서드가_같은_PR에서_바뀌었으면_변경_후_코드를_넣는다() {
        String context = review("orderService.cancel(id, \"user\");");

        assertThat(context)
            .startsWith("[같은 PR에서 함께 변경된 코드 (변경 후)]")
            .contains("demo/OrderService.java (class: OrderService, method: cancel)")
            .contains("public void cancel(Long id, String reason)");
    }

    @Test
    void 호출하지만_바뀌지_않은_메서드는_넣지_않는다() {
        assertThat(review("orderService.find(id);")).isEmpty();
    }

    @Test
    void 이름이_같아도_리시버_타입이_다르면_넣지_않는다() {
        assertThat(review("PaymentService payment = null; payment.cancel(id, \"x\");")).isEmpty();
    }

    @Test
    void 구현한_인터페이스_타입으로_호출해도_넣는다() {
        assertThat(review("Cancellable c = orderService; c.cancel(id, \"x\");"))
            .contains("method: cancel");
    }

    @Test
    void 관련_파일이_없으면_빈_문자열() {
        assertThat(builder.build("demo/A.java", "class A {}", List.of())).isEmpty();
        assertThat(builder.build("demo/A.java", "class A {}", null)).isEmpty();
    }
}
