package com.pulse.pulse.personal.application;

import com.pulse.pulse.personal.domain.KnowledgeChunk;
import com.pulse.pulse.personal.infrastructure.persistence.KnowledgeChunkRepository;
import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.vector.QdrantVectorStore;
import com.pulse.pulse.platform.vector.QdrantVectorStore.ChunkPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QdrantBackfillRunnerTest {

    @Test
    void upsertsEveryPostgresRowWithAFreshEmbedding() {
        KnowledgeChunkRepository chunkRepo = Mockito.mock(KnowledgeChunkRepository.class);
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);
        QdrantVectorStore qdrantStore = Mockito.mock(QdrantVectorStore.class);

        when(chunkRepo.findAll()).thenReturn(List.of(
                chunk(1L, "resume_section", "experience", 0, "Built Pulse"),
                chunk(2L, "goal", "g1", 0, "Goal: ship the agent")));
        when(embeddingService.embedBatch(List.of("Built Pulse", "Goal: ship the agent")))
                .thenReturn(Mono.just(List.of(new float[]{0.1f}, new float[]{0.2f})));

        new QdrantBackfillRunner(chunkRepo, embeddingService, qdrantStore).run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChunkPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantStore).upsertBatch(captor.capture());

        List<ChunkPoint> upserted = captor.getValue();
        assertEquals(2, upserted.size());
        assertEquals(1L, upserted.get(0).userId());
        assertEquals("resume_section", upserted.get(0).sourceType());
        assertEquals("Built Pulse", upserted.get(0).content());
        assertEquals("experience", upserted.get(0).metadata().get("sectionName"));
        assertEquals("g1", upserted.get(1).sourceKey());
    }

    private static KnowledgeChunk chunk(long userId, String sourceType, String sourceKey,
                                        int chunkIndex, String content) {
        return KnowledgeChunk.builder()
                .userId(userId)
                .sourceType(sourceType)
                .sourceKey(sourceKey)
                .chunkIndex(chunkIndex)
                .content(content)
                .metadata(Map.of("sectionName", sourceKey))
                .build();
    }
}
