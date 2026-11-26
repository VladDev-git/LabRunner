CREATE TABLE IF NOT EXISTS assignments (
    id          BIGSERIAL PRIMARY KEY,
    title       TEXT        NOT NULL,
    description TEXT        NOT NULL,
    language    TEXT        NOT NULL CHECK (language IN ('kotlin', 'python', 'cpp')),
    max_score   INT         NOT NULL CHECK (max_score > 0),
    deadline    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS submissions (
    id            UUID PRIMARY KEY,
    assignment_id BIGINT      NOT NULL REFERENCES assignments (id),
    student_name  TEXT        NOT NULL,
    repo_url      TEXT,
    source_code   TEXT,
    status        TEXT        NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'ERROR')),
    score         INT,
    summary       TEXT,
    report_key    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT submission_has_payload CHECK (repo_url IS NOT NULL OR source_code IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_submissions_student ON submissions (student_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_submissions_status ON submissions (status);
