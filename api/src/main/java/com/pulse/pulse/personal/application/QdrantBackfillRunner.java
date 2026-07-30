package com.pulse.pulse.personal.application;

import com.pulse.pulse.personal.domain.KnowledgeChunk;
import com.pulse.pulse.personal.infrastructure.persistence.KnowledgeChunkRepository;
import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.vector.QdrantVectorStore;
import com.pulse.pulse.platform.vector.QdrantVectorStore.ChunkPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One-time backfill of the chunks that existed before dual-write was switched on. Off by default;
 * run it with:
 *
 * <pre>./mvnw spring-boot:run -Dspring-boot.run.arguments=--pulse.vector.backfill=true</pre>
 *
 * <p>Content and metadata come from Postgres. Dense vectors are re-embedded rather than read back
 * out of the pgvector column — text-embedding-3-small is deterministic, and a few thousand chunks
 * cost cents at $0.02/1M tokens. Point ids are deterministic, so re-running is harmless.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pulse.vector.backfill", havingValue = "true")
@RequiredArgsConstructor
public class QdrantBackfillRunner implements ApplicationRunner {

    /** Chunks per embedding request; also the Qdrant upsert batch size. */
    private static final int BATCH_SIZE = 100;

    private final KnowledgeChunkRepository chunkRepo;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStore qdrantStore;

    @Override
    public void run(ApplicationArguments args) {
        List<KnowledgeChunk> rows = chunkRepo.findAll();
        log.info("Backfilling {} knowledge chunks into Qdrant", rows.size());

        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<KnowledgeChunk> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            List<float[]> embeddings = embeddingService
                    .embedBatch(batch.stream().map(KnowledgeChunk::getContent).toList())
                    .block();

            List<ChunkPoint> chunks = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                KnowledgeChunk row = batch.get(i);
                chunks.add(new ChunkPoint(row.getUserId(), row.getSourceType(), row.getSourceKey(),
                        row.getChunkIndex(), row.getContent(), embeddings.get(i), row.getMetadata()));
            }
            qdrantStore.upsertBatch(chunks);
            log.info("Backfilled {}/{} chunks", start + batch.size(), rows.size());
        }

        log.info("Backfill complete: {} rows in Postgres, {} points in Qdrant",
                rows.size(), qdrantStore.count());
    }
}
