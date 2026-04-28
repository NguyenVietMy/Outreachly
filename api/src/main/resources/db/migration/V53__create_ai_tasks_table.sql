CREATE TABLE ai_tasks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    axis        VARCHAR(30)  NOT NULL,
    section_id  VARCHAR(100),
    title       VARCHAR(300) NOT NULL,
    description TEXT,
    completed   BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    source      VARCHAR(30)  NOT NULL,
    priority    INT          NOT NULL DEFAULT 0,
    order_index INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_tasks_user_id ON ai_tasks(user_id);
CREATE INDEX idx_ai_tasks_user_axis ON ai_tasks(user_id, axis);
