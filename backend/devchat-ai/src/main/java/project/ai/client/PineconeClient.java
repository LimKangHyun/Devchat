package project.ai.client;

import com.google.protobuf.Struct;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.ListResponse;
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
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import static io.pinecone.commons.IndexInterface.buildUpsertVectorWithUnsignedIndices;

@Slf4j
@Component
public class PineconeClient {

    /**
     * Pinecone 동시 호출 상한.
     * 스레드 풀 크기와 별개로 두는 이유: 병목은 스레드가 아니라 Pinecone이다.
     * 구조 검색(요청당 최대 ~20쿼리), 벡터 검색, 인덱싱 upsert가 모두 같은 Pinecone을
     * 쓰므로 호출 지점마다 제한하지 않고 클라이언트에서 한 번에 막는다.
     * 가상 스레드 환경에서는 큐/거부 정책이 없어 이 세마포어가 유일한 제동장치가 된다.
     */
    @Value("${pinecone.max-concurrent-calls:30}")
    private int maxConcurrentCalls;

    private Semaphore semaphore;

    @Value("${pinecone.api-key}")
    private String apiKey;

    @Value("${pinecone.index-name}")
    private String indexName;

    private Index index;

    @PostConstruct
    public void init() {
        Pinecone pinecone = new Pinecone.Builder(apiKey).build();
        this.index = pinecone.getIndexConnection(indexName);
        this.semaphore = new Semaphore(maxConcurrentCalls);
        log.info("Pinecone index connected. indexName={}, maxConcurrentCalls={}",
                indexName, maxConcurrentCalls);
    }

    /**
     * 벡터 upsert
     * id: "{repoId}-{filePath}-{chunkIndex}" 형태
     * metadata: repoId, filePath, chunkIndex, code, language 저장
     */
    public void upsertBatch(List<UpsertItem> items, String namespace) {
        List<VectorWithUnsignedIndices> vectors = items.stream()
                .map(item -> {
                    Struct.Builder metaBuilder = Struct.newBuilder();
                    item.metadata().forEach((k, v) -> {
                        if (v instanceof List<?> list) {
                            com.google.protobuf.ListValue.Builder listBuilder =
                                    com.google.protobuf.ListValue.newBuilder();
                            for (Object element : list) {
                                listBuilder.addValues(
                                        com.google.protobuf.Value.newBuilder()
                                                .setStringValue(String.valueOf(element))
                                                .build()
                                );
                            }
                            metaBuilder.putFields(k,
                                    com.google.protobuf.Value.newBuilder()
                                            .setListValue(listBuilder.build())
                                            .build()
                            );
                        } else {
                            metaBuilder.putFields(k,
                                    com.google.protobuf.Value.newBuilder()
                                            .setStringValue(String.valueOf(v))
                                            .build()
                            );
                        }
                    });

                    List<Float> vectorList = new java.util.ArrayList<>();
                    for (float f : item.vector()) vectorList.add(f);

                    return buildUpsertVectorWithUnsignedIndices(
                            item.id(), vectorList, null, null, metaBuilder.build()
                    );
                })
                .toList();

        UpsertResponse response = withLimit(() -> index.upsert(vectors, namespace));
        log.debug("Pinecone batch upsert. namespace={}, count={}, upsertedCount={}",
                namespace, vectors.size(), response.getUpsertedCount());
    }

    public record UpsertItem(String id, float[] vector, Map<String, Object> metadata) {}

    /**
     * 유사 벡터 Top K 검색
     * metadata 포함해서 반환 (filePath, code 등 꺼내 쓰기 위해)
     */
    public List<ScoredVectorWithUnsignedIndices> query(float[] vector, int topK, String namespace) {
        return withLimit(() -> doQuery(vector, topK, namespace, null));
    }

    public List<ScoredVectorWithUnsignedIndices> queryWithFilter(
            float[] vector, int topK, String namespace, Struct filter) {
        return withLimit(() -> doQuery(vector, topK, namespace, filter));
    }

    /** filter가 null이면 필터 없는 순수 벡터 검색. */
    private List<ScoredVectorWithUnsignedIndices> doQuery(
            float[] vector, int topK, String namespace, Struct filter) {

        List<Float> vectorList = new java.util.ArrayList<>();
        for (float v : vector) vectorList.add(v);

        QueryResponseWithUnsignedIndices response = index.query(
                topK, vectorList, null, null, null, namespace, filter, false, true
        );

        return response.getMatchesList();
    }

    public void deleteNamespace(String namespace) {
        withLimit(() -> {
            index.deleteAll(namespace);
            return null;
        });
        log.info("Pinecone namespace 삭제. namespace={}", namespace);
    }

    public void deleteFileChunks(String repoId, String filePath, String namespace) {
        String prefix = repoId + "-" + filePath.replace("/", "_") + "-";
        ListResponse listResponse = withLimit(() -> index.list(namespace, prefix));

        List<String> idsToDelete = listResponse.getVectorsList().stream()
                .map(v -> v.getId())
                .toList();

        if (!idsToDelete.isEmpty()) {
            withLimit(() -> {
                index.deleteByIds(idsToDelete, namespace);
                return null;
            });
            log.info("Pinecone 파일 청크 삭제. filePath={}, count={}", filePath, idsToDelete.size());
        }
    }

    /**
     * 세마포어로 동시 호출 수를 제한하며 Pinecone 호출을 실행한다.
     * 가상 스레드에서 블록되면 캐리어 스레드를 점유하지 않으므로 대기 비용이 낮다.
     */
    private <T> T withLimit(Supplier<T> call) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pinecone 호출 대기 중 인터럽트", e);
        }
        try {
            return call.get();
        } finally {
            semaphore.release();
        }
    }
}