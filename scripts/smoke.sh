#!/usr/bin/env sh
set -eu

panel_url="${PANEL_URL:-http://127.0.0.1:8080}"
admin_username="${BOOTSTRAP_ADMIN_USERNAME:-admin}"
admin_password="${BOOTSTRAP_ADMIN_PASSWORD:-change-me-now}"
app_locale="${APP_LOCALE:-ru}"
temporary="$(mktemp -d)"
cookies="$temporary/cookies"
user_cookies="$temporary/user-cookies"
trap 'rm -rf "$temporary"' EXIT

json_value() {
  printf '%s' "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"
}

echo "Smoke: login"
identity="$(curl -fsS -c "$cookies" "$panel_url/api/me")"
printf '%s' "$identity" | grep -q "\"locale\":\"$app_locale\""
csrf_header="$(json_value "$identity" csrfHeaderName)"
csrf="$(json_value "$identity" csrfToken)"
test -n "$csrf_header"
test -n "$csrf"

login_code="$(curl -sS -o /dev/null -w '%{http_code}' -b "$cookies" -c "$cookies" \
  -H "$csrf_header: $csrf" \
  --data-urlencode "username=$admin_username" \
  --data-urlencode "password=$admin_password" \
  "$panel_url/login")"
test "$login_code" = "204"

identity="$(curl -fsS -b "$cookies" -c "$cookies" "$panel_url/api/me")"
csrf_header="$(json_value "$identity" csrfHeaderName)"
csrf="$(json_value "$identity" csrfToken)"
printf '%s' "$identity" | grep -q '"authenticated":true'
printf '%s' "$identity" | grep -q '"code":"ADMIN"'

suffix="$(date +%s)"
echo "Smoke: create and assign custom role"
curl -fsS -b "$cookies" "$panel_url/api/admin/permissions" | grep -q '"reports.view"'
created_role="$(curl -fsS -b "$cookies" \
  -H "$csrf_header: $csrf" -H 'Content-Type: application/json' \
  --data "{\"displayName\":\"Smoke role $suffix\",\"description\":\"Smoke\",\"permissions\":[\"reports.view\",\"reports.status.update\"]}" \
  "$panel_url/api/admin/roles")"
role_id="$(json_value "$created_role" id)"
test -n "$role_id"
created_user="$(curl -fsS -b "$cookies" \
  -H "$csrf_header: $csrf" -H 'Content-Type: application/json' \
  --data "{\"username\":\"smoke-user-$suffix\",\"password\":\"smoke-password-1\",\"roleId\":\"$role_id\"}" \
  "$panel_url/api/admin/users")"
printf '%s' "$created_user" | grep -q "\"id\":\"$role_id\""
delete_role_code="$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE -b "$cookies" \
  -H "$csrf_header: $csrf" "$panel_url/api/admin/roles/$role_id")"
test "$delete_role_code" = "409"
user_identity="$(curl -fsS -c "$user_cookies" "$panel_url/api/me")"
user_csrf_header="$(json_value "$user_identity" csrfHeaderName)"
user_csrf="$(json_value "$user_identity" csrfToken)"
custom_login_code="$(curl -sS -o /dev/null -w '%{http_code}' \
  -b "$user_cookies" -c "$user_cookies" -H "$user_csrf_header: $user_csrf" \
  --data-urlencode "username=smoke-user-$suffix" \
  --data-urlencode "password=smoke-password-1" "$panel_url/login")"
test "$custom_login_code" = "204"
user_identity="$(curl -fsS -b "$user_cookies" -c "$user_cookies" "$panel_url/api/me")"
user_csrf_header="$(json_value "$user_identity" csrfHeaderName)"
user_csrf="$(json_value "$user_identity" csrfToken)"
printf '%s' "$user_identity" | grep -q '"reports.view"'
printf '%s' "$user_identity" | grep -q '"reports.status.update"'
test "$(curl -sS -o /dev/null -w '%{http_code}' -b "$user_cookies" \
  "$panel_url/api/reports")" = "200"
test "$(curl -sS -o /dev/null -w '%{http_code}' -b "$user_cookies" \
  "$panel_url/api/admin/servers")" = "403"

echo "Smoke: create server"
created_server="$(curl -fsS -b "$cookies" \
  -H "$csrf_header: $csrf" -H 'Content-Type: application/json' \
  --data "{\"name\":\"smoke-$suffix\"}" \
  "$panel_url/api/admin/servers")"
