#!/usr/bin/env sh
set -eu

panel_url="${PANEL_URL:-http://127.0.0.1:8080}"
admin_username="${BOOTSTRAP_ADMIN_USERNAME:-admin}"
admin_password="${BOOTSTRAP_ADMIN_PASSWORD:-change-me-now}"
temporary="$(mktemp -d)"
cookies="$temporary/cookies"
trap 'rm -rf "$temporary"' EXIT

login_page="$(curl -fsS -c "$cookies" "$panel_url/login")"
csrf="$(printf '%s' "$login_page" | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p')"
curl -fsS -o /dev/null -b "$cookies" -c "$cookies" \
  --data-urlencode "username=$admin_username" \
  --data-urlencode "password=$admin_password" \
  --data-urlencode "_csrf=$csrf" \
  "$panel_url/login"

servers_page="$(curl -fsS -b "$cookies" "$panel_url/servers")"
csrf="$(printf '%s' "$servers_page" | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p')"
created_server="$(curl -fsS -L -b "$cookies" -c "$cookies" \
  --data-urlencode "name=smoke-$(date +%s)" \
  --data-urlencode "_csrf=$csrf" \
  "$panel_url/servers")"
api_key="$(printf '%s' "$created_server" | sed -n 's/.*<code>\([^<]*\)<\/code>.*/\1/p')"
test -n "$api_key"

payload='{"submission_id":"11111111-1111-1111-1111-111111111111","category":"gameplay","description":"IssueIsekai Docker smoke report.","player_uuid":"22222222-2222-2222-2222-222222222222","player_name":"SmokePlayer","world_key":"minecraft:overworld","x":10,"y":64,"z":-20,"game_mode":"SURVIVAL","reported_at":"2026-07-14T10:00:00Z","paper_version":"26.1.2"}'
first_code="$(curl -sS -o "$temporary/first.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data "$payload" "$panel_url/api/v1/reports")"
second_code="$(curl -sS -o "$temporary/second.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data "$payload" "$panel_url/api/v1/reports")"

test "$first_code" = "201"
test "$second_code" = "200"
test "$(sed -n 's/.*"report_id":"\([^"]*\)".*/\1/p' "$temporary/first.json")" = \
  "$(sed -n 's/.*"report_id":"\([^"]*\)".*/\1/p' "$temporary/second.json")"
