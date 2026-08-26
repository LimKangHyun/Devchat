package project.ai.rag;

import com.google.protobuf.Value;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;

import java.util.List;

/** Pinecone 조회 결과(ScoredVectorWithUnsignedIndices)의 metadata 필드를 꺼내는 유틸. */
public final class PineconeResultUtils {

    private PineconeResultUtils() {}

    public static String getStringField(ScoredVectorWithUnsignedIndices result, String key) {
        return result.getMetadata().getFieldsOrDefault(
                key, Value.newBuilder().setStringValue("").build()
        ).getStringValue();
    }

    public static List<String> getListField(ScoredVectorWithUnsignedIndices result, String key) {
        Value value = result.getMetadata().getFieldsOrDefault(
                key, Value.newBuilder().setStringValue("").build()
        );
        if (!value.hasListValue()) return List.of();
        return value.getListValue().getValuesList().stream()
                .map(Value::getStringValue)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}