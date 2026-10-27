package project.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * Qdrant 검색 결과 하나.
 *
 * @param payload 저장 시 넣은 메타데이터. 값은 JSON에서 읽은 String, List, Number, Boolean 중 하나다.
 */
public record SearchHit(String id, float score, Map<String, Object> payload) {

    /** 문자열 필드. 없으면 빈 문자열. */
    public String getString(String key) {
        Object value = payload.get(key);
        if (value == null) return "";
        return value instanceof String s ? s : String.valueOf(value);
    }

    /** 문자열 배열 필드. 없거나 배열이 아니면 빈 리스트. 빈 문자열 원소는 제외한다. */
    public List<String> getStringList(String key) {
        if (!(payload.get(key) instanceof List<?> list)) return List.of();
        return list.stream()
                .map(String::valueOf)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
