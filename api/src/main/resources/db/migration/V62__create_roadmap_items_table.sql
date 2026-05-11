CREATE TABLE roadmap_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    phase        VARCHAR(50),
    deadline     DATE,
    focus_rank   INT NOT NULL DEFAULT 0,
    ai_rationale TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending', 'in_progress', 'completed')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_roadmap_items_user_id ON roadmap_items(user_id);
CREATE INDEX idx_roadmap_items_user_rank ON roadmap_items(user_id, focus_rank);

CREATE TRIGGER set_updated_at_roadmap_items
    BEFORE UPDATE ON roadmap_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
