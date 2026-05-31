# 💻 Enclave Setup Guide

> [!NOTE]
> This guide covers setting up Enclave for **Local Development** (no public domain required).
> 
> For deploying to a live server, we now highly recommend using the automated 1-click script found in the [README.md](README.md).

---

## 📑 Table of Contents

- [1. Prerequisites](#1-prerequisites)
- [2. Local Development Setup](#2-local-development-setup)
- [3. Verification Checklist](#3-verification-checklist)

---

## 1) Prerequisites

Before starting local development, ensure your machine has the following tools installed:

- **Android Studio** (latest stable)
- **JDK 17**
- **Docker Engine** + Docker Compose plugin
- **Node.js 20+**

---

## 2) Local Development Setup

### Step 1 — Prepare Configuration Files

From the repository root, copy the example configurations:

```bash
cp apps/android/local.properties.example apps/android/local.properties
cp backend/server/.env.example backend/server/.env
```

### Step 2 — Start Local Backend Stack

Run the local setup script to bootstrap the Docker containers:

```bash
chmod +x setup-local.sh
./setup-local.sh
```

**What this script does:**
- Verifies Docker is available
- Starts the `backend/server/docker-compose.yml` stack
- Checks health endpoints for Kong (8000), Signaling (8085), Supabase Studio (3000), and Ntfy (2586).
- Creates `apps/android/local.properties` if missing and points app URLs to the emulator loopback (`10.0.2.2`).

### Step 3 — Complete `local.properties`

Open `apps/android/local.properties`. The Android app build requires all keys below:

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

> [!TIP]
> **Using a Physical Device?**
> If you are debugging on a physical Android device rather than the emulator, replace all instances of `10.0.2.2` with your host machine's LAN IP address (e.g., `192.168.1.X`).

### Step 4 — Build Android Client

Compile the app using Gradle:

```bash
cd apps/android
./gradlew assembleDebug
```

### Step 5 — Validate Signaling Server

We highly recommend verifying the signaling server typescript compilation locally:

```bash
cd backend/server/signaling-server
npm install
npm run typecheck
npm run build
```

---

## 3) Verification Checklist

### Check Backend Services

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
curl -sS http://localhost:8000/auth/v1/health
curl -sS http://localhost:8085/healthz
```

> [!IMPORTANT]
> To access the Supabase Database dashboard, open `http://localhost:3000` in your browser.

---

## 🤝 Credits & Open Source

Maintained by **Saif Mukhtar** ([@saifmukhtar](https://github.com/saifmukhtar) | [saifmukhtar.dev](https://saifmukhtar.dev)).

Powered by [Signal Protocol](https://github.com/signalapp/libsignal), [Supabase](https://supabase.com), [WebRTC](https://webrtc.org), and [Ntfy](https://ntfy.sh). See the [README](README.md) for full credits and AGPLv3 License details.
