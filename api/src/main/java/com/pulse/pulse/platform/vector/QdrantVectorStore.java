package com.pulse.pulse.platform.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PrefetchQuery;
import io.qdrant.client.grpc.Points.Query;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.Rrf;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.ConditionFactory.match;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.ConditionFactory.matchKeywords;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.QueryFactory.rrf;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorFactory.vector;
import static io.qdrant.client.VectorsFactory.namedVectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

/**
 * Reads and writes knowledge chunks in Qdrant, mirroring what {@code knowledge_chunks} holds in
 * Postgres.
 *
 * <p>Gated on {@code qdrant.enabled} alone since issue 03 — retrieval reads through this component,
 * so it must exist wherever there is a cluster to talk to. Whether writes are *mirrored* is the
 * caller's decision ({@code pulse.vector.dual-write} in {@code KnowledgeIndexingService}).
 *
 * <p>Every method throws on failure; callers that must not fail because of Qdrant catch and log.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "qdrant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class QdrantVectorStore {

    /** Qdrant infers the sparse vector from the chunk text server-side; no BM25 encoder in Java. */
    private static final String BM25_MODEL = "qdrant/bm25";

    /**
     * Namespace for the UUIDv5 point ids. Arbitrary, but fixed forever — changing it orphans every
     * point already in the collection.
     */
    private static final UUID ID_NAMESPACE = UUID.fromString("9d5a3f6c-8f3b-4a5e-9e7d-1c2b3a4d5e6f");

    private final QdrantClient client;
    private final ObjectMapper objectMapper;

    /** One chunk, in the shape both the indexing seam and the backfill already have it. */
    public record ChunkPoint(Long userId, String sourceType, String sourceKey, int chunkIndex,
                             String content, float[] denseVector, Map<String, Object> metadata) {}

    /**
     * One hybrid retrieval: a dense prefetch and a BM25 prefetch over the same filter, fused
     * server-side with RRF. {@code rrfK} is passed through to Qdrant so fused scores land on the
     * scale the caller's threshold assumes.
     */
    public record HybridQuery(Long userId, List<String> sourceTypes, String queryText,
                              float[] denseVector, int prefetchLimit, int limit, int rrfK) {}

    /**
     * One fused hit. {@code score} is the RRF score — Qdrant consumes the two prefetch scores
     * during fusion and does not return them, so there is no dense/BM25 breakdown to hand back.
     */
    public record SearchHit(String sourceType, String sourceKey, String content, String metadata,
                            double score) {}

    public void upsert(Long userId, String sourceType, String sourceKey, int chunkIndex,
                       String content, float[] denseVector, Map<String, Object> metadata) {
        upsertBatch(List.of(new ChunkPoint(userId, sourceType, sourceKey, chunkIndex, content,
                denseVector, metadata)));
    }

    public void upsertBatch(List<ChunkPoint> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        await(client.upsertAsync(QdrantSchemaInitializer.COLLECTION,
                chunks.stream().map(QdrantVectorStore::toPoint).toList()));
    }

    public List<SearchHit> hybridSearch(HybridQuery query) {
        Filter filter = searchFilter(query.userId(), query.sourceTypes());

        List<ScoredPoint> hits = await(client.queryAsync(QueryPoints.newBuilder()
                .setCollectionName(QdrantSchemaInitializer.COLLECTION)
                .addPrefetch(prefetch(QdrantSchemaInitializer.DENSE_VECTOR,
                        nearest(query.denseVector()), filter, query.prefetchLimit()))
                .addPrefetch(prefetch(QdrantSchemaInitializer.SPARSE_VECTOR,
                        nearest(bm25Document(query.queryText())), filter, query.prefetchLimit()))
                .setQuery(rrf(Rrf.newBuilder().setK(query.rrfK()).build()))
                .setLimit(query.limit())
                .setWithPayload(enable(true))
                .build()));

        return hits.stream().map(this::toSearchHit).toList();
    }

    public void deleteByUser(Long userId) {
        delete(Filter.newBuilder().addMust(userCondition(userId)).build());
    }

    public void deleteBySource(Long userId, String sourceType) {
        delete(Filter.newBuilder()
                .addMust(userCondition(userId))
                .addMust(matchKeyword("source_type", sourceType))
                .build());
    }

    public void deleteBySourceKey(Long userId, String sourceType, String sourceKey) {
        delete(Filter.newBuilder()
                .addMust(userCondition(userId))
                .addMust(matchKeyword("source_type", sourceType))
                .addMust(matchKeyword("source_key", sourceKey))
                .build());
    }

    /** Total points in the collection — the backfill compares it against the Postgres row count. */
    public long count() {
        return await(client.countAsync(QdrantSchemaInitializer.COLLECTION));
    }

    /**
     * Deterministic UUIDv5 over {@code user_id|source_type|source_key|chunk_index}, so re-indexing a
     * chunk replaces its point in place — matching the SQL {@code ON CONFLICT} semantics of
     * {@code KnowledgeChunkRepository.upsertChunk}.
     */
    public static UUID pointId(Long userId, String sourceType, String sourceKey, int chunkIndex) {
        String name = userId + "|" + sourceType + "|" + sourceKey + "|" + chunkIndex;

        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required for UUIDv5 point ids", e);
        }
        sha1.update(ByteBuffer.allocate(16)
                .putLong(ID_NAMESPACE.getMostSignificantBits())
                .putLong(ID_NAMESPACE.getLeastSignificantBits())
                .array());
        sha1.update(name.getBytes(StandardCharsets.UTF_8));

        byte[] hash = sha1.digest();
        hash[6] = (byte) ((hash[6] & 0x0F) | 0x50);  // version 5
        hash[8] = (byte) ((hash[8] & 0x3F) | 0x80);  // IETF variant

        ByteBuffer bits = ByteBuffer.wrap(hash, 0, 16);
        return new UUID(bits.getLong(), bits.getLong());
    }

    private void delete(Filter filter) {
        await(client.deleteAsync(QdrantSchemaInitializer.COLLECTION, filter));
    }

    /** Both prefetches carry the filter; a fusion query has no vector search of its own to filter. */
    private static Filter searchFilter(Long userId, List<String> sourceTypes) {
        Filter.Builder filter = Filter.newBuilder().addMust(userCondition(userId));
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            filter.addMust(matchKeywords("source_type", sourceTypes));
        }
        return filter.build();
    }

    private static PrefetchQuery prefetch(String vectorName, Query query, Filter filter, int limit) {
        return PrefetchQuery.newBuilder()
                .setUsing(vectorName)
                .setQuery(query)
                .setFilter(filter)
                .setLimit(limit)
                .build();
    }

    private SearchHit toSearchHit(ScoredPoint point) {
        Map<String, Value> payload = point.getPayloadMap();
        return new SearchHit(
                payload.get("source_type").getStringValue(),
                payload.get("source_key").getStringValue(),
                payload.get("content").getStringValue(),
                metadataJson(payload.get("metadata")),
                point.getScore());
    }

    /** Back to the JSON string shape {@code RetrievedChunk} carried when it came from jsonb. */
    private String metadataJson(Value metadata) {
        Map<String, String> fields = new LinkedHashMap<>();
        metadata.getStructValue().getFieldsMap()
                .forEach((key, entry) -> fields.put(key, entry.getStringValue()));
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize Qdrant chunk metadata", e);
            return "{}";
        }
    }

    private static Condition userCondition(Long userId) {
        return match("user_id", userId);
    }

    private static Document bm25Document(String text) {
        return Document.newBuilder().setModel(BM25_MODEL).setText(text).build();
    }

    private static PointStruct toPoint(ChunkPoint chunk) {
        return PointStruct.newBuilder()
                .setId(id(pointId(chunk.userId(), chunk.sourceType(), chunk.sourceKey(), chunk.chunkIndex())))
                .setVectors(namedVectors(Map.of(
                        QdrantSchemaInitializer.DENSE_VECTOR, vector(chunk.denseVector()),
                        QdrantSchemaInitializer.SPARSE_VECTOR, vector(bm25Document(chunk.content())))))
                .putPayload("user_id", value(chunk.userId().longValue()))
                .putPayload("source_type", value(chunk.sourceType()))
                .putPayload("source_key", value(chunk.sourceKey()))
                .putPayload("chunk_index", value(chunk.chunkIndex()))
                .putPayload("content", value(chunk.content()))
                .putPayload("metadata", value(toPayloadMap(chunk.metadata())))
                .build();
    }

    /** Chunk metadata values are strings everywhere they are produced today. */
    private static Map<String, Value> toPayloadMap(Map<String, Object> metadata) {
        Map<String, Value> payload = new LinkedHashMap<>();
        metadata.forEach((key, entry) -> payload.put(key, value(String.valueOf(entry))));
        return payload;
    }

    private static <T> T await(ListenableFuture<T> call) {
        try {
            return call.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Qdrant", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Qdrant call failed", e.getCause());
        }
    }
}
