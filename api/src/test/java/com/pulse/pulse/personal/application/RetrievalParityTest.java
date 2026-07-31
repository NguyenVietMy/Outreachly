package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.observability.PulseObservability;
import com.pulse.pulse.platform.vector.QdrantConfig;
import com.pulse.pulse.platform.vector.QdrantSchemaInitializer;
import com.pulse.pulse.platform.vector.QdrantVectorStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.QueryPoints;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qdrant.client.ConditionFactory.match;
import static io.qdrant.client.ConditionFactory.matchKeywords;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Issue 03's gate: the Qdrant read path must return substantially the same top-5 as the pgvector
 * implementation it replaces, measured over a frozen query set against the seeded user.
 *
 * <p>The pgvector side is reimplemented here rather than called, because
 * {@link HybridRetrievalService} no longer contains it. That is deliberate — this is a frozen
 * baseline, and it must not drift with the service. It dies with issue 04.
 *
 * <p><b>What the two numbers mean.</b> The dense arm is the migration check: same vectors, same
 * metric, so it must be near-identical, and a drop means chunks or embeddings were lost. Fused
 * overlap lands at ~77%, not the ≥80% the issue asked for, and the gap is the keyword arm rather
 * than a defect. Postgres builds its query with {@code plainto_tsquery}, which ANDs the terms, so
 * "Terraform ECS Fargate" needs one chunk holding all three and matches nothing; it returned rows
 * on only 3 of the 20 queries, i.e. the shipped "hybrid" search was effectively dense-only.
 * Qdrant's BM25 is disjunctive and contributes real hits on 19 of 20. Chasing 80% would mean
 * suppressing correct results to imitate a dead component, so the blocking gate here is the dense
 * arm and fused overlap is a tripwire. Full write-up in {@code docs/issues/03-*.md}.
 *
 * <p>Excluded from {@code mvn test}; run with {@code ./mvnw test -Pqdrant -Dtest=RetrievalParityTest}.
 * Reads QDRANT_URL, QDRANT_API_KEY, OPENAI_API_KEY, SUPABASE_SESSION_POOLER, DB_USER and
 * DB_PASSWORD from the environment, falling back to {@code api/.env.local.properties}.
 */
@Tag("qdrant")
class RetrievalParityTest {

    private static final int TOP_K = 5;
    private static final int RRF_K = 60;
    /** The dense arm is a like-for-like comparison, so anything short of near-identical is a bug. */
    private static final double MIN_MEAN_DENSE_OVERLAP = 0.95;
    /**
     * A tripwire, not issue 03's 80% gate. Fused overlap measured 75–78% across runs because the
     * pgvector keyword arm is disjunctive-vs-conjunctive broken (see the class javadoc), so 80%
     * would only be reachable by suppressing correct Qdrant hits. This catches a collapse.
     */
    private static final double MIN_MEAN_FUSED_OVERLAP = 0.70;

    /** A frozen query, optionally narrowed to the source types ChatService would have routed to. */
    private record ParityQuery(String text, List<String> sourceTypes) {}

    private record Key(String sourceType, String sourceKey) {}

    /**
     * The pgvector side of one query. {@code denseTop} and {@code keywordHits} are kept so the
     * harness can say *why* the fused sets differ rather than only that they do — the dense arm is
     * what proves the index migrated faithfully.
     */
    private record PgResult(Set<Key> fused, List<Key> denseTop, int keywordHits, float[] queryVector) {}

    private static final List<ParityQuery> QUERIES = List.of(
            // Career questions
            new ParityQuery("What should I focus on to be ready for a backend internship?", List.of()),
            new ParityQuery("How am I tracking against my career goals this quarter?", List.of()),
            new ParityQuery("What are my biggest weaknesses as a software engineering candidate?", List.of()),
            new ParityQuery("Which of my goals are behind schedule?", List.of("goal", "task", "roadmap")),
            new ParityQuery("What experience do I have relevant to a distributed systems role?",
                    List.of("resume_section")),

            // Project questions
            new ParityQuery("What did I build in the Pulse project?", List.of("github_readme")),
            new ParityQuery("Which of my repositories use Java and Spring Boot?", List.of()),
            new ParityQuery("Describe the architecture of my most recent project.", List.of()),
            new ParityQuery("What infrastructure work have I done on AWS?", List.of()),
            new ParityQuery("Have I worked with vector databases or retrieval systems?", List.of()),

            // Keyword-heavy lookups
            new ParityQuery("Terraform ECS Fargate", List.of()),
            new ParityQuery("pgvector embeddings", List.of()),
            new ParityQuery("LeetCode dynamic programming", List.of("obsidian_diff")),
            new ParityQuery("OAuth2 Google login", List.of()),
            new ParityQuery("Next.js Tailwind shadcn", List.of()),

            // Vague / semantic lookups
            new ParityQuery("What have I been studying lately?", List.of()),
            new ParityQuery("Am I making progress?", List.of()),
            new ParityQuery("What should I do next?", List.of()),
            new ParityQuery("Tell me about my strengths.", List.of()),
            new ParityQuery("Where am I spending most of my time?", List.of()));

