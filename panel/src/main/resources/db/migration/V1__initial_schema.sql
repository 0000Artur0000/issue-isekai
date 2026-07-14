CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT users_password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT users_role_valid CHECK (role IN ('ADMIN', 'OPERATOR'))
);

CREATE TABLE servers (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    api_key_hash BYTEA NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ,
    CONSTRAINT servers_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT servers_api_key_hash_sha256 CHECK (octet_length(api_key_hash) = 32)
);

CREATE TABLE reports (
    id UUID PRIMARY KEY,
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE RESTRICT,
    submission_id UUID NOT NULL,
    category VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    player_uuid UUID NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    world_key VARCHAR(255) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    game_mode VARCHAR(32) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    paper_version VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    duplicate_of_id UUID REFERENCES reports(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    telegram_notified_at TIMESTAMPTZ,
    CONSTRAINT reports_server_submission_unique UNIQUE (server_id, submission_id),
    CONSTRAINT reports_category_valid CHECK (category ~ '^[a-z0-9][a-z0-9_-]{0,63}$'),
    CONSTRAINT reports_description_length CHECK (char_length(description) BETWEEN 10 AND 1000),
    CONSTRAINT reports_player_name_not_blank CHECK (btrim(player_name) <> ''),
    CONSTRAINT reports_world_key_not_blank CHECK (btrim(world_key) <> ''),
    CONSTRAINT reports_game_mode_not_blank CHECK (btrim(game_mode) <> ''),
    CONSTRAINT reports_paper_version_not_blank CHECK (btrim(paper_version) <> ''),
    CONSTRAINT reports_status_valid CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT reports_priority_valid CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT reports_duplicate_not_self CHECK (duplicate_of_id IS NULL OR duplicate_of_id <> id),
    CONSTRAINT reports_duplicate_matches_status CHECK ((status = 'DUPLICATE') = (duplicate_of_id IS NOT NULL))
);

CREATE TABLE report_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT report_events_type_not_blank CHECK (btrim(event_type) <> '')
);

CREATE INDEX reports_server_created_at_idx ON reports (server_id, created_at DESC);
CREATE INDEX reports_status_created_at_idx ON reports (status, created_at DESC);
CREATE INDEX reports_priority_created_at_idx ON reports (priority, created_at DESC);
CREATE INDEX reports_category_created_at_idx ON reports (category, created_at DESC);
CREATE INDEX reports_assignee_created_at_idx ON reports (assignee_id, created_at DESC) WHERE assignee_id IS NOT NULL;
CREATE INDEX reports_created_at_idx ON reports (created_at DESC);
CREATE INDEX report_events_report_created_at_idx ON report_events (report_id, created_at);
