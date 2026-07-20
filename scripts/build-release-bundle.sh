#!/usr/bin/env sh
set -eu

if [ "$#" -ne 4 ]; then
  echo "Usage: $0 VERSION IMAGE SHA256_DIGEST OUTPUT_DIR" >&2
  exit 2
fi

version="$1"
image="$2"
digest="$3"
output="$4"

printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || {
  echo "VERSION must be X.Y.Z" >&2
  exit 2
}
printf '%s\n' "$image" | grep -Eq '^ghcr\.io/[a-z0-9._/-]+$' || {
  echo "IMAGE must be a lowercase ghcr.io name" >&2
  exit 2
}
printf '%s\n' "$digest" | grep -Eq '^sha256:[0-9a-f]{64}$' || {
  echo "SHA256_DIGEST is invalid" >&2
  exit 2
}
[ ! -e "$output" ] || {
  echo "OUTPUT_DIR already exists: $output" >&2
  exit 2
}

panel_jar="panel/build/libs/panel-$version.jar"
plugin_jar="paper-plugin/build/libs/paper-plugin-$version.jar"
test -f "$panel_jar"
test -f "$plugin_jar"

mkdir "$output"
cp .env.example INSTALL.md install.sh "$output/"
cp "$panel_jar" "$output/issue-isekai-panel-$version.jar"
cp "$plugin_jar" "$output/issue-isekai-plugin-$version.jar"
sed "s|^    build: \.$|    image: $image@$digest|" compose.yaml > "$output/compose.yaml"
grep -Fq "    image: $image@$digest" "$output/compose.yaml"
! grep -Eq '^    build:' "$output/compose.yaml"
chmod 755 "$output/install.sh"

(
  cd "$output"
  sha256sum .env.example INSTALL.md compose.yaml install.sh \
    "issue-isekai-panel-$version.jar" "issue-isekai-plugin-$version.jar" > SHA256SUMS
  sha256sum -c SHA256SUMS
)