server_id="$(json_value "$created_server" id)"
api_key="$(json_value "$created_server" apiKey)"
test -n "$server_id"
test -n "$api_key"

echo "Smoke: disable and enable server"
test "$(curl -sS -o /dev/null -w '%{http_code}' -X POST -b "$cookies" \
  -H "$csrf_header: $csrf" "$panel_url/api/admin/servers/$server_id/disable")" = "204"
test "$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data '{"online":true,"onlinePlayers":3,"maxPlayers":20}' \
  "$panel_url/api/v1/heartbeat")" = "401"
test "$(curl -sS -o /dev/null -w '%{http_code}' -X POST -b "$cookies" \
  -H "$csrf_header: $csrf" "$panel_url/api/admin/servers/$server_id/enable")" = "204"

echo "Smoke: heartbeat online"
test "$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data '{"online":true,"onlinePlayers":3,"maxPlayers":20}' \
  "$panel_url/api/v1/heartbeat")" = "204"
servers_json="$(curl -fsS -b "$cookies" "$panel_url/api/admin/servers")"
printf '%s' "$servers_json" | grep -q "\"id\":\"$server_id\""
printf '%s' "$servers_json" | grep -q '"state":"ONLINE"'
printf '%s' "$servers_json" | grep -q '"onlinePlayers":3,"maxPlayers":20'

printf '%s' 'UEsDBAoAAAgAAFBY71wAAAAAAAAAAAAAAAAJAAQATUVUQS1JTkYv/soAAFBLAwQUAAgICABQWO9cAAAAAAAAAAAAAAAAFAAAAE1FVEEtSU5GL01BTklGRVNULk1G803My0xLLS7RDUstKs7Mz7NSMNQz4OVyLkpNLElN0XWqtFIwAoroGRoqaIQmleaVlGrycvFyAQBQSwcIPR1doTgAAAA3AAAAUEsDBAoAAAgAAExY71wAAAAAAAAAAAAAAAAHAAAAYXNzZXRzL1BLAwQKAAAIAABMWO9cAAAAAAAAAAAAAAAADwAAAGFzc2V0cy9leGFtcGxlL1BLAwQKAAAIAABMWO9cAAAAAAAAAAAAAAAAFQAAAGFzc2V0cy9leGFtcGxlL2l0ZW1zL1BLAwQUAAgICABMWO9cAAAAAAAAAAAAAAAAHgAAAGFzc2V0cy9leGFtcGxlL2l0ZW1zL3J1YnkuanNvbquu5QIAUEsHCAawod0FAAAAAwAAAFBLAwQUAAgICABLWO9cAAAAAAAAAAAAAAAACwAAAHBhY2subWNtZXRhq1YqSEzOVrKqBtPxaflFuYklSlbmpjpKxaUFBflFJakpUNFiJatooLi5aayOUkpqcXJRZkFJZn6ekpWSZ3FxaapncWp2YqZCcW5+dqpSbS0XAFBLBwjCtph+UgAAAFoAAABQSwECCgAKAAAIAABQWO9cAAAAAAAAAAAAAAAACQAEAAAAAAAAAAAAAAAAAAAATUVUQS1JTkYv/soAAFBLAQIUABQACAgIAFBY71w9HV2hOAAAADcAAAAUAAAAAAAAAAAAAAAAACsAAABNRVRBLUlORi9NQU5JRlVTVC5NRlBLAQIKAAoAAAgAAExY71wAAAAAAAAAAAAAAAAHAAAAAAAAAAAAAAAAAKUAAABhc3NldHMvUEsBAgoACgAACAAATFjvXAAAAAAAAAAAAAAAAA8AAAAAAAAAAAAAAAAAygAAAGFzc2V0cy9leGFtcGxlL1BLAQIKAAoAAAgAAExY71wAAAAAAAAAAAAAAAAVAAAAAAAAAAAAAAAAAPcAAABhc3NldHMvZXhhbXBsZS9pdGVtcy9QSwECFAAUAAgICABMWO9cBrCh3QUAAAADAAAAHgAAAAAAAAAAAAAAAAAqAQAAYXNzZXRzL2V4YW1wbGUvaXRlbXMvcnVieS5qc29uUEsBAhQAFAAICAgAS1jvXMK2mH5SAAAAWgAAAAsAAAAAAAAAAAAAAAAAewEAAHBhY2subWNtZXRhUEsFBgAAAAAHAAcAtwEAAAYCAAAAAA==' \
  | base64 -d > "$temporary/pack.zip"
