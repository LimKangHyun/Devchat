package project.ai.indexing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparseEncoderTest {

    private final SparseEncoder encoder = new SparseEncoder();

    @Test
    void 식별자는_통째_토큰과_camelCase_조각으로_나눈다() {
        assertThat(encoder.tokenize("OAuth2EndpointUtils.getFormParameters(request)"))
            .contains("oauth2endpointutils", "endpoint", "utils",
                "getformparameters", "get", "form", "parameters", "request");
    }

    @Test
    void 자바_키워드와_한_글자_토큰은_뺀다() {
        assertThat(encoder.tokenize("public static final int x = new Foo();"))
            .containsExactly("foo");
    }

    @Test
    void 쿼리는_토큰마다_1이고_중복은_하나로_센다() {
        SparseEncoder.SparseVector v = encoder.encodeQuery("getContext getContext");
        assertThat(v.indices()).hasSize(3); // getcontext, get, context
        assertThat(v.values()).containsOnly(1f);
    }

    @Test
    void 문서는_자주_나온_토큰일수록_가중치가_크되_포화된다() {
        SparseEncoder.SparseVector once = encoder.encodeDocument("refresh");
        SparseEncoder.SparseVector many = encoder.encodeDocument("refresh refresh refresh refresh refresh");
        assertThat(many.values().get(0)).isGreaterThan(once.values().get(0));
        assertThat(many.values().get(0)).isLessThan(once.values().get(0) * 5);
    }
}
