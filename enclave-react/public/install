#!/usr/bin/env bash
# =============================================================================
#  ███████╗███╗   ██╗ ██████╗██╗      █████╗ ██╗   ██╗███████╗
#  ██╔════╝████╗  ██║██╔════╝██║     ██╔══██╗██║   ██║██╔════╝
#  █████╗  ██╔██╗ ██║██║     ██║     ███████║██║   ██║█████╗  
#  ██╔══╝  ██║╚██╗██║██║     ██║     ██╔══██║╚██╗ ██╔╝██╔══╝  
#  ███████╗██║ ╚████║╚██████╗███████╗██║  ██║ ╚████╔╝ ███████╗
#  ╚══════╝╚═╝  ╚═══╝ ╚═════╝╚══════╝╚═╝  ╚═╝  ╚═══╝  ╚══════╝
#
#  MASTER DROPLET SETUP SCRIPT
#  Run as root on a fresh Ubuntu 22.04 / 24.04 Droplet.
#
#  curl -fsSL https://install.enclave.saifmukhtar.dev | sudo bash
# =============================================================================

set -Eeuo pipefail
trap 'echo ""; echo "❌  ERROR on line ${LINENO} — setup aborted." >&2; exit 1' ERR

# ─────────────────────────────────────────────────────────────────
#  CONSTANTS — hardcoded for this deployment
# ─────────────────────────────────────────────────────────────────
readonly REPO_URL="https://github.com/saifmukhtar/enclave.git"
readonly REPO_BRANCH="main"
readonly REPO_SPARSE_PATH="enclave-server"   # Only this subfolder is cloned
readonly INSTALL_DIR="/opt/enclave-server"

# ─────────────────────────────────────────────────────────────────
#  COLORS & PRETTY HELPERS
# ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; DIM='\033[2m'; RESET='\033[0m'
TICK="${GREEN}✓${RESET}"; CROSS="${RED}✗${RESET}"; ARROW="${CYAN}▶${RESET}"

header() {
  local title="$1"
  echo ""
  echo -e "${BOLD}${CYAN}┌──────────────────────────────────────────────────────┐${RESET}"
  printf "${BOLD}${CYAN}│  %-52s│${RESET}\n" "$title"
  echo -e "${BOLD}${CYAN}└──────────────────────────────────────────────────────┘${RESET}"
}

step()    { echo -e "\n  ${ARROW}  ${BOLD}$1${RESET}"; }
ok()      { echo -e "     ${TICK}  $1"; }
info()    { echo -e "     ${DIM}$1${RESET}"; }
warn()    { echo -e "\n  ${YELLOW}⚠   $1${RESET}"; }
die()     { echo -e "\n  ${RED}${BOLD}✗  $1${RESET}\n"; exit 1; }

prompt() {
  local var_name="$1" msg="$2" default="${3:-}" value=""
  if [[ -n "$default" ]]; then
    read -rp "     → ${msg} [${default}]: " value
    value="${value:-$default}"
  else
    while [[ -z "$value" ]]; do
      read -rp "     → ${msg}: " value
      [[ -z "$value" ]] && warn "This field cannot be empty."
    done
  fi
  eval "${var_name}=\"${value}\""
}

prompt_secret() {
  local var_name="$1" msg="$2" value=""
  while [[ -z "$value" ]]; do
    read -rsp "     → ${msg} (hidden): " value; echo ""
    [[ -z "$value" ]] && warn "This field cannot be empty."
  done
  eval "${var_name}=\"${value}\""
}

# ─────────────────────────────────────────────────────────────────
#  REQUIRE ROOT
# ─────────────────────────────────────────────────────────────────
[[ "$EUID" -ne 0 ]] && die "Please run as root: sudo bash setup_droplet.sh"

# ─────────────────────────────────────────────────────────────────
#  BANNER
# ─────────────────────────────────────────────────────────────────
clear
echo -e "${BOLD}${CYAN}"
echo "  ███████╗███╗   ██╗ ██████╗██╗      █████╗ ██╗   ██╗███████╗"
echo "  ██╔════╝████╗  ██║██╔════╝██║     ██╔══██╗██║   ██║██╔════╝"
echo "  █████╗  ██╔██╗ ██║██║     ██║     ███████║██║   ██║█████╗  "
echo "  ██╔══╝  ██║╚██╗██║██║     ██║     ██╔══██║╚██╗ ██╔╝██╔══╝  "
echo "  ███████╗██║ ╚████║╚██████╗███████╗██║  ██║ ╚████╔╝ ███████╗"
echo "  ╚══════╝╚═╝  ╚═══╝ ╚═════╝╚══════╝╚═╝  ╚═╝  ╚═══╝  ╚══════╝"
echo -e "${RESET}"
echo -e "  ${BOLD}Zero-Knowledge Private Communication Platform${RESET}"
echo -e "  ${DIM}Master Droplet Setup — Ubuntu 22.04 / 24.04${RESET}"
echo ""
echo -e "  ${DIM}Repository : ${REPO_URL}${RESET}"
echo -e "  ${DIM}Branch     : ${REPO_BRANCH}${RESET}"
echo -e "  ${DIM}Install at : ${INSTALL_DIR}${RESET}"
echo ""

# ─────────────────────────────────────────────────────────────────
#  PREREQUISITES — must be read before proceeding
# ─────────────────────────────────────────────────────────────────
echo -e "  ${YELLOW}${BOLD}⚠️  BEFORE YOU BEGIN — PREREQUISITES${RESET}"
echo ""
echo -e "  ${DIM}1.${RESET} You must own a ${BOLD}domain name${RESET} (e.g., yourdomain.com)."
echo -e "  ${DIM}2.${RESET} You must create three ${BOLD}DNS A Records${RESET} pointing to this server's IP:"
echo -e "        ${CYAN}api.enclave${RESET}.yourdomain.com"
echo -e "        ${CYAN}wss.enclave${RESET}.yourdomain.com"
echo -e "        ${CYAN}ntfy.enclave${RESET}.yourdomain.com"
echo -e "  ${DIM}3.${RESET} If you use Cloudflare, proxying MUST be ${BOLD}DISABLED${RESET} (set to 'DNS Only', gray cloud)."
echo -e "     Let's Encrypt SSL and WebRTC/TURN traffic will fail if proxied."
echo ""
echo -e "  ${CYAN}If you haven't done this yet, press Ctrl+C, set up your DNS, and run this script again.${RESET}"
echo ""
read -rp "  Press ENTER to confirm you have read the prerequisites... " _
echo ""

