package project.ai.indexing.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import project.ai.config.JavaParserConfig;
import project.ai.indexing.chunking.AstChunkExtractor;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;

class AddedCallResolverTest {

    private final JavaParser parser = new JavaParserConfig().javaParser();
    private final AddedCallResolver resolver = new AddedCallResolver(parser);
    private final StructuralInfoExtractor extractor = new StructuralInfoExtractor(new AstChunkExtractor(parser));

    private static final String SOURCE = """
        package demo;
        class Provider {
            private OrderService orderService;
            void authenticate(Object auth) {
                Object client = AuthUtils.getClient(auth);
                orderService.cancel(1L);
                orderService.find(1L);
                StringBuilder sb = new StringBuilder();
                sb.append("x");
            }
        }
        """;

    private List<CalledMethodRef> resolve(Set<Integer> addedLines) {
        StructuralInfo info = extractor.extract(SOURCE, "demo/Provider.java");
        return resolver.resolve(SOURCE, addedLines, info.calledMethodRefs(), info);
    }

    @Test
    void 클래스_이름으로_부른_정적_호출은_대상을_확정한다() {
        assertThat(resolve(Set.of(5)))
            .extracting(CalledMethodRef::methodName, CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("getClient", "AuthUtils", true));
    }

    @Test
    void 필드로_부른_호출은_선언_타입을_힌트로_쓴다() {
        assertThat(resolve(Set.of(6)))
            .extracting(CalledMethodRef::methodName, CalledMethodRef::targetClassHint)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("cancel", "OrderService"));
    }

    @Test
    void 추가되지_않은_줄의_호출은_뽑지_않는다() {
        assertThat(resolve(Set.of(6))).extracting(CalledMethodRef::methodName).doesNotContain("find", "getClient");
    }

    @Test
    void 프로젝트_밖_타입의_호출은_뽑지_않는다() {
        assertThat(resolve(Set.of(8, 9))).isEmpty();
    }

    private static final String INHERITED_SOURCE = """
        package demo;
        public final class CustomContext implements BaseContext {
            public static final class Builder extends AbstractBuilder<CustomContext, Builder> {
                public CustomContext build() {
                    Object client = get(Object.class);
                    Object ctx = getContext();
                    helper();
                    return new CustomContext();
                }
                private void helper() {}
            }
        }
        """;

    private List<CalledMethodRef> resolveInherited(Set<Integer> addedLines) {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "inheritedCallEnabled", true);
        StructuralInfo info = extractor.extract(INHERITED_SOURCE, "demo/CustomContext.java");
        return resolver.resolve(INHERITED_SOURCE, addedLines, info.calledMethodRefs(), info);
    }

    @Test
    void 대상_없는_호출은_부모_클래스와_바깥_클래스가_구현한_타입의_중첩_클래스를_후보로_삼는다() {
        assertThat(resolveInherited(Set.of(5, 6)))
            .extracting(CalledMethodRef::methodName, CalledMethodRef::targetClassHint, CalledMethodRef::resolved)
            .contains(
                org.assertj.core.groups.Tuple.tuple("get", "AbstractBuilder", false),
                org.assertj.core.groups.Tuple.tuple("get", "BaseContext.AbstractBuilder", false),
                org.assertj.core.groups.Tuple.tuple("getContext", "BaseContext.AbstractBuilder", false));
    }

    @Test
    void 자기_클래스에_있는_메서드는_부모_후보로_보지_않는다() {
        assertThat(resolveInherited(Set.of(7))).extracting(CalledMethodRef::methodName).doesNotContain("helper");
    }
}
