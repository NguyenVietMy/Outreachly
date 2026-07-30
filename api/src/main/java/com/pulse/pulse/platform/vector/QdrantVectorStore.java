package com.pulse.pulse.platform.vector;

import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.PointStruct;
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
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorFactory.vector;
import static io.qdrant.client.VectorsFactory.namedVectors;

/**
 * Writes knowledge chunks to Qdrant, mirroring what {@code knowledge_chunks} holds in Postgres.
 *
 * <p>Gated on both {@code qdrant.enabled} (is there a cluster to talk to?) and
 * {@code pulse.vector.dual-write} (should we mirror writes to it?). Issue 03 moves reads here and
 * will widen the condition then.
 *
 * <p>Every method throws on failure; callers that must not fail because of Qdrant catch and log.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = {"qdrant.enabled", "pulse.vector.dual-write"}, havingValue = "true")
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

    /** One chunk, in the shape both the indexing seam and the backfill already have it. */
    public record ChunkPoint(Long userId, String sourceType, String sourceKey, int chunkIndex,
                             String content, float[] denseVector, Map<String, Object> metadata) {}

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

    private static Condition userCondition(Long userId) {
        return match("user_id", userId);
    }

    private static PointStruct toPoint(ChunkPoint chunk) {
        return PointStruct.newBuilder()
                .setId(id(pointId(chunk.userId(), chunk.sourceType(), chunk.sourceKey(), chunk.chunkIndex())))
                .setVectors(namedVectors(Map.of(
                        QdrantSchemaInitializer.DENSE_VECTOR, vector(chunk.denseVector()),
                        QdrantSchemaInitializer.SPARSE_VECTOR, vector(Document.newBuilder()
                                .setModel(BM25_MODEL)
                                .setText(chunk.content())
                                .build()))))
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
