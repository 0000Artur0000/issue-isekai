CREATE TABLE resource_packs (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    server_id UUID REFERENCES servers(id) ON DELETE RESTRICT,
    display_name VARCHAR(100) NOT NULL,
    minecraft_version VARCHAR(64) NOT NULL,
    pack_format_min INTEGER NOT NULL,
    pack_format_max INTEGER NOT NULL,
    resource_pack_id UUID,
    sha1 BYTEA,
    content_sha256 BYTEA NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT resource_packs_server_id_unique UNIQUE (server_id, id),
    CONSTRAINT resource_packs_kind_valid CHECK (kind IN ('VANILLA_BASE', 'SERVER')),
    CONSTRAINT resource_packs_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT resource_packs_minecraft_version_not_blank CHECK (btrim(minecraft_version) <> ''),
    CONSTRAINT resource_packs_format_valid CHECK (
        pack_format_min > 0 AND pack_format_max >= pack_format_min
    ),
    CONSTRAINT resource_packs_sha1_valid CHECK (sha1 IS NULL OR octet_length(sha1) = 20),
    CONSTRAINT resource_packs_sha256_valid CHECK (octet_length(content_sha256) = 32),
    CONSTRAINT resource_packs_size_valid CHECK (size_bytes BETWEEN 1 AND 104857600),
    CONSTRAINT resource_packs_owner_valid CHECK (
        (kind = 'SERVER' AND server_id IS NOT NULL AND resource_pack_id IS NOT NULL AND sha1 IS NOT NULL)
        OR
        (kind = 'VANILLA_BASE' AND server_id IS NULL AND resource_pack_id IS NULL AND sha1 IS NULL)
    )
);

CREATE UNIQUE INDEX resource_packs_server_content_unique
    ON resource_packs (server_id, content_sha256)
    WHERE kind = 'SERVER';

CREATE UNIQUE INDEX resource_packs_vanilla_version_unique
    ON resource_packs (minecraft_version)
    WHERE kind = 'VANILLA_BASE';

CREATE INDEX resource_packs_server_created_at_idx
    ON resource_packs (server_id, created_at DESC)
    WHERE server_id IS NOT NULL;

ALTER TABLE servers
    ADD COLUMN active_resource_pack_id UUID,
    ADD CONSTRAINT servers_active_resource_pack_fk
        FOREIGN KEY (id, active_resource_pack_id)
        REFERENCES resource_packs (server_id, id)
        ON DELETE RESTRICT;

ALTER TABLE reports
    ADD COLUMN resource_pack_id UUID,
    ADD COLUMN resource_pack_match VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD CONSTRAINT reports_resource_pack_fk
        FOREIGN KEY (server_id, resource_pack_id)
        REFERENCES resource_packs (server_id, id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT reports_resource_pack_match_valid CHECK (
        (resource_pack_id IS NULL AND resource_pack_match IN ('NONE', 'MISMATCH'))
        OR
        (resource_pack_id IS NOT NULL AND resource_pack_match IN ('EXACT', 'ASSUMED'))
    );

CREATE INDEX reports_resource_pack_idx
    ON reports (resource_pack_id)
    WHERE resource_pack_id IS NOT NULL;

CREATE TABLE report_inventories (
    report_id UUID PRIMARY KEY REFERENCES reports(id) ON DELETE CASCADE,
    schema_version SMALLINT NOT NULL,
    minecraft_version VARCHAR(64) NOT NULL,
    selected_hotbar_slot SMALLINT NOT NULL,
    normalized JSONB NOT NULL,
    raw_items BYTEA,
    capture_error VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT report_inventories_schema_valid CHECK (schema_version = 1),
    CONSTRAINT report_inventories_minecraft_version_not_blank CHECK (btrim(minecraft_version) <> ''),
    CONSTRAINT report_inventories_hotbar_slot_valid CHECK (selected_hotbar_slot BETWEEN 0 AND 8),
    CONSTRAINT report_inventories_normalized_object CHECK (jsonb_typeof(normalized) = 'object'),
    CONSTRAINT report_inventories_raw_size_valid CHECK (
        raw_items IS NULL OR octet_length(raw_items) <= 4194304
    ),
    CONSTRAINT report_inventories_capture_result CHECK (
        raw_items IS NOT NULL OR capture_error IS NOT NULL
    ),
    CONSTRAINT report_inventories_capture_error_valid CHECK (
        capture_error IS NULL OR capture_error ~ '^[A-Z0-9_]{1,64}$'
    )
);
