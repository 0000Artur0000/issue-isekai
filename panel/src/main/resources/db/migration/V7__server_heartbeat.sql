ALTER TABLE servers RENAME COLUMN last_seen_at TO last_report_at;

ALTER TABLE servers
    ADD COLUMN last_heartbeat_at TIMESTAMPTZ,
    ADD COLUMN heartbeat_online BOOLEAN,
    ADD COLUMN online_players INTEGER,
    ADD COLUMN max_players INTEGER,
    ADD CONSTRAINT servers_online_players_valid
        CHECK (online_players IS NULL OR online_players >= 0),
    ADD CONSTRAINT servers_max_players_valid
        CHECK (max_players IS NULL OR max_players >= 0),
    ADD CONSTRAINT servers_player_count_valid
        CHECK (online_players IS NULL OR max_players IS NULL OR online_players <= max_players);
