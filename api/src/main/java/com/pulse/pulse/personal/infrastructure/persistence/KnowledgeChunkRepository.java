package com.pulse.pulse.personal.infrastructure.persistence;

import com.pulse.pulse.personal.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    List<KnowledgeChunk> findByUserIdAndSourceType(Long userId, String sourceType);

    List<KnowledgeChunk> findByUserIdAndSourceTypeAndSourceKey(Long userId, String sourceType, String sourceKey);

    @Modifying
    @Query("DELETE FROM KnowledgeChunk k WHERE k.userId = :userId AND k.sourceType = :sourceType AND k.sourceKey = :sourceKey")
    void deleteByUserIdAndSourceTypeAndSourceKey(@Param("userId") Long userId,
                                                  @Param("sourceType") String sourceType,
                                                  @Param("sourceKey") String sourceKey);

    @Modifying
    @Query("DELETE FROM KnowledgeChunk k WHERE k.userId = :userId AND k.sourceType = :sourceType")
    void deleteByUserIdAndSourceType(@Param("userId") Long userId, @Param("sourceType") String sourceType);

    @Modifying
    @Query("DELETE FROM KnowledgeChunk k WHERE k.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT source_type, COUNT(*) FROM knowledge_chunks WHERE user_id = :userId GROUP BY source_type",
            nativeQuery = true)
    List<Object[]> countBySourceType(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
        INSERT INTO knowledge_chunks (id, user_id, source_type, source_key, chunk_index, content, embedding, metadata, embedded_at, created_at, updated_at)
        VALUES (gen_random_uuid(), :userId, :sourceType, :sourceKey, :chunkIndex, :content, :embedding\\:\\:vector, :metadata\\:\\:jsonb, now(), now(), now())
        ON CONFLICT (user_id, source_type, source_key, chunk_index)
        DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding, metadata = EXCLUDED.metadata, embedded_at = now(), updated_at = now()
        """, nativeQuery = true)
    void upsertChunk(@Param("userId") Long userId,
                     @Param("sourceType") String sourceType,
                     @Param("sourceKey") String sourceKey,
                     @Param("chunkIndex") int chunkIndex,
                     @Param("content") String content,
                     @Param("embedding") String embedding,
                     @Param("metadata") String metadata);

    @Query(value = """
        SELECT id, user_id, source_type, source_key, chunk_index, content, metadata,
               1 - (embedding <=> :queryEmbedding\\:\\:vector) AS similarity
        FROM knowledge_chunks
        WHERE user_id = :userId
          AND (:sourceTypes IS NULL OR source_type = ANY(string_to_array(:sourceTypes, ',')))
          AND embedding IS NOT NULL
        ORDER BY embedding <=> :queryEmbedding\\:\\:vector
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> findByVectorSimilarity(@Param("userId") Long userId,
                                           @Param("queryEmbedding") String queryEmbedding,
                                           @Param("sourceTypes") String sourceTypes,
                                           @Param("topK") int topK);

    @Query(value = """
        SELECT id, user_id, source_type, source_key, chunk_index, content, metadata,
               ts_rank_cd(search_vector, plainto_tsquery('english', :query)) AS rank
        FROM knowledge_chunks
        WHERE user_id = :userId
          AND (:sourceTypes IS NULL OR source_type = ANY(string_to_array(:sourceTypes, ',')))
          AND search_vector @@ plainto_tsquery('english', :query)
        ORDER BY rank DESC
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> findByKeywordSearch(@Param("userId") Long userId,
                                       @Param("query") String query,
                                       @Param("sourceTypes") String sourceTypes,
                                       @Param("topK") int topK);

    @Query(value = """
        SELECT source_type, COUNT(*) as cnt
        FROM knowledge_chunks
        WHERE user_id = :userId
        GROUP BY source_type
        """, nativeQuery = true)
    List<Object[]> countGroupedBySourceType(@Param("userId") Long userId);
}
