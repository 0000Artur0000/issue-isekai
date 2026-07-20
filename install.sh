#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
command -v docker >/dev/null 2>&1 || {
  echo "Docker is required. See INSTALL.md." >&2
  exit 1
}
docker compose version >/dev/null

if [ ! -f .env ]; then
  cp .env.example .env
  chmod 600 .env
  echo "Created .env. Change passwords, then run ./install.sh again." >&2
  exit 2
fi

docker compose pull
docker compose up -d --wait
docker compose ps
