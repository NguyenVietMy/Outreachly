package com.pulse.pulse.platform.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.Filter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.qdrant.client.ConditionFactory.match;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Issue 02, against the real cluster: upserts are idempotent because point ids are deterministic,
 * and each of the three delete paths removes exactly the points it should.
 *
 * <p>Excluded from {@code mvn test}; run with {@code ./mvnw test -Pqdrant}.
 * Requires QDRANT_URL and QDRANT_API_KEY in the environment.
 */
@Tag("qdrant")
class QdrantVectorStoreWriteTest {

    /** Negative so it cannot collide with a real user's chunks in the shared collection. */
    private static final long TEST_USER = -42L;

    @Test
    void upsertsIdempotentlyAndMirrorsEveryDeletePath() throws Exception {
        QdrantClient client = new QdrantConfig().qdrantClient(
                requireEnv("QDRANT_URL"), requireEnv("QDRANT_API_KEY"));

        try (client) {
            new QdrantSchemaInitializer(client).ensureCollection();
            QdrantVectorStore store = new QdrantVectorStore(client);
            store.deleteByUser(TEST_USER);

            store.upsert(TEST_USER, "resume_section", "experience", 0, "Built a hybrid retrieval service",
                    denseVector(), Map.of("sectionName", "experience"));
            store.upsert(TEST_USER, "resume_section", "experience", 0, "Built a hybrid retrieval service, revised",
                    denseVector(), Map.of("sectionName", "experience"));
            assertEquals(1, countForTestUser(client), "re-indexing a chunk must replace its point, not add one");

            store.upsertBatch(List.of(
                    new QdrantVectorStore.ChunkPoint(TEST_USER, "goal", "g1", 0, "Goal: ship the agent",
                            denseVector(), Map.of("category", "career")),
                    new QdrantVectorStore.ChunkPoint(TEST_USER, "task", "t1", 0, "Study Task: graph algorithms",
                            denseVector(), Map.of("axis", "dsa"))));
            assertEquals(3, countForTestUser(client));

            store.deleteBySourceKey(TEST_USER, "goal", "g1");
            assertEquals(2, countForTestUser(client));

            store.deleteBySource(TEST_USER, "resume_section");
            assertEquals(1, countForTestUser(client));

            store.deleteByUser(TEST_USER);
            assertEquals(0, countForTestUser(client));
        }
    }

    private static long countForTestUser(QdrantClient client) throws Exception {
        return client.countAsync(QdrantSchemaInitializer.COLLECTION,
                Filter.newBuilder().addMust(match("user_id", TEST_USER)).build(), true).get();
    }

    /**
     * Arity is all that matters here; nothing in this test ranks by similarity. Deliberately not the
     * {@code sin(i)} vector {@link QdrantConnectivityTest} uses, so residue from a failed run of
     * either test cannot tie with the other's nearest-neighbour assertion.
     */
    private static float[] denseVector() {
        float[] vector = new float[1536];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) Math.cos(i);
        }
        return vector;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail(name + " must be set to run this test");
        }
        return value;
    }
}
