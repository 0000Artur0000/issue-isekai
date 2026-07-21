#!/usr/bin/env bash
# Скачивает официальный Minecraft client JAR и извлекает whitelist UI-текстур
# в panel/frontend/public/assets/mc. Текстуры Mojang — см. NOTICE.md рядом с ассетами.
#
#   MC_VERSION=26.2 ./scripts/fetch-mc-ui-assets.sh
#
# MC_VERSION=release (по умолчанию) берёт последний релиз из официального manifest.
set -euo pipefail

mc_version="${MC_VERSION:-release}"
manifest_url="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
cache_dir="${MC_ASSET_CACHE:-${TMPDIR:-/tmp}/mc-assets-cache}"
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="$repo_root/panel/frontend/public/assets/mc"

mkdir -p "$cache_dir"
manifest="$cache_dir/version_manifest_v2.json"
[ -f "$manifest" ] || curl -fsSL "$manifest_url" -o "$manifest"

if [ "$mc_version" = "release" ]; then
  mc_version="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["latest"]["release"])' "$manifest")"
fi
echo "Minecraft version: $mc_version"

version_url="$(python3 -c '
import json, sys
manifest = json.load(open(sys.argv[1]))
match = [v["url"] for v in manifest["versions"] if v["id"] == sys.argv[2]]
if not match:
    raise SystemExit(f"unknown version: {sys.argv[2]}")
print(match[0])' "$manifest" "$mc_version")"

version_json="$cache_dir/$mc_version.json"
[ -f "$version_json" ] || curl -fsSL "$version_url" -o "$version_json"

client_url="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["downloads"]["client"]["url"])' "$version_json")"
client_sha1="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["downloads"]["client"]["sha1"])' "$version_json")"
client_jar="$cache_dir/client-$mc_version.jar"

if [ ! -f "$client_jar" ]; then
  echo "downloading client JAR..."
  curl -fsSL "$client_url" -o "$client_jar"
fi
echo "$client_sha1  $client_jar" | sha1sum -c -

python3 "$repo_root/scripts/mc_assets.py" "$client_jar" "$out_dir"
echo "done: $out_dir"
