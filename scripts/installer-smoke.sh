#!/usr/bin/env bash
set -Eeuo pipefail

if (($# != 1)); then
  echo "Usage: $0 LOCAL_RELEASE_BUNDLE" >&2
  exit 2
fi

source_bundle="$(realpath "$1")"
real_docker="$(command -v docker)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fake_bin="$work/bin"
target="$work/install"
log="$work/docker.log"
mkdir "$fake_bin"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
if [[ "$*" == *' pg_dump '* ]]; then
  printf '%s\n' '-- local fixture database dump'
fi
EOF

cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' '{"status":"UP"}'
EOF
chmod +x "$fake_bin/docker" "$fake_bin/curl"

version="$(find "$source_bundle" -maxdepth 1 -name 'issue-isekai-panel-*.jar' -printf '%f\n')"
version="${version#issue-isekai-panel-}"
version="${version%.jar}"

run_installer() {
  env PATH="$fake_bin:$PATH" \
    FAKE_DOCKER_LOG="$log" \
    ISSUE_ISEKAI_ADMIN_PASSWORD="Admin \$#' password" \
    ISSUE_ISEKAI_TELEGRAM_ENABLED=true \
    ISSUE_ISEKAI_TELEGRAM_TOKEN="token:\$value#test" \
    ISSUE_ISEKAI_TELEGRAM_CHAT_ID=-100123 \
    "$1/install.sh" --yes --bundle-dir "$1" --install-dir "$target"
}

echo "Installer smoke: fresh install"
run_installer "$source_bundle"
[[ "$(stat -c '%a' "$target/.env")" == 600 ]]
grep -Fq "BOOTSTRAP_ADMIN_PASSWORD='Admin \$#\' password'" "$target/.env"
grep -Fqx "PANEL_BIND_ADDRESS='127.0.0.1'" "$target/.env"
[[ "$(cat "$target/.installed-version")" == "$version" ]]
"$real_docker" compose --env-file "$target/.env" -f "$target/compose.yaml" config --quiet
env_checksum="$(sha256sum "$target/.env")"

echo "Installer smoke: idempotent rerun"
run_installer "$source_bundle"
[[ "$(sha256sum "$target/.env")" == "$env_checksum" ]]
if grep -q pg_dump "$log"; then
  echo "Same-version rerun unexpectedly created a backup" >&2
  exit 1
fi

echo "Installer smoke: update with backup"
update_version="${version%.*}.$((${version##*.} + 1))"
update_bundle="$work/update"
mkdir "$update_bundle"
cp "$source_bundle/.env.example" "$source_bundle/INSTALL.md" "$source_bundle/compose.yaml" \
  "$source_bundle/install.sh" "$update_bundle/"
cp "$source_bundle/issue-isekai-panel-$version.jar" \
  "$update_bundle/issue-isekai-panel-$update_version.jar"
cp "$source_bundle/issue-isekai-plugin-$version.jar" \
  "$update_bundle/issue-isekai-plugin-$update_version.jar"
(
  cd "$update_bundle"
  sha256sum .env.example INSTALL.md compose.yaml install.sh \
    "issue-isekai-panel-$update_version.jar" "issue-isekai-plugin-$update_version.jar" > SHA256SUMS
)
run_installer "$update_bundle"
[[ "$(cat "$target/.installed-version")" == "$update_version" ]]
[[ "$(sha256sum "$target/.env")" == "$env_checksum" ]]
grep -q pg_dump "$log"
backup_files=("$target"/backups/postgres-*.sql)
((${#backup_files[@]} == 1))
grep -Fq -- '-- local fixture database dump' "${backup_files[0]}"

echo "Installer smoke: checksum failure"
corrupt_bundle="$work/corrupt"
cp -a "$update_bundle" "$corrupt_bundle"
printf '\n# corrupt\n' >> "$corrupt_bundle/compose.yaml"
if run_installer "$corrupt_bundle" >/dev/null 2>&1; then
  echo "Corrupt bundle was accepted" >&2
  exit 1
fi
[[ "$(cat "$target/.installed-version")" == "$update_version" ]]

echo "Installer smoke passed"
