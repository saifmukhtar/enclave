# Enclave Setup Guide

This guide explains how to run Enclave in:

1. **Local development** (no public domain required)
2. **Production VPS deployment** (domain + TLS)

---

## 1) Prerequisites

### Local workstation

- Android Studio (latest stable)
- JDK 17
- Docker Engine + Docker Compose plugin
- Node.js 20+

### Production VPS (Ubuntu 24.04 recommended)

- Docker Engine + Compose plugin
- Node.js 20+
- PM2
- Nginx + Certbot
- Coturn

---

## 2) Local development setup

### Step 1 — Prepare configuration files

From repository root:

```bash
cp enclave-ui/local.properties.example enclave-ui/local.properties
cp enclave-server/.env.example enclave-server/.env
cp enclave-server/signaling-server/firebase-adminsdk.json.example enclave-server/signaling-server/firebase-adminsdk.json
```

Optional Firebase app config (if needed by your build):

```bash
cp enclave-ui/app/google-services.json.example enclave-ui/app/google-services.json
```

### Step 2 — Start local backend stack

```bash
chmod +x setup-local.sh
./setup-local.sh
```

What this script does:

- Verifies Docker is available
- Starts `enclave-server/docker-compose.yml`
- Checks:
  - `http://localhost:8000/` (Kong)
  - `http://localhost:8085/healthz` (signaling)
  - `http://localhost:3000/` (Supabase Studio)
  - `http://localhost:2586/v1/health` (Ntfy)
- Creates `enclave-ui/local.properties` if missing and points app URLs to `10.0.2.2`

### Step 3 — Complete `enclave-ui/local.properties`

The Android app build requires all keys below:

```properties
sdk.dir=/absolute/path/to/Android/Sdk

TURN_SERVER_URL=turn:10.0.2.2:3478
TURN_USERNAME=localdev
TURN_PASSWORD=localdevpass123

SIGNALING_SERVER_URL=ws://10.0.2.2:8085
SUPABASE_URL=http://10.0.2.2:8000
SUPABASE_KEY=replace_with_local_anon_key

NTFY_SERVER_URL=http://10.0.2.2:2586
NTFY_USERNAME=replace_with_ntfy_user
NTFY_PASSWORD=replace_with_ntfy_password
```

> For physical devices, replace `10.0.2.2` with your host machine LAN IP.

### Step 4 — Build Android client

```bash
cd enclave-ui
./gradlew assembleDebug
```

### Step 5 — Validate signaling server

```bash
cd enclave-server/signaling-server
npm install
npm run typecheck
npm run build
```

---

## 3) Production VPS deployment

## Step 1 — DNS

Create records:

- `api.yourdomain.com` → VPS IP
- `wss.yourdomain.com` → VPS IP

## Step 2 — Install runtime packages

```bash
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg lsb-release git nginx certbot python3-certbot-nginx coturn

# Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Node.js + PM2
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
sudo npm install -g pm2
```

## Step 3 — Copy server folder

```bash
rsync -avz --exclude 'node_modules' --exclude '.git' ./enclave-server root@YOUR_VPS_IP:/opt/
```

## Step 4 — Configure `/opt/enclave-server/.env`

Fill required values used by `deploy.sh`:

- `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `ANON_KEY`
- `SERVICE_ROLE_KEY`
- `SUPABASE_PUBLIC_URL`
- `API_EXTERNAL_URL`
- `SITE_URL`
- `JWT_EXPIRY`
- `JWT_EXP`
- `POSTGRES_USER`
- `SECRET_KEY_BASE`

## Step 5 — Deploy backend

```bash
cd /opt/enclave-server
chmod +x deploy.sh
WIPE_DB=true ./deploy.sh
```

`deploy.sh` validates required env vars, starts Docker stack, builds signaling server, and starts signaling using PM2 if available.

## Step 6 — Nginx + TLS reverse proxy

Use Certbot for certificates:

```bash
sudo certbot certonly --nginx -d api.yourdomain.com -d wss.yourdomain.com
```

Then configure:

- `api.yourdomain.com` → proxy to `127.0.0.1:8000`
- `wss.yourdomain.com` → proxy to `127.0.0.1:8085` with WebSocket headers

Validate and reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## Step 7 — Configure Coturn

Set secure long-term credential mode and open TURN ports:

- Listening: `3478` / `5349`
- Relay UDP range: `49152-65535`

Then restart:

```bash
sudo systemctl restart coturn
sudo systemctl enable coturn
```

## Step 8 — Firewall

Allow:

- `80/tcp`, `443/tcp`, `22/tcp`
- TURN: `3478/tcp+udp`, `5349/tcp+udp`
- Relay UDP: `49152:65535/udp`

---

## 4) Verification checklist

### Backend

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
curl -sS https://api.yourdomain.com/auth/v1/health
curl -sS https://wss.yourdomain.com/healthz
```

### Signaling process

```bash
pm2 list
pm2 logs enclave-signaling --lines 100
```

---

## 5) Troubleshooting

- **Gradle fails due missing keys:** confirm all required `local.properties` keys are present.
- **App cannot reach backend on emulator:** use `10.0.2.2` endpoints.
- **WebSocket disconnects in production:** confirm Nginx `Upgrade` + `Connection` headers and timeout settings.
- **TURN not working on mobile data:** verify Coturn ports and UDP relay range are open.

---

## 6) Related docs

- Project overview: [`README.md`](README.md)
- File-by-file map: [`REPO_STRUCTURE.md`](REPO_STRUCTURE.md)
