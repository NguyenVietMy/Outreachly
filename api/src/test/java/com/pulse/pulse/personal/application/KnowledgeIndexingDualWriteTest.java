package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.domain.GitHubRepositoryReader;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.KnowledgeChunkRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.observability.PulseObservability;
import com.pulse.pulse.platform.vector.QdrantVectorStore;
import com.pulse.pulse.platform.vector.QdrantVectorStore.ChunkPoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue 02: every indexing path mirrors into Qdrant, every delete path mirrors too, and a Qdrant
 * failure never fails the Postgres write.
 */
class KnowledgeIndexingDualWriteTest {

    private static final long USER_ID = 7L;

    private KnowledgeChunkRepository chunkRepo;
    private EmbeddingService embeddingService;
    private QdrantVectorStore qdrantStore;
    private KnowledgeIndexingService service;

    @BeforeEach
    void setUp() {
        chunkRepo = Mockito.mock(KnowledgeChunkRepository.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        qdrantStore = Mockito.mock(QdrantVectorStore.class);

        when(embeddingService.embed(anyString())).thenReturn(Mono.just(new float[]{0.1f, 0.2f}));

        service = new KnowledgeIndexingService(
                chunkRepo,
                Mockito.mock(GitHubRepositoryReader.class),
                Mockito.mock(UserProfileRepository.class),
                Mockito.mock(UserGoalRepository.class),
                Mockito.mock(AiTaskRepository.class),
                Mockito.mock(RoadmapItemRepository.class),
                embeddingService,
                new ObjectMapper(),
                new PulseObservability(ObservationRegistry.create(), new SimpleMeterRegistry()),
                Optional.of(qdrantStore)
        );
    }

    @Test
    void writesEachChunkToBothStores() {
        service.indexGitHubReadme(USER_ID, "viet/pulse", "pulse", "Java", "x".repeat(100));

        verify(chunkRepo).upsertChunk(Mockito.eq(USER_ID), Mockito.eq("github_readme"),
                Mockito.eq("viet/pulse"), Mockito.eq(0), anyString(), anyString());

        List<ChunkPoint> mirrored = captureUpsert();
        assertEquals(1, mirrored.size());
        assertEquals("github_readme", mirrored.get(0).sourceType());
        assertEquals("viet/pulse", mirrored.get(0).sourceKey());
        assertEquals("pulse", mirrored.get(0).metadata().get("repoName"));
    }

    @Test
    void keepsThePostgresWriteWhenQdrantFails() {
        doThrow(new IllegalStateException("Qdrant call failed")).when(qdrantStore).upsertBatch(any());

        assertDoesNotThrow(() ->
                service.indexObsidianDiff(USER_ID, "2026-07-30", "studied graph algorithms"));

        verify(chunkRepo).upsertChunk(Mockito.eq(USER_ID), Mockito.eq("obsidian_diff"),
                Mockito.eq("2026-07-30"), anyInt(), anyString(), anyString());
    }

    @Test
    void mirrorsResumeSectionsAsOneBatchAfterMirroringTheDelete() {
        when(embeddingService.embedBatch(any()))
                .thenReturn(Mono.just(List.of(new float[]{0.1f}, new float[]{0.2f})));

        service.indexResumeSections(USER_ID, "Experience\nBuilt Pulse\n\nSkills\nJava, Python\n");

        var order = inOrder(qdrantStore);
        order.verify(qdrantStore).deleteBySource(USER_ID, "resume_section");
        order.verify(qdrantStore).upsertBatch(any());

        assertEquals(2, captureUpsert().size(), "batched callers must stay one round trip");
    }

    @Test
    void mirrorsTheFullUserDelete() {
        service.reindexAll(USER_ID);

        verify(chunkRepo).deleteByUserId(USER_ID);
        verify(qdrantStore).deleteByUser(USER_ID);
    }

    @SuppressWarnings("unchecked")
    private List<ChunkPoint> captureUpsert() {
        ArgumentCaptor<List<ChunkPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantStore).upsertBatch(captor.capture());
        return captor.getValue();
    }
}