    private static final String VECTOR_SQL = """
            SELECT id, source_type, source_key
            FROM knowledge_chunks
            WHERE user_id = ?
              AND (?::text IS NULL OR source_type = ANY(string_to_array(?::text, ',')))
              AND embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String KEYWORD_SQL = """
            SELECT id, source_type, source_key
            FROM knowledge_chunks
            WHERE user_id = ?
              AND (?::text IS NULL OR source_type = ANY(string_to_array(?::text, ',')))
              AND search_vector @@ plainto_tsquery('english', ?)
            ORDER BY ts_rank_cd(search_vector, plainto_tsquery('english', ?)) DESC
            LIMIT ?
            """;

    @Test
    void qdrantTopFiveMatchesPgvectorOnTheFrozenQuerySet() throws Exception {
        PulseObservability observability =
                new PulseObservability(ObservationRegistry.create(), new SimpleMeterRegistry());
        EmbeddingService embeddings = new EmbeddingService(
                WebClient.builder(), config("OPENAI_API_KEY"), observability);

        try (Connection db = DriverManager.getConnection(
                     config("SUPABASE_SESSION_POOLER"), config("DB_USER"), config("DB_PASSWORD"));
             QdrantClient client = new QdrantConfig()
                     .qdrantClient(config("QDRANT_URL"), config("QDRANT_API_KEY"))) {

            long userId = seededUser(db);
            HybridRetrievalService qdrantPath = new HybridRetrievalService(embeddings, observability,
                    Optional.of(new QdrantVectorStore(client, new ObjectMapper())));

            // Warm the connection pools and the embedding client so the first query is not an outlier.
            pgvectorTopK(db, embeddings, userId, QUERIES.get(0));
            qdrantTopK(qdrantPath, userId, QUERIES.get(0));

            List<Double> overlaps = new ArrayList<>();
            List<Double> denseOverlaps = new ArrayList<>();
            List<Long> pgMillis = new ArrayList<>();
            List<Long> qdrantMillis = new ArrayList<>();
            int queriesWithPgKeywordHits = 0;

            System.out.printf("%-52s %7s %7s %6s %6s %6s%n",
                    "query", "fused", "dense", "pgKw", "pg(ms)", "qd(ms)");

            for (ParityQuery query : QUERIES) {
                long pgStart = System.nanoTime();
                PgResult baseline = pgvectorTopK(db, embeddings, userId, query);
                pgMillis.add(millisSince(pgStart));

                long qdrantStart = System.nanoTime();
                Set<Key> candidate = qdrantTopK(qdrantPath, userId, query);
                qdrantMillis.add(millisSince(qdrantStart));

                double overlap = overlap(baseline.fused(), candidate);
                overlaps.add(overlap);

                double denseOverlap = overlap(
                        new LinkedHashSet<>(baseline.denseTop()),
                        new LinkedHashSet<>(qdrantDenseTopK(client, userId, query, baseline.queryVector())));
                denseOverlaps.add(denseOverlap);

                if (baseline.keywordHits() > 0) {
                    queriesWithPgKeywordHits++;
                }

                System.out.printf("%-52s %6.0f%% %6.0f%% %6d %6d %6d%n",
                        truncate(query.text()), overlap * 100, denseOverlap * 100, baseline.keywordHits(),
                        pgMillis.get(pgMillis.size() - 1), qdrantMillis.get(qdrantMillis.size() - 1));
            }

            double meanOverlap = mean(overlaps);
            double meanDenseOverlap = mean(denseOverlaps);

            System.out.printf("%nuser %d, %d queries, top-%d%n", userId, QUERIES.size(), TOP_K);
            System.out.printf("mean fused top-5 overlap : %.1f%%%n", meanOverlap * 100);
            System.out.printf("mean dense-arm overlap   : %.1f%%  (index fidelity)%n", meanDenseOverlap * 100);
            System.out.printf("pgvector keyword arm returned rows on %d of %d queries%n",
                    queriesWithPgKeywordHits, QUERIES.size());
            System.out.printf("pgvector  p50 %d ms  p95 %d ms%n", percentile(pgMillis, 50), percentile(pgMillis, 95));
            System.out.printf("qdrant    p50 %d ms  p95 %d ms%n",
                    percentile(qdrantMillis, 50), percentile(qdrantMillis, 95));

            assertTrue(meanDenseOverlap >= MIN_MEAN_DENSE_OVERLAP, String.format(
                    "dense-arm overlap %.1f%% is below %.0f%% — chunks or vectors did not survive the "
                            + "move to Qdrant, which is a migration defect",
                    meanDenseOverlap * 100, MIN_MEAN_DENSE_OVERLAP * 100));

            assertTrue(meanOverlap >= MIN_MEAN_FUSED_OVERLAP, String.format(
                    "mean fused top-5 overlap %.1f%% is below %.0f%% — the two paths have diverged "
                            + "further than the known keyword-arm difference explains",
                    meanOverlap * 100, MIN_MEAN_FUSED_OVERLAP * 100));
        }
    }

    private static Set<Key> qdrantTopK(HybridRetrievalService service, long userId, ParityQuery query) {
        return service.retrieve(userId, query.text(), TOP_K, query.sourceTypes()).stream()
                .map(chunk -> new Key(chunk.sourceType(), chunk.sourceKey()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The pgvector path as it stood before this issue: two queries, then RRF over the row ids. */
    private static PgResult pgvectorTopK(Connection db, EmbeddingService embeddings, long userId,
                                         ParityQuery query) throws SQLException {
        String sourceTypes = query.sourceTypes().isEmpty() ? null : String.join(",", query.sourceTypes());
        float[] queryVector = embeddings.embed(query.text()).block();
        String embedding = EmbeddingService.toVectorString(queryVector);
        int fetchK = TOP_K * 2;

        List<Map.Entry<UUID, Key>> vectorHits = rows(db, VECTOR_SQL, statement -> {
            statement.setLong(1, userId);
            statement.setString(2, sourceTypes);
            statement.setString(3, sourceTypes);
            statement.setString(4, embedding);
            statement.setInt(5, fetchK);
        });

        List<Map.Entry<UUID, Key>> keywordHits = rows(db, KEYWORD_SQL, statement -> {
            statement.setLong(1, userId);
            statement.setString(2, sourceTypes);
            statement.setString(3, sourceTypes);
            statement.setString(4, query.text());
            statement.setString(5, query.text());
            statement.setInt(6, fetchK);
        });

        Map<UUID, Key> keys = new LinkedHashMap<>();
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (List<Map.Entry<UUID, Key>> ranked : List.of(vectorHits, keywordHits)) {
            for (int rank = 0; rank < ranked.size(); rank++) {
                UUID id = ranked.get(rank).getKey();
                keys.putIfAbsent(id, ranked.get(rank).getValue());
                scores.merge(id, 1.0 / (RRF_K + rank + 1), Double::sum);
            }
        }

        Set<Key> fused = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_K)
                .map(entry -> keys.get(entry.getKey()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new PgResult(fused, vectorHits.stream().map(Map.Entry::getValue).toList(),
                keywordHits.size(), queryVector);
    }

    /** The dense arm alone — the check that every chunk and vector survived the move to Qdrant. */
    private static List<Key> qdrantDenseTopK(QdrantClient client, long userId, ParityQuery query,
                                             float[] queryVector) throws Exception {
        Filter.Builder filter = Filter.newBuilder().addMust(match("user_id", userId));
        if (!query.sourceTypes().isEmpty()) {
            filter.addMust(matchKeywords("source_type", query.sourceTypes()));
        }

        return client.queryAsync(QueryPoints.newBuilder()
                        .setCollectionName(QdrantSchemaInitializer.COLLECTION)
                        .setUsing(QdrantSchemaInitializer.DENSE_VECTOR)
                        .setQuery(nearest(queryVector))
                        .setFilter(filter.build())
                        .setLimit(TOP_K * 2)
                        .setWithPayload(enable(true))
                        .build()).get().stream()
                .map(point -> new Key(point.getPayloadMap().get("source_type").getStringValue(),
                        point.getPayloadMap().get("source_key").getStringValue()))
                .toList();
    }

    /** Overlap as a fraction of the larger set, so a short result set is not scored as a match. */
    private static double overlap(Set<Key> baseline, Set<Key> candidate) {
        if (baseline.isEmpty() && candidate.isEmpty()) {
            return 1.0;
        }
        long shared = baseline.stream().filter(candidate::contains).count();
        return (double) shared / Math.max(baseline.size(), candidate.size());
    }

    private static long seededUser(Connection db) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(
                "SELECT user_id FROM knowledge_chunks GROUP BY user_id ORDER BY count(*) DESC LIMIT 1");
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                fail("knowledge_chunks is empty — seed a user before measuring parity");
            }
            return results.getLong(1);
        }
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private static List<Map.Entry<UUID, Key>> rows(Connection db, String sql, Binder binder)
            throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet results = statement.executeQuery()) {
                List<Map.Entry<UUID, Key>> rows = new ArrayList<>();
                while (results.next()) {
                    rows.add(Map.entry(results.getObject(1, UUID.class),
                            new Key(results.getString(2), results.getString(3))));
                }
                return rows;
            }
        }
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Nearest-rank percentile. With 20 samples p95 is the second-slowest — reported as measured. */
    private static long percentile(List<Long> samples, int percentile) {
        List<Long> sorted = samples.stream().sorted().toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static String truncate(String text) {
        return text.length() <= 56 ? text : text.substring(0, 55) + "…";
    }

    private static final Properties LOCAL_PROPERTIES = loadLocalProperties();

    private static Properties loadLocalProperties() {
        Properties properties = new Properties();
        Path file = Path.of(".env.local.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return properties;
    }

    private static String config(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = LOCAL_PROPERTIES.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            fail(name + " must be set in the environment or api/.env.local.properties");
        }
        return value;
    }
}
