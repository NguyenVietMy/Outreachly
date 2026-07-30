package com.pulse.pulse.platform.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorFactory.vector;
import static io.qdrant.client.VectorsFactory.namedVectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves against the real cluster that the Java client can drive server-side BM25 inference —
 * the assumption the whole Qdrant track rests on (issue 01). Bootstraps the collection, upserts one
 * point whose sparse vector is a {@link Document} rather than pre-computed indices/values, then
 * retrieves it by a dense query and by a BM25 text query.
 *
 * <p>Excluded from {@code mvn test}; run with {@code ./mvnw test -Pqdrant}.
 * Requires QDRANT_URL and QDRANT_API_KEY in the environment.
 */
@Tag("qdrant")
class QdrantConnectivityTest {

    private static final long TEST_POINT_ID = 999_999_999L;
    private static final String TEXT =
            "Implemented a hybrid retrieval service combining dense embeddings with BM25 keyword search.";

    @Test
    void upsertsAndRetrievesByBothDenseAndBm25() throws Exception {
        QdrantClient client = new QdrantConfig().qdrantClient(
                requireEnv("QDRANT_URL"), requireEnv("QDRANT_API_KEY"));

        try (client) {
            new QdrantSchemaInitializer(client).ensureCollection();

            client.upsertAsync(QdrantSchemaInitializer.COLLECTION, List.of(PointStruct.newBuilder()
                    .setId(id(TEST_POINT_ID))
                    .setVectors(namedVectors(Map.of(
                            QdrantSchemaInitializer.DENSE_VECTOR, vector(denseVector()),
                            QdrantSchemaInitializer.SPARSE_VECTOR, vector(Document.newBuilder()
                                    .setModel("qdrant/bm25")
                                    .setText(TEXT)
                                    .build()))))
                    .putPayload("user_id", value(1))
                    .putPayload("source_type", value("resume"))
                    .build())).get();

            List<ScoredPoint> dense = client.queryAsync(QueryPoints.newBuilder()
                    .setCollectionName(QdrantSchemaInitializer.COLLECTION)
                    .setUsing(QdrantSchemaInitializer.DENSE_VECTOR)
                    .setQuery(nearest(denseVector()))
                    .setLimit(1)
                    .build()).get();

            assertEquals(1, dense.size(), "dense query returned nothing");
            assertEquals(TEST_POINT_ID, dense.get(0).getId().getNum());

            List<ScoredPoint> bm25 = client.queryAsync(QueryPoints.newBuilder()
                    .setCollectionName(QdrantSchemaInitializer.COLLECTION)
                    .setUsing(QdrantSchemaInitializer.SPARSE_VECTOR)
                    .setQuery(nearest(Document.newBuilder()
                            .setModel("qdrant/bm25")
                            .setText("hybrid retrieval BM25 keyword")
                            .build()))
                    .setLimit(1)
                    .build()).get();

            assertEquals(1, bm25.size(), "BM25 query returned nothing — server-side inference failed");
            assertEquals(TEST_POINT_ID, bm25.get(0).getId().getNum());
            assertTrue(bm25.get(0).getScore() > 0, "BM25 score should be positive");

            client.deleteAsync(QdrantSchemaInitializer.COLLECTION, List.of(id(TEST_POINT_ID))).get();
        }
    }

    /**
     * A deterministic unit-length-ish stand-in for a real embedding. The test only needs a vector of
     * the right arity — it never compares against a semantically meaningful neighbour.
     */
    private static List<Float> denseVector() {
        List<Float> values = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            values.add((float) Math.sin(i));
        }
        return values;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " must be set to run this test");
        }
        return value;
    }
}
