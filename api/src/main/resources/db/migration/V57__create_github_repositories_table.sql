CREATE TABLE github_repositories (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_full_name      VARCHAR(255) NOT NULL,
    repo_name           VARCHAR(100) NOT NULL,
    description         TEXT,
    primary_language    VARCHAR(50),
    topics              JSONB NOT NULL DEFAULT '[]',
    languages           JSONB NOT NULL DEFAULT '{}',
    pushed_at           TIMESTAMPTZ,
    open_issues_count   INT NOT NULL DEFAULT 0,
    is_fork             BOOLEAN NOT NULL DEFAULT FALSE,
    default_branch      VARCHAR(100),
    readme_content      TEXT,
    readme_fetched_at   TIMESTAMPTZ,
    recent_commits      JSONB NOT NULL DEFAULT '[]',
    open_prs            JSONB NOT NULL DEFAULT '[]',
    assigned_issues     JSONB NOT NULL DEFAULT '[]',
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, repo_full_name)
);

CREATE INDEX idx_github_repos_user_id ON github_repositories(user_id);
CREATE INDEX idx_github_repos_pushed_at ON github_repositories(user_id, pushed_at DESC);

CREATE TRIGGER update_github_repositories_updated_at
    BEFORE UPDATE ON github_repositories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
