package com.pulse.pulse.personal.application;

import com.pulse.pulse.personal.infrastructure.persistence.KnowledgeChunkRepository;
import com.pulse.pulse.platform.ai.EmbeddingService;
import com.pulse.pulse.platform.observability.PulseObservability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRetrievalService {

    private static final int RRF_K = 60;
    private static final double MIN_RRF_THRESHOLD = 0.008;

    private final KnowledgeChunkRepository chunkRepo;
    private final EmbeddingService embeddingService;
    private final PulseObservability observability;

    public List<RetrievedChunk> retrieve(Long userId, String query, int topK, List<String> sourceTypes) {
        return observability.observe("pulse.rag.retrieve", obs -> {
            observability.high(obs, "user.id", userId);
            observability.high(obs, "query.length", query.length());
            observability.low(obs, "top_k", String.valueOf(topK));
        }, () -> {
            String sourceTypesParam = sourceTypes != null && !sourceTypes.isEmpty()
                    ? String.join(",", sourceTypes) : null;

            int fetchK = topK * 2;

            float[] queryEmbedding = embeddingService.embed(query).block();
            String embeddingStr = EmbeddingService.toVectorString(queryEmbedding);

            List<Object[]> vectorResults = chunkRepo.findByVectorSimilarity(
                    userId, embeddingStr, sourceTypesParam, fetchK);

            List<Object[]> keywordResults = chunkRepo.findByKeywordSearch(
                    userId, query, sourceTypesParam, fetchK);

            List<RetrievedChunk> fused = reciprocalRankFusion(vectorResults, keywordResults, topK);

            log.debug("RAG retrieve for user {}: {} vector hits, {} keyword hits, {} after fusion",
                    userId, vectorResults.size(), keywordResults.size(), fused.size());

            return fused;
        });
    }

    private List<RetrievedChunk> reciprocalRankFusion(List<Object[]> vectorResults,
                                                       List<Object[]> keywordResults,
                                                       int topK) {
        Map<UUID, RankedDoc> docMap = new LinkedHashMap<>();

        for (int rank = 0; rank < vectorResults.size(); rank++) {
            Object[] row = vectorResults.get(rank);
            UUID id = (UUID) row[0];
            double similarity = ((Number) row[7]).doubleValue();
            RankedDoc doc = docMap.computeIfAbsent(id, k -> RankedDoc.from(row));
            doc.vectorRank = rank + 1;
            doc.vectorSimilarity = similarity;
        }

        for (int rank = 0; rank < keywordResults.size(); rank++) {
            Object[] row = keywordResults.get(rank);
            UUID id = (UUID) row[0];
            double keywordRank = ((Number) row[7]).doubleValue();
            RankedDoc doc = docMap.computeIfAbsent(id, k -> RankedDoc.from(row));
            doc.keywordRank = rank + 1;
            doc.keywordScore = keywordRank;
        }

        return docMap.values().stream()
                .peek(doc -> {
                    doc.rrfScore = 0;
                    if (doc.vectorRank != null) {
                        doc.rrfScore += 1.0 / (RRF_K + doc.vectorRank);
                    }
                    if (doc.keywordRank != null) {
                        doc.rrfScore += 1.0 / (RRF_K + doc.keywordRank);
                    }
                })
                .filter(doc -> doc.rrfScore >= MIN_RRF_THRESHOLD)
                .sorted(Comparator.comparingDouble((RankedDoc d) -> d.rrfScore).reversed())
                .limit(topK)
                .map(doc -> new RetrievedChunk(
                        doc.sourceType,
                        doc.sourceKey,
                        doc.content,
                        doc.rrfScore,
                        doc.vectorSimilarity,
                        doc.keywordScore,
                        doc.metadata
                ))
                .collect(Collectors.toList());
    }

    private static class RankedDoc {
        UUID id;
        String sourceType;
        String sourceKey;
        String content;
        String metadata;
        Integer vectorRank;
        Double vectorSimilarity;
        Integer keywordRank;
        Double keywordScore;
        double rrfScore;

        static RankedDoc from(Object[] row) {
            RankedDoc doc = new RankedDoc();
            doc.id = (UUID) row[0];
            doc.sourceType = (String) row[2];
            doc.sourceKey = (String) row[3];
            doc.content = (String) row[5];
            doc.metadata = row[6] != null ? row[6].toString() : "{}";
            return doc;
        }
    }

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
