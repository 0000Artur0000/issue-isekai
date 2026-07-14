CREATE TABLE report_participants (
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id, user_id)
);

CREATE INDEX report_participants_user_created_at_idx
    ON report_participants (user_id, created_at DESC);
