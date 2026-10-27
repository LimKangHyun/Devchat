package project.ai.indexing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 코드용 BM25 sparse 벡터 인코더.
 *
 * dense 임베딩은 "의미가 비슷한 코드"를 찾지만, diff에 그대로 적힌 식별자
 * (OAuth2EndpointUtils.getFormParameters 같은)를 정확히 맞추는 데는 약하다.
 * 그 빈틈을 키워드 매칭으로 메우려고 dense와 함께 쓴다.
 *
 * 여기서는 BM25의 TF 부분만 계산한다. IDF는 코퍼스 전체 통계가 필요하므로
 * Qdrant 컬렉션의 sparse 벡터 설정(modifier: idf)이 검색 시점에 서버에서 곱한다.
 * 그래서 문서를 추가·삭제해도 다시 계산할 필요가 없다.
 */
@Component
public class SparseEncoder {

    /** BM25 기본값. */
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /**
     * 평균 문서 길이(토큰 수). 정확한 값은 코퍼스마다 다르지만, 증분 인덱싱 중에 매번 다시 구할 수 없어
     * 메서드 청크의 대략적인 크기로 고정한다. 길이 정규화의 기준점일 뿐이라 대략값이어도 순위 영향이 작다.
     */
    private static final double AVG_DOC_LENGTH = 120;

    private static final Pattern NON_IDENTIFIER = Pattern.compile("[^A-Za-z0-9]+");

    /** camelCase·PascalCase·숫자 경계. getHTTPClient2 -> get, HTTP, Client, 2 */
    private static final Pattern CAMEL_BOUNDARY =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])(?=[0-9])");

    /** 거의 모든 청크에 나와 변별력이 없는 자바 키워드. IDF가 낮춰 주긴 하지만 벡터 크기를 줄이려고 뺀다. */
    private static final Set<String> STOP_WORDS = Set.of(
            "public", "private", "protected", "static", "final", "return", "new", "this", "void",
            "class", "interface", "import", "package", "if", "else", "for", "while", "try", "catch",
            "throw", "throws", "null", "true", "false", "int", "long", "boolean", "extends",
            "implements", "override", "the", "and", "of", "to", "a", "an", "is", "in", "var"
    );

    public record SparseVector(List<Integer> indices, List<Float> values) {
        public boolean isEmpty() {
            return indices.isEmpty();
        }
    }

    /** 저장할 청크용. 토큰 빈도에 BM25 TF 포화·길이 정규화를 적용한다. */
    public SparseVector encodeDocument(String text) {
        List<String> tokens = tokenize(text);
        Map<Integer, Integer> tf = countByIndex(tokens);
        double lengthNorm = 1 - B + B * tokens.size() / AVG_DOC_LENGTH;

        List<Integer> indices = new ArrayList<>(tf.size());
        List<Float> values = new ArrayList<>(tf.size());
        tf.forEach((index, count) -> {
            indices.add(index);
            values.add((float) (count * (K1 + 1) / (count + K1 * lengthNorm)));
        });
        return new SparseVector(indices, values);
    }

    /** 검색 쿼리용. 쿼리 쪽은 등장 여부만 보므로 토큰마다 1이다. */
    public SparseVector encodeQuery(String text) {
        Map<Integer, Integer> tf = countByIndex(tokenize(text));
        List<Integer> indices = new ArrayList<>(tf.keySet());
        List<Float> values = new ArrayList<>(indices.size());
        indices.forEach(i -> values.add(1f));
        return new SparseVector(indices, values);
    }

    /**
     * 식별자를 통째로 한 토큰, camelCase로 나눈 조각을 각각 한 토큰으로 만든다.
     * 통째 토큰은 정확한 식별자 일치에, 조각은 getFormParameters와 formParameters 같은 부분 일치에 걸린다.
     */
    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) return tokens;

        for (String word : NON_IDENTIFIER.split(text)) {
            if (word.isEmpty()) continue;
            addToken(tokens, word);
            String[] parts = CAMEL_BOUNDARY.split(word);
            if (parts.length > 1) {
                for (String part : parts) addToken(tokens, part);
            }
        }
        return tokens;
    }

    private void addToken(List<String> tokens, String raw) {
        String token = raw.toLowerCase();
        if (token.length() < 2 || STOP_WORDS.contains(token)) return;
        if (Character.isDigit(token.charAt(0))) return;
        tokens.add(token);
    }

    /** 토큰을 sparse 인덱스(0 이상 int)로 바꿔 센다. 해시 충돌은 어휘 규모에서 무시할 수준이다. */
    private Map<Integer, Integer> countByIndex(List<String> tokens) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token.hashCode() & 0x7fffffff, 1, Integer::sum);
        }
        return counts;
    }
}
