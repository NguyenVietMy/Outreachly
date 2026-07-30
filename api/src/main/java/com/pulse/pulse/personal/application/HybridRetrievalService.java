package com.pulse.pulse.personal.application;

import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.observability.PulseObservability;
import com.pulse.pulse.platform.vector.QdrantVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRetrievalService {

    /** Passed through to Qdrant's server-side RRF so scores stay on MIN_RRF_THRESHOLD's scale. */
    private static final int RRF_K = 60;
    private static final double MIN_RRF_THRESHOLD = 0.008;

    private final EmbeddingService embeddingService;
    private final PulseObservability observability;
    /** Absent only when {@code qdrant.enabled=false}; retrieval has no other backing store. */
    private final Optional<QdrantVectorStore> qdrantStore;

    public List<RetrievedChunk> retrieve(Long userId, String query, int topK, List<String> sourceTypes) {
        return observability.observe("pulse.rag.retrieve", obs -> {
            observability.high(obs, "user.id", userId);
            observability.high(obs, "query.length", query.length());
            observability.low(obs, "top_k", String.valueOf(topK));
        }, () -> {
            QdrantVectorStore store = qdrantStore.orElseThrow(() -> new IllegalStateException(
                    "Retrieval reads from Qdrant; set qdrant.enabled=true"));

            int fetchK = topK * 2;

            float[] queryEmbedding = embeddingService.embed(query).block();

            List<RetrievedChunk> fused = store.hybridSearch(new QdrantVectorStore.HybridQuery(
                            userId, sourceTypes, query, queryEmbedding, fetchK, topK, RRF_K))
                    .stream()
                    // Qdrant has no equivalent of this cutoff — dropping it silently widens recall.
                    .filter(hit -> hit.score() >= MIN_RRF_THRESHOLD)
                    .map(hit -> new RetrievedChunk(
                            hit.sourceType(),
                            hit.sourceKey(),
                            hit.content(),
                            hit.score(),
                            null,
                            null,
                            hit.metadata()))
                    .toList();

            log.debug("RAG retrieve for user {}: {} chunks after Qdrant RRF fusion", userId, fused.size());

            return fused;
        });
    }

    /**
     * {@code vectorSimilarity} and {@code keywordScore} are always null since retrieval moved to
     * Qdrant: fusion happens server-side and only the fused {@code rrfScore} comes back.
     */
    public record RetrievedChunk(
            String sourceType,
            String sourceKey,
            String content,
            double rrfScore,
            Double vectorSimilarity,
            Double keywordScore,
            String metadata
    ) {}
}
