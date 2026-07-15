ALTER TABLE resource_packs
    DROP CONSTRAINT resource_packs_owner_valid,
    DROP COLUMN resource_pack_id;

ALTER TABLE resource_packs
    ADD CONSTRAINT resource_packs_owner_valid CHECK (
        (kind = 'SERVER' AND server_id IS NOT NULL AND sha1 IS NOT NULL)
        OR
        (kind = 'VANILLA_BASE' AND server_id IS NULL AND sha1 IS NULL)
    );
