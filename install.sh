#!/usr/bin/env bash
set -Eeuo pipefail

readonly REPOSITORY="0000Artur0000/issue-isekai"
requested_version="latest"
install_dir="${ISSUE_ISEKAI_INSTALL_DIR:-/opt/issue-isekai}"
project_name="${ISSUE_ISEKAI_PROJECT_NAME:-issue-isekai}"
bundle_dir=""
bundle_version=""
assume_yes=false
temporary_dir=""
docker_command=(docker)

cleanup() {
  if [[ -n "$temporary_dir" ]]; then
    rm -rf "$temporary_dir"
  fi
}
trap cleanup EXIT

die() {
  echo "Error: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: ./install.sh [options]
  --version X.Y.Z     Install a selected release (default: latest)
  --install-dir PATH  Installation directory (default: /opt/issue-isekai)
  --bundle-dir PATH   Use a local release bundle instead of GitHub
  --yes               Non-interactive mode; reads ISSUE_ISEKAI_* variables
  --help              Show this help
EOF
}

while (($#)); do
  case "$1" in
    --version)
      (($# >= 2)) || die "--version requires a value"
      requested_version="${2#v}"
      shift 2
      ;;
    --install-dir)
      (($# >= 2)) || die "--install-dir requires a value"
      install_dir="$2"
      shift 2
      ;;
    --bundle-dir)
      (($# >= 2)) || die "--bundle-dir requires a value"
      bundle_dir="$2"
      shift 2
      ;;
    --yes)
      assume_yes=true
      shift
      ;;
    --help)
      usage
      exit 0
      ;;
    *) die "unknown option: $1" ;;
  esac
done

[[ "$requested_version" == latest || "$requested_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || die "version must be latest or X.Y.Z"
[[ "$install_dir" == /* ]] || die "installation directory must be absolute"
[[ "$project_name" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || die "invalid Compose project name"

as_root() {
  if ((EUID == 0)); then
    "$@"
  else
    command -v sudo >/dev/null 2>&1 || die "sudo is required"
    sudo "$@"
  fi
}

check_platform() {
  [[ -r /etc/os-release ]] || die "Ubuntu 22.04 or newer is required"
  # shellcheck disable=SC1091
  source /etc/os-release
  [[ "${ID:-}" == ubuntu ]] || die "Ubuntu 22.04 or newer is required"
  local major="${VERSION_ID%%.*}"
  if [[ ! "$major" =~ ^[0-9]+$ ]] || ((major < 22)); then
    die "Ubuntu 22.04 or newer is required"
  fi
  case "$(dpkg --print-architecture)" in
    amd64 | arm64) ;;
    *) die "only amd64 and arm64 are supported" ;;
  esac
}

ensure_base_tools() {
  local command
  for command in curl openssl sha256sum; do
    command -v "$command" >/dev/null 2>&1 || {
      as_root env DEBIAN_FRONTEND=noninteractive apt-get update
      as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl openssl coreutils
      return
    }
  done
}

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    return
  fi

  echo "Installing Docker Engine and Compose plugin from download.docker.com..."
  as_root env DEBIAN_FRONTEND=noninteractive apt-get update
  as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl
  as_root install -m 0755 -d /etc/apt/keyrings
  as_root curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  as_root chmod a+r /etc/apt/keyrings/docker.asc

  # shellcheck disable=SC1091
  source /etc/os-release
  local codename="${UBUNTU_CODENAME:-$VERSION_CODENAME}"
  printf '%s\n' \
    'Types: deb' \
    'URIs: https://download.docker.com/linux/ubuntu' \
    "Suites: $codename" \
    'Components: stable' \
    "Architectures: $(dpkg --print-architecture)" \
    'Signed-By: /etc/apt/keyrings/docker.asc' \
    | as_root tee /etc/apt/sources.list.d/docker.sources >/dev/null

  as_root env DEBIAN_FRONTEND=noninteractive apt-get update
  as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  as_root systemctl enable --now docker
}

select_docker_command() {
  if docker info >/dev/null 2>&1; then
    docker_command=(docker)
  elif ((EUID == 0)); then
    die "Docker daemon is unavailable"
  else
    as_root docker info >/dev/null || die "Docker daemon is unavailable"
    docker_command=(sudo docker)
  fi
}

download() {
  curl -fL --retry 3 --retry-delay 2 "$1" -o "$2"
}

detect_bundle_version() {
  local -a jars
  mapfile -t jars < <(find "$1" -maxdepth 1 -type f -name 'issue-isekai-panel-*.jar' -printf '%f\n')
  ((${#jars[@]} == 1)) || die "bundle must contain exactly one panel JAR"
  local detected="${jars[0]#issue-isekai-panel-}"
  detected="${detected%.jar}"
  [[ "$detected" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "invalid bundle version"
  printf '%s' "$detected"
}

verify_bundle() {
  local directory="$1" version="$2"
  local file
  for file in .env.example INSTALL.md SHA256SUMS compose.yaml install.sh \
    "issue-isekai-panel-$version.jar" "issue-isekai-plugin-$version.jar"; do
    [[ -f "$directory/$file" ]] || die "bundle file is missing: $file"
  done
  (cd "$directory" && sha256sum -c SHA256SUMS) >&2
}

prepare_bundle() {
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ -n "$bundle_dir" ]]; then
    bundle_dir="$(realpath "$bundle_dir")"
  elif [[ -f "$script_dir/SHA256SUMS" && -f "$script_dir/compose.yaml" \
    && ! -f "$script_dir/.installed-version" ]]; then
    bundle_dir="$script_dir"
  else
    local version="$requested_version"
    if [[ "$version" == latest ]]; then
      local release_url
      release_url="$(curl -fsSL -o /dev/null -w '%{url_effective}' \
        "https://github.com/$REPOSITORY/releases/latest")"
      local tag="${release_url##*/}"
      [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "cannot resolve latest stable release"
      version="${tag#v}"
    fi
    temporary_dir="$(mktemp -d)"
    bundle_dir="$temporary_dir"
    local base="https://github.com/$REPOSITORY/releases/download/v$version"
    local file
    for file in .env.example INSTALL.md SHA256SUMS compose.yaml install.sh \
      "issue-isekai-panel-$version.jar" "issue-isekai-plugin-$version.jar"; do
      echo "Downloading $file" >&2
      download "$base/$file" "$bundle_dir/$file"
    done
  fi

  bundle_version="$(detect_bundle_version "$bundle_dir")" || return 1
  if [[ "$requested_version" != latest && "$requested_version" != "$bundle_version" ]]; then
    die "requested version $requested_version does not match bundle $bundle_version"
  fi
  verify_bundle "$bundle_dir" "$bundle_version" || return 1
}

ask() {
  local variable="$1" label="$2" default="$3" value
  read -r -p "$label [$default]: " value
  printf -v "$variable" '%s' "${value:-$default}"
}

ask_secret() {
  local variable="$1" label="$2" value
  read -r -s -p "$label: " value
  echo
  printf -v "$variable" '%s' "$value"
}

confirm() {
  $assume_yes && return
  local answer
  read -r -p "$1 [y/N]: " answer
  [[ "$answer" =~ ^[Yy]$ ]] || die "cancelled"
}

valid_ipv4() {
  local value="$1" part
  local -a parts
  IFS=. read -r -a parts <<< "$value"
  ((${#parts[@]} == 4)) || return 1
  for part in "${parts[@]}"; do
    [[ "$part" =~ ^[0-9]+$ ]] && ((10#$part <= 255)) || return 1
  done
}

validate_settings() {
  valid_ipv4 "$bind_address" || die "bind address must be an IPv4 address"
  if [[ ! "$panel_port" =~ ^[0-9]+$ ]] || ((panel_port < 1 || panel_port > 65535)); then
    die "panel port must be between 1 and 65535"
  fi
  [[ "$postgres_db" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "invalid PostgreSQL database name"
  [[ "$postgres_user" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "invalid PostgreSQL username"
  [[ -n "$admin_username" && ${#admin_username} -le 64 ]] || die "admin username must contain 1-64 characters"
  local password_bytes
  password_bytes="$(LC_ALL=C printf '%s' "$admin_password" | wc -c)"
  ((password_bytes >= 8 && password_bytes <= 72)) || die "admin password must contain 8-72 bytes"
  [[ "$app_locale" == ru || "$app_locale" == en ]] || die "locale must be ru or en"
  [[ "$telegram_enabled" == true || "$telegram_enabled" == false ]] || die "Telegram enabled must be true or false"
  if [[ "$telegram_enabled" == true ]]; then
    [[ -n "$telegram_token" && -n "$telegram_chat_id" ]] || die "Telegram token and chat ID are required"
  fi
  local value
  for value in "$admin_username" "$admin_password" "$telegram_token" "$telegram_chat_id"; do
    [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || die "settings cannot contain newlines"
  done
}

collect_settings() {
  bind_address="${ISSUE_ISEKAI_BIND_ADDRESS:-127.0.0.1}"
  panel_port="${ISSUE_ISEKAI_PANEL_PORT:-8080}"
  postgres_db="${ISSUE_ISEKAI_POSTGRES_DB:-issue_isekai}"
  postgres_user="${ISSUE_ISEKAI_POSTGRES_USER:-issue_isekai}"
  admin_username="${ISSUE_ISEKAI_ADMIN_USERNAME:-admin}"
  admin_password="${ISSUE_ISEKAI_ADMIN_PASSWORD:-}"
  app_locale="${ISSUE_ISEKAI_LOCALE:-ru}"
  telegram_enabled="${ISSUE_ISEKAI_TELEGRAM_ENABLED:-false}"
  telegram_token="${ISSUE_ISEKAI_TELEGRAM_TOKEN:-}"
  telegram_chat_id="${ISSUE_ISEKAI_TELEGRAM_CHAT_ID:-}"

  if ! $assume_yes; then
    ask bind_address "Bind address" "$bind_address"
    ask panel_port "Panel port" "$panel_port"
    ask postgres_db "PostgreSQL database" "$postgres_db"
    ask postgres_user "PostgreSQL user" "$postgres_user"
    ask admin_username "Admin username" "$admin_username"
    while :; do
      local confirmation
      ask_secret admin_password "Admin password (8-72 bytes)"
      ask_secret confirmation "Repeat admin password"
      [[ "$admin_password" == "$confirmation" ]] && break
      echo "Passwords do not match." >&2
    done
    ask app_locale "Language (ru/en)" "$app_locale"
    local telegram_answer
    read -r -p "Enable Telegram notifications? [y/N]: " telegram_answer
    if [[ "$telegram_answer" =~ ^[Yy]$ ]]; then
      telegram_enabled=true
      ask_secret telegram_token "Telegram bot token"
      ask telegram_chat_id "Telegram chat ID" ""
    else
      telegram_enabled=false
    fi
  fi

  [[ -n "$admin_password" ]] || die "ISSUE_ISEKAI_ADMIN_PASSWORD is required with --yes"
  validate_settings
  if [[ "$bind_address" != 127.0.0.1 ]]; then
    echo "WARNING: the panel will be publicly reachable. Put it behind HTTPS/reverse proxy." >&2
    confirm "Continue with public bind $bind_address?"
  fi
}

env_quote() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\'/\\\'}"
  printf "'%s'" "$value"
}

write_env() {
  local target="$install_dir/.env" temporary
  temporary="$(mktemp "$install_dir/.env.tmp.XXXXXX")"
  {
    printf 'POSTGRES_DB=%s\n' "$(env_quote "$postgres_db")"
    printf 'POSTGRES_USER=%s\n' "$(env_quote "$postgres_user")"
    printf 'POSTGRES_PASSWORD=%s\n' "$(env_quote "$(openssl rand -hex 24)")"
    printf 'BOOTSTRAP_ADMIN_USERNAME=%s\n' "$(env_quote "$admin_username")"
    printf 'BOOTSTRAP_ADMIN_PASSWORD=%s\n' "$(env_quote "$admin_password")"
    printf 'PANEL_BIND_ADDRESS=%s\n' "$(env_quote "$bind_address")"
    printf 'PANEL_PORT=%s\n' "$(env_quote "$panel_port")"
    printf 'APP_LOCALE=%s\n' "$(env_quote "$app_locale")"
    printf 'TELEGRAM_ENABLED=%s\n' "$(env_quote "$telegram_enabled")"
    printf 'TELEGRAM_BOT_TOKEN=%s\n' "$(env_quote "$telegram_token")"
    printf 'TELEGRAM_CHAT_ID=%s\n' "$(env_quote "$telegram_chat_id")"
    printf "TELEGRAM_POLL_INTERVAL_MS='30000'\n"
  } > "$temporary"
  chmod 600 "$temporary"
  mv "$temporary" "$target"
}

env_value() {
  local key="$1" fallback="$2" raw
  raw="$(sed -n "s/^${key}=//p" "$install_dir/.env" | tail -n 1)"
  [[ -n "$raw" ]] || {
    printf '%s' "$fallback"
    return
  }
  if [[ "$raw" == \'*\' || "$raw" == \"*\" ]]; then
    raw="${raw:1:${#raw}-2}"
  fi
  printf '%s' "$raw"
}

compose() {
  "${docker_command[@]}" compose --project-name "$project_name" \
    --env-file "$install_dir/.env" -f "$install_dir/compose.yaml" "$@"
}

backup_database() {
  local database user backup temporary timestamp
  database="$(env_value POSTGRES_DB issue_isekai)"
  user="$(env_value POSTGRES_USER issue_isekai)"
  timestamp="$(date -u +%Y%m%dT%H%M%S%NZ)"
  mkdir -p "$install_dir/backups"
  backup="$install_dir/backups/postgres-$timestamp.sql"
  temporary="$backup.tmp"
  echo "Creating database backup: $backup"
  if ! compose exec -T postgres pg_dump -U "$user" "$database" > "$temporary"; then
    rm -f "$temporary"
    die "database backup failed; update was not started"
  fi
  if [[ ! -s "$temporary" ]]; then
    rm -f "$temporary"
    die "database backup is empty; update was not started"
  fi
  chmod 600 "$temporary"
  mv "$temporary" "$backup"
}

copy_bundle() {
  local version="$1" file
  for file in .env.example INSTALL.md SHA256SUMS compose.yaml; do
    install -m 0644 "$bundle_dir/$file" "$install_dir/$file"
  done
  install -m 0755 "$bundle_dir/install.sh" "$install_dir/install.sh"
  install -m 0644 "$bundle_dir/issue-isekai-panel-$version.jar" "$install_dir/"
  install -m 0644 "$bundle_dir/issue-isekai-plugin-$version.jar" "$install_dir/"
  printf '%s\n' "$version" > "$install_dir/.installed-version"
}

check_platform
ensure_base_tools

if ! $assume_yes; then
  ask install_dir "Installation directory" "$install_dir"
  [[ "$install_dir" == /* ]] || die "installation directory must be absolute"
fi

prepare_bundle
version="$bundle_version"
install_docker
select_docker_command

owner="${SUDO_USER:-$(id -un)}"
group="$(id -gn "$owner")"
if ! install -d -m 0755 "$install_dir" 2>/dev/null; then
  as_root install -d -m 0755 -o "$owner" -g "$group" "$install_dir"
fi

fresh=true
installed_version=""
if [[ -f "$install_dir/.env" ]]; then
  fresh=false
  installed_version="$(cat "$install_dir/.installed-version" 2>/dev/null || true)"
  if [[ "$installed_version" != "$version" ]]; then
    confirm "Update ${installed_version:-unknown} to $version?"
    [[ -f "$install_dir/compose.yaml" ]] || die "existing compose.yaml is missing"
    backup_database
    echo "Flyway rollback is intentionally not automatic; keep the backup until acceptance is complete."
  fi
fi

copy_bundle "$version"
if $fresh; then
  collect_settings
  write_env
else
  chmod 600 "$install_dir/.env"
  echo "Existing .env preserved. Bootstrap admin password was not changed."
fi

compose pull
compose up -d --wait

bind_address="$(env_value PANEL_BIND_ADDRESS 127.0.0.1)"
panel_port="$(env_value PANEL_PORT 8080)"
readiness_host="$bind_address"
[[ "$readiness_host" == 0.0.0.0 ]] && readiness_host=127.0.0.1
curl -fsS "http://$readiness_host:$panel_port/actuator/health/readiness" | grep -q '"status":"UP"' \
  || die "panel readiness check failed"

echo "IssueIsekai $version is ready: http://$bind_address:$panel_port"
echo "Logs: cd $install_dir && ${docker_command[*]} compose --project-name $project_name logs -f panel"
echo "Plugin JAR: $install_dir/issue-isekai-plugin-$version.jar"
