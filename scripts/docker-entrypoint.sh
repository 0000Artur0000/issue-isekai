#!/bin/sh
set -eu

directory="${RESOURCE_PACK_DIR:-/data/resource-packs}"
expected_uid="${RESOURCE_PACK_EXPECTED_UID:-10001}"
minimum_kb="$(( ${RESOURCE_PACK_MIN_FREE_MB:-256} * 1024 ))"

test -d "$directory"
test -w "$directory"
test "$(stat -c '%u' "$directory")" = "$expected_uid"
available_kb="$(df -Pk "$directory" | awk 'NR == 2 {print $4}')"
if [ "$available_kb" -lt "$minimum_kb" ]; then
  echo "Resource-pack volume has less than ${RESOURCE_PACK_MIN_FREE_MB:-256} MiB free" >&2
  exit 1
fi

exec java -jar /app/issue-isekai-panel.jar
