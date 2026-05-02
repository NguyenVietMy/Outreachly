CREATE TABLE daily_suggestions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    suggestion_date DATE NOT NULL,
    tasks           JSONB NOT NULL DEFAULT '[]',
    context_hash    VARCHAR(64),
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, suggestion_date)
);

CREATE INDEX idx_daily_suggestions_user_date ON daily_suggestions(user_id, suggestion_date DESC);
