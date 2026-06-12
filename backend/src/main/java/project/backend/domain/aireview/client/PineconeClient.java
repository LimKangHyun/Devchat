package project.backend.domain.aireview.client;

import com.google.protobuf.Struct;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.UpsertResponse;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.pinecone.commons.IndexInterface.buildUpsertVectorWithUnsignedIndices;

@Slf4j
@Component
public class PineconeClient {

    @Value("${pinecone.api-key}")
    private String apiKey;

    @Value("${pinecone.index-name}")
    private String indexName;

    private Index index;

    // 앱 시작 시 한 번만 연결 생성, 이후 재사용
    @PostConstruct
    public void init() {
        Pinecone pinecone = new Pinecone.Builder(apiKey).build();
        this.index = pinecone.getIndexConnection(indexName);
        log.info("Pinecone index connected. indexName={}", indexName);
    }

    /**
     * 벡터 upsert
     * id: "{repoId}-{filePath}-{chunkIndex}" 형태
     * metadata: repoId, filePath, chunkIndex, code, language 저장
     */
    public void upsert(String id, float[] vector, Map<String, String> metadata, String namespace) {
        Struct.Builder metaBuilder = Struct.newBuilder();
        metadata.forEach((k, v) ->
                metaBuilder.putFields(k, com.google.protobuf.Value.newBuilder().setStringValue(v).build())
        );

        List<Float> vectorList = new java.util.ArrayList<>();
        for (float v : vector) vectorList.add(v);

        VectorWithUnsignedIndices vec = buildUpsertVectorWithUnsignedIndices(
                id, vectorList, null, null, metaBuilder.build()
        );

        UpsertResponse response = index.upsert(List.of(vec), namespace);
        log.debug("Pinecone upsert. id={}, namespace={}, upsertedCount={}", id, namespace, response.getUpsertedCount());
    }

    /**
     * 유사 벡터 Top K 검색
     * metadata 포함해서 반환 (filePath, code 등 꺼내 쓰기 위해)
     */
    public List<ScoredVectorWithUnsignedIndices> query(float[] vector, int topK, String namespace) {
        List<Float> vectorList = new java.util.ArrayList<>();
        for (float v : vector) vectorList.add(v);

        QueryResponseWithUnsignedIndices response = index.query(
                topK, vectorList, null, null, null, namespace, null, false, true
        );

        return response.getMatchesList();
    }

    public void deleteNamespace(String namespace) {
        index.deleteAll(namespace);
        log.info("Pinecone namespace 삭제. namespace={}", namespace);
    }
}