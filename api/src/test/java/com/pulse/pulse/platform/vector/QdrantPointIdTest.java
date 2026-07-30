package com.pulse.pulse.platform.vector;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Point ids are the only thing keeping Qdrant upserts idempotent, so the mapping is pinned here:
 * a change to the namespace or the name format silently orphans every point in the collection.
 */
class QdrantPointIdTest {

    @Test
    void isStableForTheSameChunk() {
        assertEquals(UUID.fromString("08f07fbb-df25-527e-b24b-d0bd51c52757"),
                QdrantVectorStore.pointId(7L, "resume_section", "experience", 2));
    }

    @Test
    void differsAcrossUsersSourcesAndChunkIndexes() {
        UUID base = QdrantVectorStore.pointId(7L, "resume_section", "experience", 2);

        assertNotEquals(base, QdrantVectorStore.pointId(8L, "resume_section", "experience", 2));
        assertNotEquals(base, QdrantVectorStore.pointId(7L, "goal", "experience", 2));
        assertNotEquals(base, QdrantVectorStore.pointId(7L, "resume_section", "skills", 2));
        assertNotEquals(base, QdrantVectorStore.pointId(7L, "resume_section", "experience", 3));
    }

    @Test
    void isAVersion5Uuid() {
        assertEquals(5, QdrantVectorStore.pointId(1L, "goal", "a", 0).version());
    }
}
