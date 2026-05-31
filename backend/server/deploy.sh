#!/bin/bash
# Enclave Server Automated Deployment Script

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
DOCKER_BIN="${DOCKER_BIN:-docker}"

echo "======================================"
echo " Starting Enclave Server Deployment"
echo "======================================"

if ! command -v "$DOCKER_BIN" >/dev/null 2>&1; then
  echo "docker is required but not found"
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required but not found"
  exit 1
fi

if [ ! -f "volumes/api/kong.yml" ]; then
  echo "volumes/api/kong.yml is missing. Deployment halted."
  exit 1
fi

required_vars=(
  POSTGRES_PASSWORD
  JWT_SECRET
  ANON_KEY
  SERVICE_ROLE_KEY
  SUPABASE_PUBLIC_URL
  API_EXTERNAL_URL
  SITE_URL
  JWT_EXPIRY
  JWT_EXP
  POSTGRES_USER
  SECRET_KEY_BASE
)

for v in "${required_vars[@]}"; do
  if ! grep -qE "^${v}=.+" .env; then
    echo "Missing required env var in $SCRIPT_DIR/.env: $v"
    exit 1
  fi
done

WIPE_DB="${WIPE_DB:-false}"

if [ "$WIPE_DB" = "true" ]; then
  echo "[1/5] Stopping containers and resetting database volume..."
  "$DOCKER_BIN" compose down || true
  rm -rf volumes/db/data
else
  echo "[1/5] Stopping containers (database kept)."
  "$DOCKER_BIN" compose down || true
fi

mkdir -p volumes/db/data volumes/db/init volumes/storage volumes/api volumes/functions volumes/snippets

echo "[2/5] Starting Supabase stack..."
attempt=1
max_attempts=5
until "$DOCKER_BIN" compose up -d; do
  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "docker compose up failed after ${max_attempts} attempts."
    exit 1
  fi
  echo "docker compose up failed (attempt ${attempt}/${max_attempts}). Retrying in 8s..."
  attempt=$((attempt + 1))
  sleep 8
done

echo "[3/5] Current Supabase container status:"
"$DOCKER_BIN" ps --filter "name=supabase" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo "[4/5] Building signaling server..."
cd signaling-server
npm ci
npm run build

echo "[5/5] Starting signaling process..."
if command -v pm2 >/dev/null 2>&1; then
  if pm2 describe enclave-signaling >/dev/null 2>&1; then
    pm2 restart enclave-signaling --update-env
  else
    pm2 start dist/server.js --name enclave-signaling
  fi
  pm2 save
else
  echo "pm2 not installed; starting with nohup fallback"
  nohup node dist/server.js > ../signaling-server.log 2>&1 &
fi

echo "======================================"
echo " Deployment Complete"
echo " Supabase gateway: ${SUPABASE_PUBLIC_URL:-http://localhost:8000}"
echo " Signaling ws: ws://0.0.0.0:${PORT:-8085}"
echo "======================================"