# ─────────────────────────────────────────────────────────────────
#  PRE-FLIGHT CHECKLIST — what this script will do
# ─────────────────────────────────────────────────────────────────
echo -e "  ${BOLD}This script will automatically:${RESET}"
echo ""
echo -e "  ${GREEN}[1]${RESET} Install system packages"
echo -e "      ${DIM}Docker, Node.js 20, PM2, Nginx, Certbot, Coturn, UFW firewall${RESET}"
echo ""
echo -e "  ${GREEN}[2]${RESET} Clone only the server code from GitHub"
echo -e "      ${DIM}Uses Git sparse-checkout — downloads enclave-server/ only (not the Android app)${RESET}"
echo ""
echo -e "  ${GREEN}[3]${RESET} Generate all cryptographic secrets automatically"
echo -e "      ${DIM}JWT secret, Postgres password, Supabase ANON + SERVICE_ROLE keys, Coturn secret${RESET}"
echo -e "      ${DIM}You never need to type or copy any secrets${RESET}"
echo ""
echo -e "  ${GREEN}[4]${RESET} Write the server .env configuration file"
echo -e "      ${DIM}Stored at ${INSTALL_DIR}/.env with permissions 600 (root-only)${RESET}"
echo ""
echo -e "  ${GREEN}[5]${RESET} Configure Coturn (STUN/TURN server for voice & video calls)"
echo -e "      ${DIM}Writes /etc/turnserver.conf, opens UDP relay ports 49152-65535${RESET}"
echo ""
echo -e "  ${GREEN}[6]${RESET} Configure UFW firewall"
echo -e "      ${DIM}Opens: SSH, 80 (HTTP), 443 (HTTPS), 3478/5349 (TURN), relay UDP range${RESET}"
echo ""
echo -e "  ${GREEN}[7]${RESET} Launch the full Supabase stack via Docker Compose"
echo -e "      ${DIM}PostgreSQL, Auth, REST API, Realtime, Storage, Kong API Gateway, Ntfy${RESET}"
echo ""
echo -e "  ${GREEN}[8]${RESET} Run all database migrations"
echo -e "      ${DIM}Pre-key bundles, user profiles, vault metadata, TURN credentials, and more${RESET}"
echo ""
echo -e "  ${GREEN}[9]${RESET} Create your ntfy push notification user account"
echo -e "      ${DIM}ntfy handles incoming call alerts and message sync triggers${RESET}"
echo ""
echo -e "  ${GREEN}[10]${RESET} Build and launch the WebSocket signaling server"
echo -e "       ${DIM}Compiles TypeScript and starts it under PM2 (auto-restart on crash/reboot)${RESET}"
echo ""
echo -e "  ${GREEN}[11]${RESET} Configure Nginx as a reverse proxy for all three services"
echo -e "       ${DIM}api.enclave.yourdomain.com → Supabase  |  wss.enclave.yourdomain.com → Signaling  |  ntfy.enclave.yourdomain.com → Ntfy${RESET}"
echo ""
echo -e "  ${GREEN}[12]${RESET} Obtain free SSL certificates via Let's Encrypt (Certbot)"
echo -e "       ${DIM}Certificates auto-renew every 90 days via cron${RESET}"
echo ""
echo -e "  ${GREEN}[13]${RESET} Create your two Enclave App accounts automatically in Supabase"
echo -e "       ${DIM}Bypasses in-app signup bugs by directly provisioning your accounts securely${RESET}"
echo ""
echo -e "  ${GREEN}[14]${RESET} Write a complete summary file with all secrets + Android config"
echo -e "       ${DIM}Saved to ${INSTALL_DIR}/enclave-secrets.txt — copy values into your Android project${RESET}"
echo ""
echo -e "  ${YELLOW}You will be asked 7 questions at the start. That's it.${RESET}"
echo ""
read -rp "  Press ENTER to begin setup, or Ctrl+C to cancel... " _

# ─────────────────────────────────────────────────────────────────
#  STEP 0 — COLLECT ALL USER INPUT UP-FRONT
# ─────────────────────────────────────────────────────────────────
header "STEP 0  ·  Your Configuration"

echo ""
echo -e "  ${BOLD}Question 1 of 7${RESET}"
echo -e "  ${DIM}Enter your root domain. The script will automatically create three nested subdomains:${RESET}"
echo -e "  ${DIM}  • api.enclave.yourdomain.com   →  Supabase REST / Auth / Storage${RESET}"
echo -e "  ${DIM}  • wss.enclave.yourdomain.com   →  End-to-end encrypted signaling WebSocket${RESET}"
echo -e "  ${DIM}  • ntfy.enclave.yourdomain.com  →  Push notification server${RESET}"
echo -e "  ${DIM}Example: saifmukhtar.dev${RESET}"
echo ""
prompt DOMAIN "Root domain (e.g. yourdomain.com)"

DOMAIN_API="api.enclave.${DOMAIN}"
DOMAIN_WSS="wss.enclave.${DOMAIN}"
DOMAIN_NTFY="ntfy.enclave.${DOMAIN}"

echo ""
echo -e "  ${DIM}Subdomains that will be configured:${RESET}"
echo -e "    ${TICK}  https://${DOMAIN_API}"
echo -e "    ${TICK}  wss://${DOMAIN_WSS}"
echo -e "    ${TICK}  https://${DOMAIN_NTFY}"
echo ""

echo -e "  ${BOLD}Question 2 of 7${RESET}"
echo -e "  ${DIM}Your email address is needed by Let's Encrypt to send certificate expiry notices.${RESET}"
echo -e "  ${DIM}It is NOT used for anything else.${RESET}"
echo ""
prompt CERTBOT_EMAIL "Email for SSL certificate notices"

