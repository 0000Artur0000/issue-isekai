CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_code_valid CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT roles_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE permissions (
    code VARCHAR(64) PRIMARY KEY,
    CONSTRAINT permissions_code_valid CHECK (code ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$')
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(64) NOT NULL REFERENCES permissions(code) ON DELETE RESTRICT,
    PRIMARY KEY (role_id, permission_code)
);

INSERT INTO roles (id, code, display_name, description, system)
VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'ADMIN', 'Администратор', 'Полный доступ', TRUE),
    ('00000000-0000-0000-0000-0000000000a2', 'OPERATOR', 'Оператор', 'Базовая роль оператора', TRUE);

INSERT INTO permissions (code)
VALUES
    ('reports.view'),
    ('reports.inventory.view'),
    ('reports.participate'),
    ('reports.status.update'),
    ('reports.priority.update'),
    ('reports.assignee.update'),
    ('reports.duplicate.update'),
    ('servers.view'),
    ('servers.create'),
    ('servers.state.update'),
    ('servers.keys.rotate'),
    ('servers.packs.view'),
    ('servers.packs.upload'),
    ('servers.packs.activate'),
    ('users.view'),
    ('users.create'),
    ('users.state.update'),
    ('users.password.reset'),
    ('users.role.assign'),
    ('roles.view'),
    ('roles.create'),
    ('roles.update'),
    ('roles.delete');

INSERT INTO role_permissions (role_id, permission_code)
SELECT '00000000-0000-0000-0000-0000000000a1', code FROM permissions;

INSERT INTO role_permissions (role_id, permission_code)
VALUES
    ('00000000-0000-0000-0000-0000000000a2', 'reports.view'),
    ('00000000-0000-0000-0000-0000000000a2', 'reports.inventory.view'),
    ('00000000-0000-0000-0000-0000000000a2', 'reports.participate'),
    ('00000000-0000-0000-0000-0000000000a2', 'servers.view');

ALTER TABLE users
    ADD COLUMN role_id UUID,
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET role_id = CASE role
    WHEN 'ADMIN' THEN '00000000-0000-0000-0000-0000000000a1'::UUID
    WHEN 'OPERATOR' THEN '00000000-0000-0000-0000-0000000000a2'::UUID
END;

ALTER TABLE users
    ALTER COLUMN role_id SET NOT NULL,
    ADD CONSTRAINT users_role_fk FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT,
    ADD CONSTRAINT users_auth_version_valid CHECK (auth_version >= 0),
    DROP CONSTRAINT users_role_valid,
    DROP COLUMN role;

CREATE INDEX users_role_id_idx ON users (role_id);

CREATE FUNCTION bump_user_auth_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash
        OR NEW.role_id IS DISTINCT FROM OLD.role_id
        OR NEW.enabled IS DISTINCT FROM OLD.enabled THEN
        NEW.auth_version = OLD.auth_version + 1;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_auth_version_trigger
BEFORE UPDATE OF password_hash, role_id, enabled ON users
FOR EACH ROW EXECUTE FUNCTION bump_user_auth_version();

CREATE FUNCTION bump_role_users_auth_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE users
    SET auth_version = auth_version + 1
    WHERE role_id = COALESCE(NEW.role_id, OLD.role_id);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER role_permissions_auth_version_trigger
AFTER INSERT OR UPDATE OR DELETE ON role_permissions
FOR EACH ROW EXECUTE FUNCTION bump_role_users_auth_version();
