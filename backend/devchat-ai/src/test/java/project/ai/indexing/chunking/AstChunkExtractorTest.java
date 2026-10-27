package project.ai.indexing.chunking;

import org.junit.jupiter.api.Test;
import project.ai.config.JavaParserConfig;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AstChunkExtractorTest {

    private final AstChunkExtractor extractor = new AstChunkExtractor(new JavaParserConfig().javaParser());

    private List<CalledMethodRef> refsOf(String body) {
        String source = """
                package demo;
                class Caller {
                    void run(Object bean, java.util.List<Order> orders) {
                %s
                    }
                }
                """.formatted(body);
        return extractor.extract(source, "demo/Caller.java").get(0).calledMethodRefs();
    }

    private CalledMethodRef ref(List<CalledMethodRef> refs, String methodName) {
        return refs.stream().filter(r -> r.methodName().equals(methodName)).findFirst().orElseThrow();
    }

    @Test
    void var_new_생성식이면_타입을_확정한다() {
        var refs = refsOf("""
                var svc = new OrderService();
                svc.cancel();
                """);
        assertThat(ref(refs, "cancel")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OrderService", true);
    }

    @Test
    void var_캐스트면_타입을_확정한다() {
        var refs = refsOf("""
                var s = (OrderService) bean;
                s.cancel();
                """);
        assertThat(ref(refs, "cancel")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OrderService", true);
    }

    @Test
    void 타입을_명시한_람다_파라미터는_확정한다() {
        var refs = refsOf("""
                orders.forEach((Order o) -> o.cancel());
                """);
        assertThat(ref(refs, "cancel")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("Order", true);
    }

    @Test
    void 타입을_생략한_람다_파라미터는_이름으로_추정한다() {
        var refs = refsOf("""
                orders.forEach(order -> order.cancel());
                """);
        assertThat(ref(refs, "cancel")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("Order", false);
    }

    @Test
    void catch_파라미터는_확정한다() {
        var refs = refsOf("""
                try {
                    bean.toString();
                } catch (IllegalStateException e) {
                    e.getMessage();
                }
                """);
        assertThat(ref(refs, "getMessage")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("IllegalStateException", true);
    }

    @Test
    void 패턴_변수는_확정한다() {
        var refs = refsOf("""
                if (bean instanceof Order ord) {
                    ord.ship();
                }
                """);
        assertThat(ref(refs, "ship")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("Order", true);
    }

    @Test
    void 제네릭_타입은_제네릭_인자가_아니라_바깥_타입을_본다() {
        var refs = refsOf("""
                Repository<Order> repo = null;
                repo.save();
                """);
        assertThat(ref(refs, "save")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("Repository", true);
    }

    @Test
    void 범용_타입이면_힌트를_비운다() {
        var refs = refsOf("""
                orders.size();
                java.util.List<Order> fqn = orders;
                fqn.add(null);
                String name = "a";
                name.trim();
                """);
        assertThat(refs).filteredOn(r -> List.of("size", "add", "trim").contains(r.methodName()))
                .hasSize(3)
                .allSatisfy(r -> {
                    assertThat(r.targetClassHint()).isNull();
                    assertThat(r.resolved()).isFalse();
                });
    }

    @Test
    void 범용_타입_필드도_힌트를_비우고_제네릭_인자를_쓰지_않는다() {
        String source = """
                package demo;
                import java.util.Map;
                class Caller {
                    private Map<String, Order> cache;
                    private OrderRepository orderRepository;
                    void run() {
                        cache.get("k");
                        orderRepository.findAll();
                    }
                }
                """;
        var refs = extractor.extract(source, "demo/Caller.java").get(0).calledMethodRefs();

        assertThat(ref(refs, "get").targetClassHint()).isNull();
        assertThat(ref(refs, "findAll")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OrderRepository", true);
    }

    @Test
    void 내부_클래스는_바깥_클래스_이름을_붙여_저장한다() {
        String source = """
                package demo;
                public final class TokenStatus {
                    void touch() {
                        Builder b = new Builder();
                        b.build();
                    }
                    public static final class Builder {
                        public TokenStatus build() {
                            return new TokenStatus();
                        }
                    }
                }
                """;
        List<ChunkMeta> chunks = extractor.extract(source, "demo/TokenStatus.java");

        ChunkMeta touch = chunks.stream().filter(c -> c.methodName().equals("touch")).findFirst().orElseThrow();
        ChunkMeta build = chunks.stream().filter(c -> c.methodName().equals("build")).findFirst().orElseThrow();
        assertThat(build.className()).isEqualTo("TokenStatus.Builder");
        assertThat(touch.className()).isEqualTo("TokenStatus");
        assertThat(touch.calledMethodQualified()).contains("TokenStatus.Builder.build");
    }

    @Test
    void 다른_클래스의_내부_클래스는_명시한_이름이나_import로_정규화한다() {
        String source = """
                package demo;
                import org.example.client.RegisteredClient.Builder;
                class Caller {
                    void run(org.example.auth.OAuth2Authorization.Token<String> token, Builder builder) {
                        token.isActive();
                        builder.build();
                        OAuth2Authorization.Token.of(null);
                    }
                }
                """;
        var refs = extractor.extract(source, "demo/Caller.java").get(0).calledMethodRefs();

        assertThat(ref(refs, "isActive")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OAuth2Authorization.Token", true);
        assertThat(ref(refs, "build")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("RegisteredClient.Builder", true);
        // 타입을 직접 쓴 정적 호출은 이름 관례 대신 정규화된 타입 이름을 쓰되, 확정 여부는 추정으로 둔다.
        assertThat(ref(refs, "of")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OAuth2Authorization.Token", false);
    }

    @Test
    void 참조_타입에서_패키지_조각과_바깥_타입_조각을_따로_세지_않는다() {
        var chunk = extractor.extract("""
                package demo;
                class Caller {
                    void run() {
                        org.example.auth.OAuth2Authorization.Token<Order> t = null;
                        t.toString();
                    }
                }
                """, "demo/Caller.java").get(0);
        assertThat(chunk.referencedTypeNames())
                .contains("OAuth2Authorization.Token", "Order")
                .doesNotContain("OAuth2Authorization", "auth", "example", "Token");
    }

    @Test
    void 빌더_체인은_관례로_리시버_타입을_추정한다() {
        var refs = refsOf("""
                ClientSettings s = ClientSettings.withSettings(null).requireProofKey(true).build();
                JwtClaimsSet c = JwtClaimsSet.builder().issuer("x").build();
                """);
        assertThat(refs).filteredOn(r -> r.methodName().equals("requireProofKey"))
                .extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ClientSettings.Builder", false));
        assertThat(refs).filteredOn(r -> r.methodName().equals("issuer"))
                .extracting(CalledMethodRef::targetClassHint)
                .containsExactly("JwtClaimsSet.Builder");
        assertThat(refs).filteredOn(r -> r.methodName().equals("build"))
                .extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ClientSettings.Builder", false),
                        org.assertj.core.groups.Tuple.tuple("JwtClaimsSet.Builder", false));
    }

    @Test
    void 빌더가_아닌_체인은_추정하지_않는다() {
        var refs = refsOf("""
                orders.stream().map(o -> o).count();
                """);
        assertThat(ref(refs, "map").targetClassHint()).isNull();
        assertThat(ref(refs, "count").targetClassHint()).isNull();
    }

    @Test
    void 빌더_build_다음_호출은_바깥_타입으로_추정한다() {
        assertThat(AstChunkExtractor.returnTypeByBuilderConvention("ClientSettings.Builder", "build", false))
                .isEqualTo("ClientSettings");
        assertThat(AstChunkExtractor.returnTypeByBuilderConvention("JwtClaimsSetBuilder", "build", false))
                .isEqualTo("JwtClaimsSet");
        assertThat(AstChunkExtractor.returnTypeByBuilderConvention("OrderService", "find", false)).isNull();
    }

    @Test
    void 생성자_체인은_생성한_타입으로_확정한다() {
        var refs = refsOf("""
                new OrderService().cancel();
                """);
        assertThat(ref(refs, "cancel")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OrderService", true);
    }

    @Test
    void var_메서드_반환값이면_이름으로_추정한다() {
        var refs = refsOf("""
                var orderService = lookup();
                orderService.cancel();
                """);
        assertThat(ref(refs, "cancel")).extracting(CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
                .containsExactly("OrderService", false);
    }
}
