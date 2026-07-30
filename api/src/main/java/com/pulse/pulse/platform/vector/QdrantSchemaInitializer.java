package com.pulse.pulse.platform.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.Modifier;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.SparseVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorParamsMap;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Creates the {@code knowledge_chunks} collection on startup if it is absent. Idempotent: an
 * existing collection is left untouched, so this is safe to run on every boot and on every task
 * in a multi-task ECS service.
 *
 * <p>The sparse vector carries the IDF modifier because Qdrant computes BM25 term weights
 * server-side and needs the collection-wide document frequencies to do it.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "qdrant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class QdrantSchemaInitializer implements ApplicationRunner {

    public static final String COLLECTION = "knowledge_chunks";
    public static final String DENSE_VECTOR = "dense";
    public static final String SPARSE_VECTOR = "bm25";

    /** Matches EmbeddingService's text-embedding-3-small output. */
    private static final int DENSE_SIZE = 1536;

    private final QdrantClient qdrantClient;

    @Override
    public void run(ApplicationArguments args) throws ExecutionException, InterruptedException {
        ensureCollection();
    }

    /** Package-private so the connectivity test can bootstrap without a Spring context. */
    void ensureCollection() throws ExecutionException, InterruptedException {
        if (qdrantClient.collectionExistsAsync(COLLECTION).get()) {
            log.info("Qdrant collection {} already exists", COLLECTION);
            return;
        }

        qdrantClient.createCollectionAsync(CreateCollection.newBuilder()
                .setCollectionName(COLLECTION)
                .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParamsMap(VectorParamsMap.newBuilder()
                                .putMap(DENSE_VECTOR, VectorParams.newBuilder()
                                        .setSize(DENSE_SIZE)
                                        .setDistance(Distance.Cosine)
                                        .build())))
                .setSparseVectorsConfig(SparseVectorConfig.newBuilder()
                        .putMap(SPARSE_VECTOR, SparseVectorParams.newBuilder()
                                .setModifier(Modifier.Idf)
                                .build()))
                .build()).get();

        qdrantClient.createPayloadIndexAsync(COLLECTION, "user_id",
                PayloadSchemaType.Integer, null, true, null, null).get();
        qdrantClient.createPayloadIndexAsync(COLLECTION, "source_type",
                PayloadSchemaType.Keyword, null, true, null, null).get();

        log.info("Created Qdrant collection {}", COLLECTION);
    }
}
