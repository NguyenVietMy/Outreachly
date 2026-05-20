CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_chunks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type   VARCHAR(30) NOT NULL,
    source_key    VARCHAR(255) NOT NULL,
    chunk_index   INT NOT NULL DEFAULT 0,
    content       TEXT NOT NULL,
    embedding     vector(1536),
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    metadata      JSONB NOT NULL DEFAULT '{}',
    embedded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, source_type, source_key, chunk_index)
);

CREATE INDEX idx_chunks_user_source ON knowledge_chunks(user_id, source_type);
CREATE INDEX idx_chunks_search_vector ON knowledge_chunks USING GIN(search_vector);