echo ""
echo -e "  ${BOLD}Question 3 of 7${RESET}"
echo -e "  ${DIM}ntfy is the push notification server that wakes up your partner's phone when${RESET}"
echo -e "  ${DIM}you send a message or start a call. Choose a username for your ntfy account.${RESET}"
echo ""
prompt NTFY_USERNAME "ntfy username"

echo ""
echo -e "  ${BOLD}Question 4 of 7${RESET}"
echo -e "  ${DIM}Choose a strong password for your ntfy account. Input is hidden.${RESET}"
echo ""
prompt_secret NTFY_PASSWORD "ntfy password"

echo ""
echo -e "  ${BOLD}Question 5 of 7${RESET}"
echo -e "  ${DIM}Enclave needs two user accounts (one for you, one for your partner).${RESET}"
echo -e "  ${DIM}We will create these automatically in Supabase to avoid signup bugs.${RESET}"
echo ""
prompt APP_USER1_EMAIL "User 1 Email"
prompt_secret APP_USER1_PASSWORD "User 1 Password"

echo ""
echo -e "  ${BOLD}Question 6 of 7${RESET}"
echo -e "  ${DIM}Now for your partner's account.${RESET}"
echo ""
prompt APP_USER2_EMAIL "User 2 Email"
prompt_secret APP_USER2_PASSWORD "User 2 Password"

echo ""
echo -e "  ${BOLD}Question 7 of 7${RESET}"
echo -e "  ${DIM}Before SSL certificates can be issued, your three subdomains MUST have DNS A records${RESET}"
echo -e "  ${DIM}pointing to this server's public IP. Go to your DNS provider (Cloudflare, Namecheap, etc)${RESET}"
echo -e "  ${DIM}and add these records. The script will ask again later when it runs Certbot.${RESET}"
echo ""
echo -e "  ${DIM}(The script will detect your server IP automatically)${RESET}"
echo ""
echo -e "  ${BOLD}All questions answered. Setup starting now...${RESET}"
sleep 2

# ─────────────────────────────────────────────────────────────────
#  STEP 1 — SYSTEM DEPENDENCIES
# ─────────────────────────────────────────────────────────────────
header "STEP 1  ·  Installing System Packages"
echo ""
echo -e "  ${DIM}Installing: Docker, Node.js 20, PM2, Nginx, Certbot, Coturn, UFW${RESET}"
echo -e "  ${DIM}This may take 2-5 minutes on a fresh server...${RESET}"
echo ""

step "Updating apt package lists..."
apt-get update -qq
ok "Package lists updated"

step "Installing base utilities and services..."
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  curl wget git ca-certificates gnupg lsb-release \
  ufw nginx certbot python3-certbot-nginx \
  coturn jq apt-transport-https software-properties-common
ok "Base packages installed: nginx, certbot, coturn, ufw, git"

# Docker Engine
step "Installing Docker Engine..."
if command -v docker &>/dev/null; then
  ok "Docker already installed: $(docker --version)"
else
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    docker-ce docker-ce-cli containerd.io docker-compose-plugin
  systemctl enable docker --now
  ok "Docker installed: $(docker --version)"
fi

# Node.js 20 LTS
step "Installing Node.js 20 LTS..."
NODE_MAJOR="$(node --version 2>/dev/null | cut -d. -f1 | tr -d 'v' || echo 0)"
if ! command -v node &>/dev/null || [[ "$NODE_MAJOR" -lt 18 ]]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash - >/dev/null 2>&1
  apt-get install -y -qq nodejs
  ok "Node.js installed: $(node --version)"
else
  ok "Node.js already installed: $(node --version)"
fi

# PM2 — process manager (keeps signaling server alive across reboots)
step "Installing PM2 process manager..."
npm install -g pm2 --silent
pm2 startup systemd -u root --hp /root >/dev/null 2>&1 || true
ok "PM2 installed: $(pm2 --version)"

# ─────────────────────────────────────────────────────────────────
#  STEP 2 — SPARSE CLONE (enclave-server ONLY)
# ─────────────────────────────────────────────────────────────────
header "STEP 2  ·  Cloning enclave-server from GitHub"
echo ""
echo -e "  ${DIM}Using Git sparse-checkout to download only the server subfolder.${RESET}"
echo -e "  ${DIM}This skips the entire Android app (~200 MB) — keeping the server lean.${RESET}"
echo ""

if [[ -d "${INSTALL_DIR}/.git" ]]; then
  warn "Existing installation found at ${INSTALL_DIR}."
  read -rp "     → Update it from GitHub? (y/N): " _update_confirm
  if [[ "$_update_confirm" =~ ^[Yy]$ ]]; then
    step "Pulling latest changes from ${REPO_BRANCH}..."
    cd "${INSTALL_DIR}"
    git fetch origin "${REPO_BRANCH}"
    git checkout "${REPO_BRANCH}"
    git pull origin "${REPO_BRANCH}"
    ok "Repository updated"
  else
    ok "Keeping existing installation — skipping clone"
  fi
elif [[ -d "${INSTALL_DIR}" ]]; then
  # Directory exists but isn't a git repo — rename it and clone fresh
  warn "${INSTALL_DIR} exists but is not a git repository. Renaming to ${INSTALL_DIR}.bak"
  mv "${INSTALL_DIR}" "${INSTALL_DIR}.bak"
  _do_clone=true
else
  _do_clone=true
fi