echo "Smoke: upload resource pack"
revision="$(curl -fsS -b "$cookies" -H "$csrf_header: $csrf" \
  -F 'displayName=Smoke pack' -F 'minecraftVersion=26.1.2' \
  -F "file=@$temporary/pack.zip;type=application/zip" \
  "$panel_url/api/admin/servers/$server_id/resource-packs")"
revision_id="$(json_value "$revision" id)"
pack_sha1="$(json_value "$revision" sha1)"
test -n "$revision_id"
test -n "$pack_sha1"

echo "Smoke: activate resource pack"
curl -fsS -o /dev/null -X PUT -b "$cookies" -H "$csrf_header: $csrf" \
  "$panel_url/api/admin/servers/$server_id/resource-packs/$revision_id/active"

sed "s/0123456789abcdef0123456789abcdef01234567/$pack_sha1/" \
  contracts/create-report-request-with-inventory.json > "$temporary/payload.json"
echo "Smoke: ingest inventory"
first_code="$(curl -sS -o "$temporary/first.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data-binary "@$temporary/payload.json" "$panel_url/api/v1/reports")"
second_code="$(curl -sS -o "$temporary/second.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data-binary "@$temporary/payload.json" "$panel_url/api/v1/reports")"
test "$first_code" = "201"
test "$second_code" = "200"
first_id="$(json_value "$(cat "$temporary/first.json")" report_id)"
second_id="$(json_value "$(cat "$temporary/second.json")" report_id)"
test "$first_id" = "$second_id"

echo "Smoke: read report"
curl -fsS -b "$cookies" "$panel_url/api/reports?size=1" | grep -q "$first_id"
curl -fsS -b "$cookies" "$panel_url/api/reports/$first_id" | grep -q '"playerName":"Steve"'
curl -fsS -b "$cookies" "$panel_url/api/reports/$first_id/inventory" \
  | grep -q '"item_model":"example:ruby_pickaxe"'
curl -fsS -b "$cookies" "$panel_url/api/admin/servers" | grep -q '"lastReportAt":"'
echo "Smoke: read resource-pack asset"
asset="$(curl -fsS -b "$cookies" \
  "$panel_url/api/resource-packs/$revision_id/assets/example/items/ruby.json")"
test "$asset" = '{}'

echo "Smoke: enforce one permission per changed report field"
test "$(curl -sS -o /dev/null -w '%{http_code}' -X PUT -b "$user_cookies" \
  -H "$user_csrf_header: $user_csrf" -H 'Content-Type: application/json' \
  --data '{"status":"IN_PROGRESS","priority":"NORMAL","assigneeId":null,"duplicateOfId":null}' \
  "$panel_url/api/reports/$first_id")" = "204"
denied_body="$temporary/denied.json"
test "$(curl -sS -o "$denied_body" -w '%{http_code}' -X PUT -b "$user_cookies" \
  -H "$user_csrf_header: $user_csrf" -H 'Content-Type: application/json' \
  --data '{"status":"IN_PROGRESS","priority":"HIGH","assigneeId":null,"duplicateOfId":null}' \
  "$panel_url/api/reports/$first_id")" = "403"
grep -q '"code":"FORBIDDEN"' "$denied_body"
grep -q '"args":\[\]' "$denied_body"
test "$(curl -sS -o /dev/null -w '%{http_code}' -b "$user_cookies" \
  "$panel_url/api/reports/$first_id/inventory")" = "403"

echo "Smoke: heartbeat offline"
test "$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-Server-Key: $api_key" \
  --data '{"online":false,"onlinePlayers":0,"maxPlayers":20}' \
  "$panel_url/api/v1/heartbeat")" = "204"
curl -fsS -b "$cookies" "$panel_url/api/admin/servers" | grep -q '"state":"OFFLINE"'

echo "Smoke: SPA index"
curl -fsS "$panel_url/login" | grep -q 'id="root"'
for route in board timeline "reports/$first_id" users roles servers; do
  curl -fsS -b "$cookies" "$panel_url/$route" | grep -q 'id="root"'
done
unknown_api_body="$temporary/unknown-api.json"
unknown_api_code="$(curl -sS -o "$unknown_api_body" -w '%{http_code}' \
  -b "$cookies" "$panel_url/api/unknown")"
test "$unknown_api_code" = "404"
grep -q '"code":"API_NOT_FOUND"' "$unknown_api_body"
grep -q '"args":\[\]' "$unknown_api_body"
curl -fsS -o /dev/null -D - "$panel_url/board" -b "$cookies" | grep -qi 'content-security-policy'
