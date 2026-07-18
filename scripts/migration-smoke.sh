#!/usr/bin/env sh
set -eu

project="issue-isekai-migration-test"
v1_container="$project-v1"
export PANEL_PORT=0
export POSTGRES_DB=issue_isekai
export POSTGRES_USER=issue_isekai
export POSTGRES_PASSWORD=issue_isekai
export BOOTSTRAP_ADMIN_USERNAME=admin
export BOOTSTRAP_ADMIN_PASSWORD=migration-smoke-password

compose() {
  docker compose -p "$project" "$@"
}

cleanup() {
  docker rm -f "$v1_container" >/dev/null 2>&1 || true
  compose down -v --remove-orphans >/dev/null 2>&1 || true
}

wait_for_panel() {
  container="$1"
  attempts=0
  until docker exec "$container" wget -q -O - \
      http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null | grep -q '"status":"UP"'; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      docker logs "$container"
      return 1
    fi
    sleep 2
  done
}

trap cleanup EXIT
cleanup
compose build panel
compose up -d postgres

attempts=0
until compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; do
  attempts=$((attempts + 1))
  test "$attempts" -lt 30
  sleep 1
done

compose run -d --name "$v1_container" --no-deps \
  -e SPRING_FLYWAY_TARGET=1 \
  -e SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration \
  panel >/dev/null
wait_for_panel "$v1_container"

compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
INSERT INTO users (id, username, password_hash, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'migration-user', 'hash', 'OPERATOR');

INSERT INTO servers (id, name, api_key_hash)
VALUES
    ('00000000-0000-0000-0000-000000000002', 'migration-server-1', decode(repeat('01', 32), 'hex')),
    ('00000000-0000-0000-0000-000000000003', 'migration-server-2', decode(repeat('02', 32), 'hex'));

UPDATE servers SET last_seen_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000002';

INSERT INTO reports (
    id, server_id, submission_id, category, description, player_uuid, player_name,
    world_key, x, y, z, game_mode, reported_at, paper_version
) VALUES
    (
        '00000000-0000-0000-0000-000000000004',
        '00000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000004',
        'gameplay', 'Existing report before V2 migration.',
        '20000000-0000-0000-0000-000000000004', 'Steve', 'minecraft:overworld',
        1, 64, 2, 'SURVIVAL', CURRENT_TIMESTAMP, '26.1.2'
    ),
    (
        '00000000-0000-0000-0000-000000000005',
        '00000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000005',
        'gameplay', 'Second existing report before migration.',
        '20000000-0000-0000-0000-000000000005', 'Alex', 'minecraft:overworld',
        3, 64, 4, 'SURVIVAL', CURRENT_TIMESTAMP, '26.1.2'
    );
SQL

docker rm -f "$v1_container" >/dev/null
compose up -d panel
wait_for_panel "$(compose ps -q panel)"

compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
DO $$
DECLARE
    report_one UUID := '00000000-0000-0000-0000-000000000004';
    report_two UUID := '00000000-0000-0000-0000-000000000005';
    server_one UUID := '00000000-0000-0000-0000-000000000002';
    server_two UUID := '00000000-0000-0000-0000-000000000003';
    user_one UUID := '00000000-0000-0000-0000-000000000001';
    pack_one UUID := '00000000-0000-0000-0000-000000000006';
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE success) <> 7 THEN
        RAISE EXCEPTION 'expected Flyway versions V1 through V7';
    END IF;
    IF (SELECT count(*) FROM roles) <> 2 THEN
        RAISE EXCEPTION 'expected ADMIN and OPERATOR roles';
    END IF;
    IF (SELECT count(*) FROM permissions) <> 23 THEN
        RAISE EXCEPTION 'expected permission catalog';
    END IF;
    IF (SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
        WHERE r.code = 'ADMIN') <> 23 THEN
        RAISE EXCEPTION 'ADMIN permissions were not seeded';
    END IF;
    IF (SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
        WHERE r.code = 'OPERATOR') <> 4 THEN
        RAISE EXCEPTION 'OPERATOR permissions were not seeded';
    END IF;
    IF (SELECT r.code FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = user_one)
        <> 'OPERATOR' THEN
        RAISE EXCEPTION 'existing user role was not migrated';
    END IF;
    IF (SELECT auth_version FROM users WHERE id = user_one) <> 0 THEN
        RAISE EXCEPTION 'existing user auth_version was not initialized';
    END IF;
    IF (SELECT last_report_at FROM servers WHERE id = server_one) IS NULL THEN
        RAISE EXCEPTION 'last_seen_at was not migrated to last_report_at';
    END IF;
    UPDATE servers
    SET last_heartbeat_at = CURRENT_TIMESTAMP, heartbeat_online = TRUE,
        online_players = 3, max_players = 20
    WHERE id = server_one;
    BEGIN
        UPDATE servers SET online_players = 21 WHERE id = server_one;
        RAISE EXCEPTION 'invalid heartbeat player count was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
    UPDATE users SET password_hash = 'changed-hash' WHERE id = user_one;
    IF (SELECT auth_version FROM users WHERE id = user_one) <> 1 THEN
        RAISE EXCEPTION 'user security change did not increment auth_version';
    END IF;
    INSERT INTO role_permissions (role_id, permission_code)
    SELECT id, 'servers.create' FROM roles WHERE code = 'OPERATOR';
    IF (SELECT auth_version FROM users WHERE id = user_one) <> 2 THEN
        RAISE EXCEPTION 'role permission grant did not increment auth_version';
    END IF;
    DELETE FROM role_permissions
    WHERE role_id = (SELECT id FROM roles WHERE code = 'OPERATOR')
        AND permission_code = 'servers.create';
    IF (SELECT auth_version FROM users WHERE id = user_one) <> 3 THEN
        RAISE EXCEPTION 'role permission revoke did not increment auth_version';
    END IF;
    IF EXISTS (
        SELECT FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'role'
    ) THEN
        RAISE EXCEPTION 'legacy users.role column still exists';
    END IF;
    BEGIN
        DELETE FROM roles WHERE code = 'OPERATOR';
        RAISE EXCEPTION 'role used by a user was deleted';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    INSERT INTO report_participants (report_id, user_id) VALUES (report_one, user_one);
    BEGIN
        INSERT INTO report_participants (report_id, user_id) VALUES (report_one, user_one);
        RAISE EXCEPTION 'duplicate participant was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    INSERT INTO resource_packs (
        id, kind, display_name, minecraft_version, pack_format_min, pack_format_max,
        content_sha256, size_bytes
    ) VALUES (
        '00000000-0000-0000-0000-000000000007', 'VANILLA_BASE', 'Minecraft 26.1.2',
        '26.1.2', 75, 84, decode(repeat('07', 32), 'hex'), 1024
    );

    INSERT INTO resource_packs (
        id, kind, server_id, display_name, minecraft_version, pack_format_min, pack_format_max,
        sha1, content_sha256, size_bytes
    ) VALUES (
        pack_one, 'SERVER', server_one, 'Server pack', '26.1.2', 75, 84,
        decode(repeat('06', 20), 'hex'), decode(repeat('06', 32), 'hex'), 2048
    );

    UPDATE servers SET active_resource_pack_id = pack_one WHERE id = server_one;
    BEGIN
        UPDATE servers SET active_resource_pack_id = pack_one WHERE id = server_two;
        RAISE EXCEPTION 'cross-server active resource pack was accepted';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    UPDATE reports
    SET resource_pack_id = pack_one, resource_pack_match = 'EXACT'
    WHERE id = report_one;
    BEGIN
        UPDATE reports
        SET resource_pack_id = pack_one, resource_pack_match = 'EXACT'
        WHERE id = report_two;
        RAISE EXCEPTION 'cross-server report resource pack was accepted';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    INSERT INTO report_inventories (
        report_id, schema_version, minecraft_version, selected_hotbar_slot, normalized, raw_items
    ) VALUES (
        report_one, 1, '26.1.2', 2, '{"slots":[]}'::jsonb, decode('010203', 'hex')
    );

    UPDATE report_inventories SET schema_version = 2 WHERE report_id = report_one;
    BEGIN
        UPDATE report_inventories SET schema_version = 3 WHERE report_id = report_one;
        RAISE EXCEPTION 'inventory schema version 3 was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    DELETE FROM reports WHERE id = report_one;
    IF EXISTS (SELECT FROM report_participants WHERE report_id = report_one)
        OR EXISTS (SELECT FROM report_inventories WHERE report_id = report_one) THEN
        RAISE EXCEPTION 'report children did not cascade';
    END IF;
    IF NOT EXISTS (SELECT FROM reports WHERE id = report_two) THEN
        RAISE EXCEPTION 'existing V1 report was lost';
    END IF;
END $$;
SQL
