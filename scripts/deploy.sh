#!/usr/bin/env bash
# One-command local deployment. Generates .env with random secrets on first run.
set -euo pipefail
cd "$(dirname "$0")/.."

rand() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 16
  else
    od -An -N16 -tx1 /dev/urandom | tr -d ' \n'
  fi
}

if [ ! -f .env ]; then
  cp .env.example .env
  root_pw="$(rand)"; db_pw="$(rand)"; redis_pw="$(rand)"; meili_key="$(rand)"
  sed -i "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${root_pw}|" .env
  sed -i "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=${db_pw}|" .env
  sed -i "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=${redis_pw}|" .env
  sed -i "s|^MEILI_MASTER_KEY=.*|MEILI_MASTER_KEY=${meili_key}|" .env
  echo "Created .env with random secrets."
fi

docker compose up -d --build
echo
echo "Site:  http://localhost:$(grep '^HTTP_PORT=' .env | cut -d= -f2)/"
echo "Admin: http://localhost:$(grep '^HTTP_PORT=' .env | cut -d= -f2)/admin/"
echo "Login: admin / admin123  (change after first login)"
