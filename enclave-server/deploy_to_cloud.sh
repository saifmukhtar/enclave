#!/bin/bash
# Enclave Cloud Deployment Script (Optimized)
# Deploys code, restarts docker containers, runs migrations, and rebuilds signaling on VPS.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=================================================="
echo "🚀 1. Syncing files to VPS via rsync (enclave)..."
echo "=================================================="
rsync -avz --delete \
  --exclude 'node_modules' \
  --exclude '.git' \
  --exclude '.gradle' \
  --exclude 'certs' \
  --exclude '.env' \
  --exclude 'volumes/db/data' \
  --exclude 'volumes/storage' \
  --exclude 'signaling-server/dist' \
  --exclude '*.log' \
  "$SCRIPT_DIR/" enclave:~/enclave-server/

echo ""
echo "=================================================="
echo "🐳 2. Restarting Supabase Docker stack on VPS..."
echo "=================================================="
ssh enclave "bash -s" << 'EOF'
  set -e
  cd ~/enclave-server
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose down || true
    docker-compose up -d
  else
    docker compose down || true
    docker compose up -d
  fi
  echo "Waiting 10 seconds for database to stabilize..."
  sleep 10
EOF

echo ""
echo "=================================================="
echo "🗄️ 3. Executing SQL migrations inside Supabase..."
echo "=================================================="
ssh enclave "bash -s" << 'EOF'
  set -e
  MIGRATION_DIR="$HOME/enclave-server/volumes/db/init"
  
  echo "🔍 Scanning custom migrations inside $MIGRATION_DIR..."
  for sql_file in $(ls "$MIGRATION_DIR"/[0-9][0-9]-*.sql 2>/dev/null | sort); do
    echo "⚡ Executing App Migration: $(basename "$sql_file")..."
    docker exec -i supabase-db psql -U postgres -d postgres < "$sql_file"
  done

  # Dynamically extract COTURN parameters from env file on Droplet and populate db credentials
  if [ -f "$HOME/enclave-server/.env" ]; then
    echo "🔄 Syncing Coturn TURN credentials from .env to Database..."
    COTURN_SECRET=$(grep "^COTURN_AUTH_SECRET=" "$HOME/enclave-server/.env" | cut -d'=' -f2-)
    COTURN_REALM_VAL=$(grep "^COTURN_REALM=" "$HOME/enclave-server/.env" | cut -d'=' -f2-)
    
    # If no realm/secret exist, try to parse from /etc/turnserver.conf directly
    if [ -z "$COTURN_SECRET" ] && [ -f "/etc/turnserver.conf" ]; then
      COTURN_SECRET=$(grep -m 1 "^user=" "/etc/turnserver.conf" | cut -d':' -f2-)
      COTURN_REALM_VAL=$(grep -m 1 "^realm=" "/etc/turnserver.conf" | cut -d'=' -f2-)
    fi
    
    # Default fallback to public VPS IP or domain if external-ip exists
    VPS_IP=$(grep -m 1 "^external-ip=" "/etc/turnserver.conf" 2>/dev/null | cut -d'=' -f2-)
    if [ -z "$VPS_IP" ]; then
      VPS_IP="your-turn-server.com"
    fi

    if [ -n "$COTURN_SECRET" ]; then
      # Determine turn_username and turn_password based on static configuration
      # Since coturn uses enclavemedia:BlushSecurePass2026 as user
      TURN_USER="enclavemedia"
      TURN_PASS="$COTURN_SECRET"
      TURN_URL="turn:$VPS_IP:3478"
      
      echo "⚡ Updating TURN credentials in database to: $TURN_URL with user $TURN_USER"
      docker exec -i supabase-db psql -U postgres -d postgres -c \
        "INSERT INTO public.turn_credentials (turn_url, turn_username, turn_password) \
         VALUES ('$TURN_URL', '$TURN_USER', '$TURN_PASS') \
         ON CONFLICT DO NOTHING;"
      
      # Also update if it already exists to ensure it has the correct credentials
      docker exec -i supabase-db psql -U postgres -d postgres -c \
        "UPDATE public.turn_credentials SET turn_url='$TURN_URL', turn_username='$TURN_USER', turn_password='$TURN_PASS';"
    fi
  fi
  
  echo "⚡ Running Supabase Realtime database release migrations..."
  docker exec -i supabase-realtime /app/bin/realtime eval 'Realtime.Release.migrate' || echo "Realtime migrations eval failed or already executed."
EOF

echo ""
echo "=================================================="
echo "🔄 4. Rebuilding & Restarting Signaling Server..."
echo "=================================================="
ssh enclave "bash -s" << 'EOF'
  set -e
  cd ~/enclave-server/signaling-server
  npm install
  npx tsc
  if [ -f ../.env ]; then
    export $(grep -v "^#" ../.env | xargs)
  fi
  if command -v pm2 >/dev/null 2>&1; then
    pm2 restart enclave-signaling --update-env || pm2 start dist/server.js --name enclave-signaling
  else
    echo "pm2 not found; launching server via node fallback"
    nohup node dist/server.js > ../signaling-server.log 2>&1 &
  fi
EOF

echo ""
echo "=================================================="
echo "✅ Deployment complete! Your cloud node is live."
echo "=================================================="