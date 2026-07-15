ALTER TABLE report_inventories
    DROP CONSTRAINT report_inventories_schema_valid;

ALTER TABLE report_inventories
    ADD CONSTRAINT report_inventories_schema_valid
        CHECK (schema_version BETWEEN 1 AND 2);