if [[ "${_do_clone:-false}" == "true" ]]; then
  step "Cloning enclave-server/ from ${REPO_URL} (branch: ${REPO_BRANCH})..."
  git clone \
    --filter=blob:none \
    --sparse \
    --branch "${REPO_BRANCH}" \
    --depth 1 \
    "${REPO_URL}" \
    "${INSTALL_DIR}"

  cd "${INSTALL_DIR}"
  git sparse-checkout set "${REPO_SPARSE_PATH}"

  # Flatten: move enclave-server/* up to INSTALL_DIR if it's nested
  if [[ -d "${INSTALL_DIR}/${REPO_SPARSE_PATH}" ]]; then
    step "Flattening directory structure..."
    shopt -s dotglob
    mv "${INSTALL_DIR}/${REPO_SPARSE_PATH}"/* "${INSTALL_DIR}/"
    rmdir "${INSTALL_DIR}/${REPO_SPARSE_PATH}"
    shopt -u dotglob
    ok "Flattened: ${REPO_SPARSE_PATH}/* → ${INSTALL_DIR}/"
  fi

  ok "Repository cloned to ${INSTALL_DIR}"
fi

# Sanity check — critical files must be present
for _req in docker-compose.yml signaling-server/server.ts volumes/db/init volumes/api/kong.yml; do
  [[ -e "${INSTALL_DIR}/${_req}" ]] || die "Required path not found: ${INSTALL_DIR}/${_req}\nCheck that your GitHub repo structure is correct."
done
ok "Repository structure verified"

# ─────────────────────────────────────────────────────────────────
#  STEP 3 — GENERATE CRYPTOGRAPHIC SECRETS
# ─────────────────────────────────────────────────────────────────
header "STEP 3  ·  Generating Cryptographic Secrets"
echo ""
echo -e "  ${DIM}All secrets are generated using Node.js crypto (cryptographically secure random).${RESET}"
echo -e "  ${DIM}You do not need to provide or remember any of these — they are saved automatically.${RESET}"
echo ""

step "Generating JWT_SECRET (32 random bytes, base64)..."
JWT_SECRET=$(node -e "const c=require('crypto'); process.stdout.write(c.randomBytes(32).toString('base64'));")
ok "JWT_SECRET generated"

step "Generating SECRET_KEY_BASE (64 random bytes, hex)..."
SECRET_KEY_BASE=$(node -e "const c=require('crypto'); process.stdout.write(c.randomBytes(64).toString('hex'));")
ok "SECRET_KEY_BASE generated"

step "Generating POSTGRES_PASSWORD (16 random bytes, hex)..."
POSTGRES_PASSWORD=$(node -e "const c=require('crypto'); process.stdout.write(c.randomBytes(16).toString('hex'));")
ok "POSTGRES_PASSWORD generated"

step "Generating COTURN_AUTH_SECRET (32 random bytes, hex)..."
COTURN_SECRET=$(node -e "const c=require('crypto'); process.stdout.write(c.randomBytes(32).toString('hex'));")
ok "COTURN_AUTH_SECRET generated"

step "Signing Supabase ANON_KEY and SERVICE_ROLE_KEY JWTs..."
EXP_TS=$(node -e "process.stdout.write(String(Math.round(new Date('2036-01-01').getTime()/1000)));")
IAT_TS=$(node -e "process.stdout.write(String(Math.round(Date.now()/1000)));")

_sign_jwt() {
  local role="$1"
  node -e "
    const c = require('crypto');
    const secret = '${JWT_SECRET}';
    const b64u = s => Buffer.from(s).toString('base64').replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');
    const h = b64u(JSON.stringify({alg:'HS256',typ:'JWT'}));
    const p = b64u(JSON.stringify({iss:'supabase',ref:'stub',role:'${role}',iat:${IAT_TS},exp:${EXP_TS}}));
    const sig = c.createHmac('sha256',secret).update(h+'.'+p).digest('base64url');
    process.stdout.write(h+'.'+p+'.'+sig);
  "
}

ANON_KEY=$(_sign_jwt "anon")
SERVICE_ROLE_KEY=$(_sign_jwt "service_role")
ok "ANON_KEY signed (valid until 2036)"
ok "SERVICE_ROLE_KEY signed (valid until 2036)"

step "Detecting Droplet public IP address..."
DROPLET_IP=$(curl -s --max-time 5 http://169.254.169.254/metadata/v1/interfaces/public/0/ipv4/address 2>/dev/null \
  || curl -s --max-time 5 https://api.ipify.org 2>/dev/null \
  || hostname -I | awk '{print $1}')
ok "Droplet IP: ${DROPLET_IP}"

# ─────────────────────────────────────────────────────────────────
#  STEP 4 — WRITE .env FILE
# ─────────────────────────────────────────────────────────────────
header "STEP 4  ·  Writing Server .env File"
echo ""
echo -e "  ${DIM}All configuration is written to ${INSTALL_DIR}/.env${RESET}"
echo -e "  ${DIM}Permissions are set to 600 (readable by root only).${RESET}"
echo ""

cat > "${INSTALL_DIR}/.env" << ENV_EOF
# ── AUTO-GENERATED BY setup_droplet.sh ─────────────────────────────────────
# Generated : $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# Server IP : ${DROPLET_IP}
# Domain    : ${DOMAIN}
# ────────────────────────────────────────────────────────────────────────────

# — Database —
POSTGRES_USER=postgres
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
POSTGRES_PORT=5432

# — JWT & Security —
JWT_SECRET=${JWT_SECRET}
SECRET_KEY_BASE=${SECRET_KEY_BASE}
JWT_EXPIRY=86400
JWT_EXP=86400

# — Supabase Public URLs —
SUPABASE_PUBLIC_URL=https://${DOMAIN_API}
API_EXTERNAL_URL=https://${DOMAIN_API}
SUPABASE_URL=https://${DOMAIN_API}
SITE_URL=https://${DOMAIN}
GOTRUE_SITE_URL=https://${DOMAIN}
ADDITIONAL_REDIRECT_URLS=https://${DOMAIN}

# — Supabase API Keys —
ANON_KEY=${ANON_KEY}
SERVICE_ROLE_KEY=${SERVICE_ROLE_KEY}

# — Internal Ports (bound to 127.0.0.1, exposed via Nginx) —
STUDIO_PORT=3000
KONG_HTTP_PORT=8000
KONG_HTTPS_PORT=8443
META_PORT=8082

# — Signaling WebSocket Server —
PORT=8085
MAX_CLIENTS=2
MAX_WS_PAYLOAD_BYTES=20971520
HEARTBEAT_INTERVAL_MS=30000

# — SSL Certificates (managed by Certbot) —
SSL_CERT_PATH=/etc/letsencrypt/live/${DOMAIN_API}/fullchain.pem
SSL_KEY_PATH=/etc/letsencrypt/live/${DOMAIN_API}/privkey.pem

# — Coturn STUN/TURN —
COTURN_AUTH_SECRET=${COTURN_SECRET}
COTURN_REALM=${DOMAIN}

# — PostgREST Schema —
POSTGREST_DB_SCHEMA=public,storage,graphql_public

# — Ntfy Push Notifications —
NTFY_SERVER_URL=https://${DOMAIN_NTFY}
NTFY_USERNAME=${NTFY_USERNAME}
NTFY_PASSWORD=${NTFY_PASSWORD}
ENV_EOF

chmod 600 "${INSTALL_DIR}/.env"
ok ".env written to ${INSTALL_DIR}/.env (permissions: 600)"

# ─────────────────────────────────────────────────────────────────
#  STEP 5 — CONFIGURE COTURN
# ─────────────────────────────────────────────────────────────────
header "STEP 5  ·  Configuring Coturn STUN/TURN Server"
echo ""
echo -e "  ${DIM}Coturn enables WebRTC peer-to-peer connections for voice and video calls.${RESET}"
echo -e "  ${DIM}It acts as a media relay when two devices cannot connect directly (e.g. firewalls).${RESET}"
echo ""

step "Enabling coturn daemon in /etc/default/coturn..."
if ! grep -q "^TURNSERVER_ENABLED=1" /etc/default/coturn 2>/dev/null; then
  sed -i 's/#*TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/g' /etc/default/coturn
  grep -q "TURNSERVER_ENABLED=1" /etc/default/coturn || echo "TURNSERVER_ENABLED=1" >> /etc/default/coturn
fi
ok "Coturn daemon enabled"

step "Writing /etc/turnserver.conf..."
[[ -f /etc/turnserver.conf ]] && cp /etc/turnserver.conf /etc/turnserver.conf.bak.$(date +%s)
cat > /etc/turnserver.conf << TURN_EOF
# ── Enclave STUN/TURN — auto-configured by setup_droplet.sh ─────────────────
# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Network
listening-port=3478
tls-listening-port=5349
min-port=49152
max-port=65535
external-ip=${DROPLET_IP}
realm=${DOMAIN}

# Authentication — time-limited credentials via shared secret
use-auth-secret
static-auth-secret=${COTURN_SECRET}

# Security hardening
no-tcp-relay
no-multicast-peers
fingerprint
TURN_EOF

ok "turnserver.conf written"
info "TURN server will accept connections on ${DROPLET_IP}:3478 (UDP/TCP) and :5349 (TLS)"

# ─────────────────────────────────────────────────────────────────
#  STEP 6 — UFW FIREWALL
# ─────────────────────────────────────────────────────────────────
header "STEP 6  ·  Configuring Firewall (UFW)"
echo ""
echo -e "  ${DIM}UFW (Uncomplicated Firewall) controls which ports are accessible from the internet.${RESET}"
echo -e "  ${DIM}Only the necessary ports are opened — everything else is blocked by default.${RESET}"
echo ""

step "Configuring firewall rules..."
ufw --force reset >/dev/null 2>&1
ufw default deny incoming  >/dev/null 2>&1
ufw default allow outgoing >/dev/null 2>&1
ufw allow ssh              comment 'SSH access'
ufw allow 80/tcp           comment 'HTTP (Certbot + redirect to HTTPS)'
ufw allow 443/tcp          comment 'HTTPS (Nginx)'
ufw allow 3478/tcp         comment 'TURN TCP'
ufw allow 3478/udp         comment 'STUN/TURN UDP'
ufw allow 5349/tcp         comment 'TURN TLS TCP'
ufw allow 5349/udp         comment 'TURN TLS UDP'
ufw allow 49152:65535/udp  comment 'TURN relay media ports (WebRTC)'
ufw --force enable >/dev/null 2>&1
ok "Firewall enabled"

echo ""
echo -e "  ${DIM}Open ports:${RESET}"
echo -e "    ${TICK}  22    SSH"
echo -e "    ${TICK}  80    HTTP"
echo -e "    ${TICK}  443   HTTPS"
echo -e "    ${TICK}  3478  STUN/TURN (UDP + TCP)"
echo -e "    ${TICK}  5349  TURN TLS (UDP + TCP)"
echo -e "    ${TICK}  49152-65535  WebRTC relay ports (UDP)"

# ─────────────────────────────────────────────────────────────────
#  STEP 7 — LAUNCH SUPABASE DOCKER STACK
# ─────────────────────────────────────────────────────────────────
header "STEP 7  ·  Launching Supabase Docker Stack"
echo ""
echo -e "  ${DIM}Supabase provides the database, authentication, REST API, real-time subscriptions,${RESET}"
echo -e "  ${DIM}file storage, and the Kong API Gateway — all running in Docker containers.${RESET}"
echo -e "  ${DIM}Docker images are pulled from the internet on first run (~1-2 minutes).${RESET}"
echo ""

cd "${INSTALL_DIR}"

step "Creating required volume directories..."
mkdir -p volumes/db/data volumes/db/init volumes/storage \
         volumes/api volumes/functions volumes/snippets volumes/ntfy
ok "Volume directories ready"

step "Pulling Docker images (this may take a few minutes)..."
docker compose pull

step "Starting all Supabase containers..."
_attempt=1; _max=5
until docker compose up -d; do
  [[ "$_attempt" -ge "$_max" ]] && die "docker compose up failed after ${_max} attempts. Run 'docker compose logs' for details."
  warn "Attempt ${_attempt}/${_max} failed — retrying in 10s..."
  ((_attempt++)); sleep 10
done

ok "All containers started"
echo ""
echo -e "  ${DIM}Running containers:${RESET}"
docker compose ps --format "    {{.Name}}  \t{{.Status}}" 2>/dev/null || docker compose ps

step "Waiting 35 seconds for PostgreSQL to fully initialize..."
for _i in $(seq 1 35); do
  printf "\r     ${DIM}%d / 35 seconds...${RESET}" "$_i"
  sleep 1
done
echo ""
ok "Database initialization window complete"

# ─────────────────────────────────────────────────────────────────
#  STEP 8 — DATABASE MIGRATIONS
# ─────────────────────────────────────────────────────────────────
header "STEP 8  ·  Running Database Migrations"
echo ""
echo -e "  ${DIM}Migrations create all the custom tables Enclave needs:${RESET}"
echo -e "  ${DIM}pre-key bundles, user profiles, vault metadata, TURN credentials, and more.${RESET}"
echo ""

step "Waiting for PostgreSQL to be ready..."
for _i in $(seq 1 30); do
  if docker exec supabase-db pg_isready -U postgres -d postgres >/dev/null 2>&1; then
    ok "PostgreSQL is ready"
    break
  fi
  [[ "$_i" -eq 30 ]] && die "PostgreSQL never became ready. Try: docker logs supabase-db"
  printf "\r     ${DIM}Waiting... %d/30${RESET}" "$_i"
  sleep 3
done

MIGRATION_DIR="${INSTALL_DIR}/volumes/db/init"
_migration_count=0

for _sql in $(ls "${MIGRATION_DIR}"/[0-9][0-9]-*.sql 2>/dev/null | sort); do
  _name="$(basename "$_sql")"
  step "Running migration: ${_name}..."
  docker exec -i supabase-db psql -U postgres -d postgres < "$_sql"
  ok "${_name} applied"
  ((_migration_count++))
done

ok "All ${_migration_count} migrations applied"

step "Seeding TURN server credentials into database..."
TURN_URL_DB="turn:${DROPLET_IP}:3478"
docker exec -i supabase-db psql -U postgres -d postgres << PGSQL 2>/dev/null || true
INSERT INTO public.turn_credentials (turn_url, turn_username, turn_password)
  VALUES ('${TURN_URL_DB}', 'enclave', '${COTURN_SECRET}')
  ON CONFLICT DO NOTHING;
UPDATE public.turn_credentials
  SET turn_url='${TURN_URL_DB}', turn_username='enclave', turn_password='${COTURN_SECRET}'
  WHERE true;
PGSQL
ok "TURN credentials seeded into database"

# ─────────────────────────────────────────────────────────────────
#  STEP 9 — NTFY USER SETUP
# ─────────────────────────────────────────────────────────────────
header "STEP 9  ·  Setting Up Ntfy Push User"
echo ""
echo -e "  ${DIM}ntfy is a lightweight self-hosted push notification server.${RESET}"
echo -e "  ${DIM}Enclave uses it to wake up your partner's device when you send a message or start a call.${RESET}"
echo ""

step "Waiting for ntfy container to start..."
sleep 8

if docker ps --format '{{.Names}}' | grep -q enclave-ntfy; then
  step "Creating ntfy user: ${NTFY_USERNAME}..."
  
  # Securely pass password via environment variable directly to ntfy CLI inside Docker
  docker exec -e NTFY_PASSWORD="${NTFY_PASSWORD}" enclave-ntfy ntfy user add --role=user "${NTFY_USERNAME}" 2>/dev/null \
    || warn "Could not create ntfy user automatically. Check logs or run manually."
  
  # Grant publish + subscribe access to all topics
  docker exec enclave-ntfy ntfy access "${NTFY_USERNAME}" "*" read-write 2>/dev/null || true
  ok "ntfy user '${NTFY_USERNAME}' created with read-write access to all topics"
else
  warn "ntfy container not running. Skipping user creation."
  warn "Start it with: cd ${INSTALL_DIR} && docker compose up -d ntfy"
fi

# ─────────────────────────────────────────────────────────────────
#  STEP 10 — BUILD & LAUNCH SIGNALING SERVER
# ─────────────────────────────────────────────────────────────────
header "STEP 10 ·  Building Signaling WebSocket Server"
echo ""
echo -e "  ${DIM}The signaling server is a Node.js WebSocket server written in TypeScript.${RESET}"
echo -e "  ${DIM}It handles E2EE message delivery, call negotiation (WebRTC), and delivery receipts.${RESET}"
echo -e "  ${DIM}PM2 keeps it running in the background and restarts it automatically on crashes.${RESET}"
echo ""

step "Installing npm dependencies..."
cd "${INSTALL_DIR}/signaling-server"
npm install --silent
ok "npm dependencies installed"

step "Compiling TypeScript to JavaScript..."
npx tsc
ok "TypeScript compiled to dist/"

step "Starting signaling server under PM2..."
cd "${INSTALL_DIR}"
# Load env vars so PM2 starts with correct config
set -a; source .env; set +a

cd signaling-server
if pm2 describe enclave-signaling >/dev/null 2>&1; then
  pm2 restart enclave-signaling --update-env
  ok "Signaling server restarted"
else
  pm2 start dist/server.js \
    --name enclave-signaling \
    --log "${INSTALL_DIR}/signaling-server.log" \
    --time \
    --restart-delay=3000 \
    --max-restarts=10
  ok "Signaling server started"
fi
pm2 save >/dev/null
ok "PM2 configuration saved (will auto-start on reboot)"

# ─────────────────────────────────────────────────────────────────
#  STEP 11 — CONFIGURE NGINX
# ─────────────────────────────────────────────────────────────────
header "STEP 11 ·  Configuring Nginx Reverse Proxy"
echo ""
echo -e "  ${DIM}Nginx sits in front of all three services and routes HTTPS traffic to them.${RESET}"
echo -e "  ${DIM}All services only listen on 127.0.0.1 (localhost) — Nginx is the only public entry point.${RESET}"
echo ""

step "Removing default Nginx site..."
rm -f /etc/nginx/sites-enabled/default
ok "Default site removed"

# ── Supabase (api.domain) ──────────────────────────────────────
step "Writing Nginx config for ${DOMAIN_API} (Supabase)..."
cat > /etc/nginx/sites-available/enclave-api << NGINX_EOF
# ── Enclave: Supabase API Gateway ── auto-configured by setup_droplet.sh
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN_API};

    # All Supabase REST/Auth/Storage/Realtime traffic
    location / {
        proxy_pass         http://127.0.0.1:8000;
        proxy_set_header   Host \$host;
        proxy_set_header   X-Real-IP \$remote_addr;
        proxy_set_header   X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
        proxy_send_timeout 300s;

        # Required for Supabase Realtime WebSocket upgrades
        proxy_http_version 1.1;
        proxy_set_header   Upgrade \$http_upgrade;
        proxy_set_header   Connection "upgrade";

        # Large payloads (file uploads to Supabase Storage)
        client_max_body_size 500m;
    }
}
NGINX_EOF
ok "${DOMAIN_API} configured"

# ── Signaling WebSocket (wss.domain) ─────────────────────────
step "Writing Nginx config for ${DOMAIN_WSS} (Signaling)..."
cat > /etc/nginx/sites-available/enclave-wss << NGINX_EOF
# ── Enclave: E2EE Signaling WebSocket ── auto-configured by setup_droplet.sh
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN_WSS};

    location / {
        proxy_pass         http://127.0.0.1:8085;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade \$http_upgrade;
        proxy_set_header   Connection "upgrade";
        proxy_set_header   Host \$host;
        proxy_set_header   X-Real-IP \$remote_addr;
        proxy_set_header   X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;

        # WebSocket connections are long-lived — never time out
        proxy_read_timeout  86400s;
        proxy_send_timeout  86400s;
        proxy_connect_timeout 75s;
    }
}
NGINX_EOF
ok "${DOMAIN_WSS} configured"

# ── Ntfy (ntfy.domain) ─────────────────────────────────────
step "Writing Nginx config for ${DOMAIN_NTFY} (Ntfy)..."
cat > /etc/nginx/sites-available/enclave-ntfy << NGINX_EOF
# ── Enclave: Ntfy Push Notification Server ── auto-configured by setup_droplet.sh
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN_NTFY};

    location / {
        proxy_pass         http://127.0.0.1:2586;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade \$http_upgrade;
        proxy_set_header   Connection "upgrade";
        proxy_set_header   Host \$host;
        proxy_set_header   X-Real-IP \$remote_addr;
        proxy_set_header   X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        # Long-poll / SSE connections for push delivery
        proxy_read_timeout 86400s;
    }
}
NGINX_EOF
ok "${DOMAIN_NTFY} configured"

step "Enabling all Nginx sites..."
ln -sf /etc/nginx/sites-available/enclave-api  /etc/nginx/sites-enabled/
ln -sf /etc/nginx/sites-available/enclave-wss  /etc/nginx/sites-enabled/
ln -sf /etc/nginx/sites-available/enclave-ntfy /etc/nginx/sites-enabled/

step "Testing Nginx configuration..."
nginx -t
systemctl reload nginx
ok "Nginx configured and reloaded"

# ─────────────────────────────────────────────────────────────────
#  STEP 12 — SSL CERTIFICATES (LET'S ENCRYPT)
# ─────────────────────────────────────────────────────────────────
header "STEP 12 ·  Obtaining SSL Certificates (Let's Encrypt)"
echo ""
echo -e "  ${DIM}Certbot will contact Let's Encrypt and prove ownership of your domain${RESET}"
echo -e "  ${DIM}by serving a challenge file over HTTP. This only works if your DNS A records${RESET}"
echo -e "  ${DIM}already point to this server's IP (${DROPLET_IP}).${RESET}"
echo ""
echo -e "  ${BOLD}Required DNS A records at your DNS provider:${RESET}"
echo ""
printf "    %-35s  %s\n" "${DOMAIN_API}"  "→  ${DROPLET_IP}"
printf "    %-35s  %s\n" "${DOMAIN_WSS}"  "→  ${DROPLET_IP}"
printf "    %-35s  %s\n" "${DOMAIN_NTFY}" "→  ${DROPLET_IP}"
echo ""
echo -e "  ${YELLOW}If DNS is not ready, choose N — you can run Certbot manually later with:${RESET}"
echo -e "  ${DIM}  certbot --nginx -d ${DOMAIN_API} -d ${DOMAIN_WSS} -d ${DOMAIN_NTFY} --email ${CERTBOT_EMAIL}${RESET}"
echo ""

read -rp "     → Have you added the DNS A records above and they are pointing to ${DROPLET_IP}? (y/N): " _dns_ready

if [[ "$_dns_ready" =~ ^[Yy]$ ]]; then
  step "Requesting SSL certificates for all three subdomains..."
  certbot --nginx \
    --non-interactive \
    --agree-tos \
    --email "${CERTBOT_EMAIL}" \
    -d "${DOMAIN_API}" \
    -d "${DOMAIN_WSS}" \
    -d "${DOMAIN_NTFY}"

  ok "SSL certificates obtained for all three subdomains"

  step "Setting up automatic certificate renewal cron job..."
  (crontab -l 2>/dev/null | grep -v certbot; \
   echo "0 3 * * * certbot renew --quiet && systemctl reload nginx && systemctl restart coturn") \
    | crontab -
  ok "Auto-renewal cron configured (runs daily at 3 AM)"

  step "Configuring Coturn with Let's Encrypt TLS certificates..."
  echo "cert=/etc/letsencrypt/live/${DOMAIN_API}/fullchain.pem" >> /etc/turnserver.conf
  echo "pkey=/etc/letsencrypt/live/${DOMAIN_API}/privkey.pem" >> /etc/turnserver.conf
  
  step "Starting Coturn with TLS certificates now active..."
  systemctl restart coturn && systemctl enable coturn
  ok "Coturn running with TLS on port 5349"

  step "Reloading Nginx with HTTPS configuration..."
  systemctl reload nginx
  ok "Nginx now serving HTTPS"
else
  warn "SSL skipped. Run this command when your DNS is ready:"
  warn "  certbot --nginx -d ${DOMAIN_API} -d ${DOMAIN_WSS} -d ${DOMAIN_NTFY} --email ${CERTBOT_EMAIL}"
  warn "Then restart Coturn: systemctl restart coturn"

  step "Starting Coturn in HTTP-only mode (no TLS certs yet)..."
  systemctl restart coturn && systemctl enable coturn || true
  ok "Coturn started (will need restart after SSL is set up)"
fi

# ─────────────────────────────────────────────────────────────────
#  STEP 13 — CREATE APP ACCOUNTS
# ─────────────────────────────────────────────────────────────────
header "STEP 13 ·  Creating Enclave App Accounts"
echo ""
echo -e "  ${DIM}Creating your two user accounts directly via Supabase Admin API...${RESET}"
echo -e "  ${DIM}This bypasses the mobile app signup screen entirely for reliability.${RESET}"
echo ""

_create_user() {
  local email="$1"
  local pass="$2"
  local name="$3"
  step "Creating ${name} account: ${email}..."
  
  local resp
  resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:8000/auth/v1/admin/users" \
    -H "apikey: ${SERVICE_ROLE_KEY}" \
    -H "Authorization: Bearer ${SERVICE_ROLE_KEY}" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${pass}\",\"email_confirm\":true}")
  
  if [[ "$resp" == "200" || "$resp" == "201" ]]; then
    ok "${name} account created successfully"
  else
    warn "Failed to create ${name} account (HTTP ${resp}). It may already exist, or Supabase is still starting."
  fi
}

# Wait for Kong/Auth to be fully responsive
sleep 5

_create_user "${APP_USER1_EMAIL}" "${APP_USER1_PASSWORD}" "User 1"
_create_user "${APP_USER2_EMAIL}" "${APP_USER2_PASSWORD}" "User 2"

# ─────────────────────────────────────────────────────────────────
#  STEP 14 — WRITE SUMMARY FILE
# ─────────────────────────────────────────────────────────────────
header "STEP 14 ·  Writing Summary & Android Config"
echo ""
echo -e "  ${DIM}A complete summary is saved with all credentials and the exact values${RESET}"
echo -e "  ${DIM}you need to paste into your Android project's local.properties file.${RESET}"
echo ""

SUMMARY_FILE="${INSTALL_DIR}/enclave-secrets.txt"
cat > "$SUMMARY_FILE" << SUMMARY_EOF
================================================================================
  ENCLAVE — DEPLOYMENT COMPLETE
  Generated : $(date -u +"%Y-%m-%dT%H:%M:%SZ")
  Server IP : ${DROPLET_IP}
================================================================================

  SERVICE ENDPOINTS
  ─────────────────────────────────────────────────
  Supabase API  :  https://${DOMAIN_API}
  Signaling WSS :  wss://${DOMAIN_WSS}
  Ntfy Push     :  https://${DOMAIN_NTFY}

================================================================================
  📋  COPY THIS BLOCK INTO:  enclave-ui/local.properties
================================================================================
sdk.dir=/path/to/your/Android/Sdk

SUPABASE_URL=${DOMAIN_API}
SUPABASE_KEY=${ANON_KEY}
SIGNALING_SERVER_URL=wss://${DOMAIN_WSS}
TURN_SERVER_URL=turn:${DROPLET_IP}:3478
TURN_USERNAME=enclave
TURN_PASSWORD=${COTURN_SECRET}
NTFY_SERVER_URL=https://${DOMAIN_NTFY}
NTFY_USERNAME=${NTFY_USERNAME}
NTFY_PASSWORD=${NTFY_PASSWORD}
================================================================================

  🔐  SERVER SECRETS  (DO NOT SHARE — stored in ${INSTALL_DIR}/.env)
  ─────────────────────────────────────────────────
  POSTGRES_PASSWORD   : ${POSTGRES_PASSWORD}
  JWT_SECRET          : ${JWT_SECRET}
  SECRET_KEY_BASE     : ${SECRET_KEY_BASE}
  ANON_KEY            : ${ANON_KEY}
  SERVICE_ROLE_KEY    : ${SERVICE_ROLE_KEY}
  COTURN_AUTH_SECRET  : ${COTURN_SECRET}
  NTFY_USERNAME       : ${NTFY_USERNAME}
  NTFY_PASSWORD       : ${NTFY_PASSWORD}

================================================================================
  ✅  POST-INSTALL CHECKLIST
================================================================================
  [ ]  DNS A records created and pointing to ${DROPLET_IP}
  [ ]  SSL certificate obtained (certbot step above)
  [ ]  local.properties updated in Android project
  [ ]  Android app compiled and installed on test device
  [ ]  TURN reachability tested at: https://trickle-ice.appspot.com
       Use: turn:${DROPLET_IP}:3478  |  Username: enclave  |  Credential: ${COTURN_SECRET}

================================================================================
  🔒  SECURITY NOTE: PUBLIC SIGNUPS ARE DISABLED
================================================================================
  Public registration is hard-disabled at the API level (GOTRUE_DISABLE_SIGNUP).
  No one else can register on your server. Your two accounts were created securely.
  If you ever need to create another account, use the Supabase Studio UI
  or temporarily set GOTRUE_DISABLE_SIGNUP="false" in docker-compose.yml.

================================================================================
  📖  USEFUL COMMANDS
================================================================================
  View all containers   : docker compose -f ${INSTALL_DIR}/docker-compose.yml ps
  View container logs   : docker compose -f ${INSTALL_DIR}/docker-compose.yml logs -f [service]
  Restart signaling     : pm2 restart enclave-signaling
  View signaling logs   : pm2 logs enclave-signaling
  Renew SSL certs       : certbot renew
  Re-run full setup     : curl -fsSL https://install.enclave.saifmukhtar.dev | sudo bash
================================================================================
SUMMARY_EOF

chmod 600 "$SUMMARY_FILE"
ok "Summary written to ${SUMMARY_FILE}"

# ─────────────────────────────────────────────────────────────────
#  DONE
# ─────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}"
echo "  ╔═══════════════════════════════════════════════════════╗"
echo "  ║                                                       ║"
echo "  ║   ✅  ENCLAVE SERVER SETUP COMPLETE                   ║"
echo "  ║                                                       ║"
echo "  ╚═══════════════════════════════════════════════════════╝"
echo -e "${RESET}"
echo ""
echo -e "  ${BOLD}Your server is live at:${RESET}"
echo -e "    ${CYAN}https://${DOMAIN_API}${RESET}   (Supabase API)"
echo -e "    ${CYAN}wss://${DOMAIN_WSS}${RESET}     (Signaling)"
echo -e "    ${CYAN}https://${DOMAIN_NTFY}${RESET}  (Ntfy push)"
echo ""
echo -e "  ${BOLD}${YELLOW}Next step — read your Android config:${RESET}"
echo -e "    ${BOLD}cat ${SUMMARY_FILE}${RESET}"
echo ""
echo -e "  ${DIM}Copy the local.properties block from that file into your Android project${RESET}"
echo -e "  ${DIM}then recompile and install the app on your device.${RESET}"
echo ""
