# Enclave — System Architecture

> A plain-English, end-to-end explanation of how every piece of this project fits together.
> No assumptions. No guessing. Everything here was read from actual source files.

---

## Table of Contents

1. [High-Level Overview](#high-level-overview)
2. [Tech Stack Breakdown](#tech-stack-breakdown)
3. [Folder Structure](#folder-structure)
4. [Data Flow & APIs](#data-flow--apis)
5. [Database Schema](#database-schema)
6. [Encryption & Security Model](#encryption--security-model)
7. [Background Workers & Services](#background-workers--services)
8. [Push Notifications](#push-notifications)
9. [How to Run / Deploy](#how-to-run--deploy)

---

## High-Level Overview

**Enclave is a private, end-to-end encrypted communication app built for exactly two people** — a couple who want a sovereign, self-hosted messaging space that no third party can read, index, or mine for metadata.

### What does it do?

- **E2EE Chat** — Text, images, voice notes, and video messages encrypted on-device with the Signal Protocol's Double Ratchet before anything leaves the phone.
- **WebRTC Video & Voice Calls** — Peer-to-peer calls with STUN/TURN traversal (Coturn) for NAT-busting.
- **Shared Canvas** — A live collaborative whiteboard synced over WebSockets at ~60 FPS.
- **Presence Engine ("Kiss Screen")** — A unique multi-sensory interaction layer: 2D soft-body physics mesh, ASMR whisper mode, haptic synthesis, and real-time gesture sync between two phones.
- **Lounge** — A shared space with music playlists, a collaborative scrapbook, daily love letters, dice games, countdown timers, and profile cards.
- **E2EE Vault** — A biometric-protected encrypted file vault for photos and documents with cloud backup.
- **Status Stories** — 24-hour ephemeral stories between the two users.
- **Disappearing Messages** — Messages that auto-delete after a configurable duration.

### The Three Major Pieces

```
┌─────────────────────────────────────────────────────────┐
│                    YOUR VPS (Self-Hosted)               │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │              Docker Compose Stack                  │  │
│  │                                                    │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │  │
│  │  │  Kong    │  │  GoTrue  │  │   PostgREST      │ │  │
│  │  │ (Gateway)│  │  (Auth)  │  │  (REST API)      │ │  │
│  │  └────┬─────┘  └────┬─────┘  └────────┬─────────┘ │  │
│  │       │              │                 │           │  │
│  │  ┌────▼──────────────▼─────────────────▼─────────┐ │  │
│  │  │           PostgreSQL Database                  │ │  │
│  │  │    (profiles, key bundles, vault metadata,     │ │  │
│  │  │     stories, music, drawings, scrapbook)       │ │  │
│  │  └───────────────────────────────────────────────┘ │  │
│  │                                                    │  │
│  │  ┌────────────┐  ┌────────────┐  ┌─────────────┐  │  │
│  │  │  Storage    │  │  Realtime  │  │   Ntfy      │  │  │
│  │  │  (Files)    │  │  (PubSub)  │  │  (Push)     │  │  │
│  │  └────────────┘  └────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────┐  ┌──────────────────────┐    │
│  │  Signaling Server    │  │     Coturn           │    │
│  │  (Node.js WebSocket) │  │  (STUN/TURN relay)   │    │
│  │  Port 8085           │  │  Port 3478/5349      │    │
│  └──────────────────────┘  └──────────────────────┘    │
└─────────────────────────────────────────────────────────┘
          ▲              ▲                ▲
          │  WebSocket   │  REST/WS       │  WebRTC
          │              │                │
┌─────────┴──────────────┴────────────────┴────────────────┐
│                  ANDROID APP (apps/android)              │
│                                                          │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │  Jetpack  │  │   Signal     │  │    Room DB        │  │
│  │  Compose  │  │   Protocol   │  │  (local SQLite)   │  │
│  │    UI     │  │  (libsignal) │  │                   │  │
│  └──────────┘  └──────────────┘  └───────────────────┘  │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│             REACT WEBSITE (apps/web)                     │
│                                                          │
│  Marketing/docs site — deployed to GitHub Pages          │
│  No backend connection. Static SPA.                      │
└──────────────────────────────────────────────────────────┘
```

### How the pieces connect

| From | To | Protocol | Purpose |
|------|----|----------|---------|
| Android App | Signaling Server | WebSocket (WSS) | Real-time message relay, WebRTC signaling, kiss/lounge sync |
| Android App | Supabase (Kong) | HTTPS REST | Auth, profile sync, key bundle upload, file storage |
| Android App | Supabase Realtime | WebSocket | Kiss screen presence broadcast (ephemeral, no DB writes) |
| Android App | Ntfy Server | WebSocket | Background push notification wakeups |
| Android App | Coturn | STUN/TURN (UDP) | NAT traversal for WebRTC calls |
| React Website | None | — | Static site, no backend connection |

**Key insight:** The Signaling Server is a **relay, not a database**. It routes encrypted blobs between the two connected clients. It never sees plaintext. The Supabase stack is the **persistent data layer** — authentication, profiles, key bundles, and file storage.

---

## Tech Stack Breakdown

### Android App (`apps/android`)

| Category | Technology | Version | Source |
|----------|-----------|---------|--------|
| Language | Kotlin | — | `build.gradle.kts` |
| UI Framework | Jetpack Compose | BOM 2024.06.00 | `build.gradle.kts` |
| Material | Material 3 | (from BOM) | `build.gradle.kts` |
| Min SDK | Android 14 (API 34) | compileSdk/targetSdk 35 | `build.gradle.kts` |
| Build | Gradle + KSP | — | `build.gradle.kts` |
| Local DB | Room | 2.6.1 | `build.gradle.kts` |
| E2EE Crypto | libsignal-client | 0.39.2 | `build.gradle.kts` |
| Hardware Crypto | AndroidX Security Crypto | 1.1.0-alpha06 | `build.gradle.kts` |
| Biometrics | AndroidX Biometric | 1.2.0-alpha05 | `build.gradle.kts` |
| WebRTC | Stream WebRTC Android | 1.1.1 | `build.gradle.kts` |
| HTTP/WebSocket | OkHttp | 4.12.0 | `build.gradle.kts` |
| WebSocket Client | Ktor Client WebSockets | 2.3.11 | `build.gradle.kts` |
| Backend SDK | Supabase Kotlin | BOM 2.6.1 | `build.gradle.kts` |
| → Auth | Supabase GoTrue KT | (from BOM) | `build.gradle.kts` |
| → REST | Supabase Postgrest KT | (from BOM) | `build.gradle.kts` |
| → Storage | Supabase Storage KT | (from BOM) | `build.gradle.kts` |
| → Realtime | Supabase Realtime KT | (from BOM) | `build.gradle.kts` |
| Serialization | KotlinX Serialization JSON | 1.6.3 | `build.gradle.kts` |
| Background Work | WorkManager | 2.9.0 | `build.gradle.kts` |
| Media Playback | Media3 (ExoPlayer) | 1.3.1 | `build.gradle.kts` |
| QR Codes | ZXing Core | 3.5.3 | `build.gradle.kts` |

### Backend (`backend/server`)

| Category | Technology | Version | Source |
|----------|-----------|---------|--------|
| Container Orchestration | Docker Compose | — | `docker-compose.yml` |
| Database | PostgreSQL (Supabase fork) | 15.1.0.147 | `docker-compose.yml` |
| API Gateway | Kong | 2.8.1 | `docker-compose.yml` |
| Auth Service | GoTrue | v2.132.3 | `docker-compose.yml` |
| REST API | PostgREST | v11.2.0 | `docker-compose.yml` |
| Realtime | Supabase Realtime | v2.28.31 | `docker-compose.yml` |
| File Storage | Supabase Storage API | v1.10.1 | `docker-compose.yml` |
| DB Admin | Supabase Studio | latest | `docker-compose.yml` |
| DB Meta | Postgres Meta | v0.68.0 | `docker-compose.yml` |
| Push Notifications | Ntfy | latest | `docker-compose.yml` |
| Signaling Server | Node.js + Express + ws | Express 5.2.1, ws 8.20.1 | `signaling-server/package.json` |
| STUN/TURN | Coturn | — | `setup_coturn.sh` |
| Process Manager | PM2 | — | `deploy.sh` |

### React Website (`apps/web`)

| Category | Technology | Version | Source |
|----------|-----------|---------|--------|
| Framework | React | 18.3.1 | `package.json` |
| Router | React Router DOM | 6.18.0 | `package.json` |
| Animation | Framer Motion | 10.12.0 | `package.json` |
| Markdown | React Markdown + remark-gfm | 8.0.5 / 3.0.1 | `package.json` |
| Build Tool | Vite | 5.4.8 | `package.json` |
| Language | TypeScript | 5.5.4 | `package.json` |
| E2E Tests | Playwright | 1.54.2 | `package.json` |
| Hosting | GitHub Pages | — | `.github/workflows/deploy-pages.yml` |

### Landing Page (`enclave-page`)

| Category | Technology | Source |
|----------|-----------|--------|
| Markup | Static HTML | `index.html` |
| Styling | Custom CSS | `style.css` |
| Scripting | Vanilla JavaScript | `script.js` |

---

## Folder Structure

```
enclave/                           ← Root of the monorepo
├── apps/android/                  ← 📱 ANDROID APP (Kotlin + Compose)
│   ├── app/
│   │   ├── build.gradle.kts       ← All dependencies, SDK versions, plugins
│   │   └── src/main/
│   │       ├── AndroidManifest.xml ← Permissions, activities, services
│   │       └── java/dev/saifmukhtar/enclave/
│   │           ├── MainActivity.kt          ← Entry point, sets up Compose
│   │           ├── crypto/
│   │           │   ├── CryptoManager.kt     ← 🔑 Double Ratchet encrypt/decrypt + local AES
│   │           │   ├── EnclaveSignalStore.kt ← Signal protocol state store
│   │           │   ├── ExifStripper.kt      ← Removes EXIF from photos
│   │           │   └── VaultCipher.kt       ← Vault file encryption
│   │           ├── data/
│   │           │   ├── config/
│   │           │   │   └── ConfigManager.kt ← 🔐 Encrypted config storage (URLs, keys)
│   │           │   ├── local/               ← Room database layer
│   │           │   │   ├── EnclaveDatabase.kt ← Room DB definition (v13, 9 entities)
│   │           │   │   ├── MessageEntity.kt  ← E2EE messages table
│   │           │   │   ├── MessageDao.kt     ← Message queries
│   │           │   │   ├── MediaMetadataEntity.kt ← Vault media index
│   │           │   │   ├── LetterEntity.kt   ← Love letters
│   │           │   │   ├── UserProfileEntity.kt  ← Cached partner profiles
│   │           │   │   ├── StatusStoryEntity.kt  ← 24h stories
│   │           │   │   ├── CallLogEntity.kt      ← Call history
│   │           │   │   ├── OutboxEntity.kt       ← Offline message outbox
│   │           │   │   ├── TimeCapsuleEntity.kt  ← Scheduled future messages
│   │           │   │   └── EncryptedNoteEntity.kt← E2EE notes
│   │           │   └── vault/
│   │           │       ├── EncryptedFileManager.kt ← Hardware-backed file encryption
│   │           │       └── VaultRepository.kt      ← Vault import/export/sync logic
│   │           ├── models/
│   │           │   ├── KissGestureFrame.kt  ← Kiss physics data model
│   │           │   └── Track.kt             ← Music track model
│   │           ├── network/
│   │           │   └── BundleRepository.kt  ← 🔗 ALL Supabase API calls
│   │           │       (key bundles, profiles, music, drawings,
│   │           │        scrapbook, vault, TURN credentials)
│   │           ├── notifications/
│   │           │   ├── NtfyListenerService.kt ← Background WebSocket push listener
│   │           │   └── EnclaveSyncWorker.kt    ← WorkManager sync trigger
│   │           ├── security/
│   │           │   └── ConfigEncryptor.kt    ← Config value encryption
│   │           ├── media/
│   │           │   ├── EncryptedDataSource.kt    ← E2EE media loading for ExoPlayer
│   │           │   ├── EncryptedFileDataSource.kt← File-based encrypted source
│   │           │   ├── MusicPlaybackService.kt   ← Foreground Media3 session service
│   │           │   ├── MusicSyncController.kt    ← Syncs playback state via signaling
│   │           │   └── VoiceMemoController.kt    ← Voice note recording
│   │           ├── ui/                        ← 🎨 All Compose UI screens
│   │           │   ├── auth/                  ← Login, app lock, no-partner gate
│   │           │   ├── bootstrap/             ← First-run server config screen
│   │           │   ├── call/                  ← Video call screen + call log
│   │           │   ├── chat/                  ← Chat screen + message components
│   │           │   ├── kiss/                  ← Presence Engine physics screen
│   │           │   ├── lounge/                ← Shared space (music, canvas, etc.)
│   │           │   ├── main/                  ← EnclaveApp (DI root) + nav host
│   │           │   ├── profile/               ← Profile edit + status stories
│   │           │   ├── vault/                 ← Encrypted vault + biometric auth
│   │           │   ├── theme/                 ← EnclaveTheme (colors, typography)
│   │           │   └── widget/                ← Home screen companion widget
│   │           ├── webrtc/
│   │           │   ├── SignalingClient.kt     ← 🔌 WebSocket signaling (Ktor)
│   │           │   ├── WebRtcManager.kt       ← WebRTC peer connection lifecycle
│   │           │   └── ScreenShareService.kt  ← Screen share foreground service
│   │           └── worker/                    ← ⏰ Background periodic workers
│   │               ├── OutboxSyncWorker.kt        ← Retries failed message sends
│   │               ├── DisappearingMessagesWorker.kt ← Deletes expired messages
│   │               ├── PreKeyRotationWorker.kt    ← Rotates Signal pre-keys
│   │               ├── DailyBackupWorker.kt       ← Cloud backup to Supabase Storage
│   │               └── TimeCapsuleWorker.kt       ← Sends scheduled future messages
│   └── local.properties.example           ← Template for server URLs/keys
│
├── backend/server/                   ← ☁️ BACKEND (Docker + Node.js)
│   ├── docker-compose.yml            ← 🐳 Full Supabase stack + Ntfy
│   ├── .env.example                  ← All environment variable templates
│   ├── deploy.sh                     ← Local deployment script
│   ├── deploy_to_cloud.sh            ← Production VPS deployment (rsync + SSH)
│   ├── generate_keys.js              ← Generates JWT secrets, ANON_KEY, SERVICE_ROLE_KEY
│   ├── setup_coturn.sh               ← Coturn STUN/TURN installer
│   ├── signaling-server/             ← WebSocket relay
│   │   ├── server.ts                 ← ⚡ The signaling server (~600 lines)
│   │   ├── package.json              ← Express 5 + ws 8
│   │   └── tsconfig.json
│   └── volumes/                      ← Persistent data mounts
│       ├── api/
│       │   └── kong.yml              ← Kong API gateway routes
│       ├── db/
│       │   ├── init/                 ← SQL migrations (run on first start)
│       │   │   ├── 00-seed_users.sql
│       │   │   ├── 01-pre_key_bundles.sql    ← Key bundles + profiles tables
│       │   │   ├── 03-profile-extensions.sql ← Username, stories, online status
│       │   │   ├── 04-lounge-extensions.sql  ← Music + storage bucket
│       │   │   ├── 05-drawings-gallery.sql   ← Drawings + storage bucket
│       │   │   ├── 06-scrapbook-table.sql    ← Scrapbook + storage bucket
│       │   │   ├── 07-playlist-queue.sql     ← Music queue + love language
│       │   │   ├── 08-vault-metadata.sql     ← Vault metadata + buckets
│       │   │   └── 09-turn-credentials.sql   ← TURN server credentials
│       │   ├── roles.sql, jwt.sql, etc.      ← Supabase internal setup
│       │   └── data.sql                       ← Seed data
│       └── ntfy/                     ← Ntfy auth database
│
├── apps/web/                         ← 🌐 MARKETING WEBSITE (React + Vite)
│   ├── package.json                  ← React 18, Router, Framer Motion
│   ├── vite.config.ts               ← Vite build with vendor chunk splitting
│   ├── src/
│   │   ├── App.tsx                  ← Main SPA with feature cards, architecture, setup
│   │   ├── main.tsx                 ← React entry point
│   │   ├── components/              ← Header, Hero, Footer, FeatureCard, DocsLayout
│   │   ├── docs/content.ts          ← Imports README, SETUP_GUIDE, REPO_STRUCTURE
│   │   └── styles.css               ← Global styles
│   └── public/
│       └── docs/                     ← Markdown doc files
│
├── .github/workflows/
│   └── deploy-pages.yml              ← GitHub Actions: build React + deploy to Pages
├── setup-local.sh                    ← One-command local dev setup
├── SETUP_GUIDE.md                    ← Detailed deployment guide
├── REPO_STRUCTURE.md                 ← Architecture map
├── README.md                         ← Project overview
└── LICENSE                           ← AGPLv3
```

---

## Data Flow & APIs

### 1. Sending an E2EE Text Message

This is the most important data flow in the entire app. Here's exactly what happens, step by step:

```
Partner A's Phone                                Server                               Partner B's Phone
─────────────────                                ──────                               ─────────────────

1. User types "hey" and hits Send
       │
2. ChatViewModel.sendEncryptedMessage()
       │
3. CryptoManager.encryptMessage()
   ├─ Double Ratchet encrypts "hey"
   │  into ciphertext bytes
   └─ Returns: ByteArray (ciphertext)
       │
4. SignalingClient.sendEncryptedMessage()
   ├─ Wraps ciphertext as Base64 in:
   │  { type: "SIGNAL_PAYLOAD",
   │    senderId: "user-a-uuid",
   │    targetId: "user-b-uuid",
   │    payload: "base64ciphertext...",
   │    contentType: "TEXT",
   │    messageId: "uuid" }
   └─ Sends over WebSocket
       │
       ├──────────────────────────────────►
       │                                   │
       │                          5. Signaling Server receives
       │                             ├─ Verifies sender is registered
       │                             ├─ Looks up target by targetId
       │                             ├─ If target online: relays message
       │                             ├─ If target offline: queues (max 100)
       │                             │  + sends Ntfy push notification
       │                             └─ Returns DELIVERY_STATUS to sender
       │                                   │
       │                                   ├──────────────────────────────►
       │                                   │                              │
       │                                   │                   6. SignalingClient receives
       │                                   │                      incomingSignalPayloads
       │                                   │                              │
       │                                   │                   7. MessageDecryptorUseCase
       │                                   │                      ├─ Base64 decode payload
       │                                   │                      ├─ CryptoManager.decryptMessage()
       │                                   │                      │  └─ Double Ratchet decrypt
       │                                   │                      └─ Returns: "hey"
       │                                   │                              │
       │                                   │                   8. ChatViewModel stores in
       │                                   │                      Room DB (MessageEntity)
       │                                   │                              │
       │                                   │                   9. Compose UI re-renders
       │                                   │                      with decrypted message
```

**Key point:** The server only ever sees Base64-encoded ciphertext. It cannot read the message content. The Double Ratchet ensures forward secrecy — each message uses a new key, so compromising one key doesn't expose past messages.

### 2. Authentication Flow

```
User                              Android App                      Supabase GoTrue
────                              ───────────                      ───────────────

1. Enter email + password
       │
2. LoginScreen.onLoginSuccess()
       │
3. supabase.auth.signInWith(Email)
   { email, password }
       │
       ├──────────────────────────────────────────►
       │                                          │
       │                              4. GoTrue verifies credentials
       │                                 against auth.users table
       │                                          │
       │                              5. Returns JWT session
       │                                 { accessToken, refreshToken, user }
       │                                          │
       ◄──────────────────────────────────────────┤
       │                                          │
6. App stores session
   ├─ resolvedMyId = user.id
   ├─ isLoggedIn = true
   ├─ Saves to SharedPreferences
   └─ Session auto-refreshes via
      Supabase SDK
```

**Important:** Signup is **disabled** after initial provisioning (`GOTRUE_DISABLE_SIGNUP: "true"`). This is a two-person app — you create exactly two accounts, then lock the door.

### 3. WebRTC Video Call Setup

```
Caller                            Signaling Server                 Callee
──────                            ────────────────                 ──────

1. User taps "Call"
       │
2. CallViewModel.initiateCall()
   ├─ Creates WebRtcManager
   ├─ Creates PeerConnection
   ├─ Adds local audio/video tracks
   ├─ Creates SDP Offer
   └─ Sends WEBRTC_OFFER
       │
       ├──────────────────────────────►
       │                               │
       │                      3. Server relays
       │                         WEBRTC_OFFER
       │                               │
       │                               ├────────────────────────────►
       │                               │                              │
       │                               │                   4. Callee receives offer
       │                               │                      ├─ Creates PeerConnection
       │                               │                      ├─ Creates SDP Answer
       │                               │                      └─ Sends WEBRTC_ANSWER
       │                               │                              │
       │                      5. Server relays                      │
       │                         WEBRTC_ANSWER                      │
       │                               │                              │
       ◄──────────────────────────────┤                              │
       │                              │                              │
6. Both exchange ICE_CANDIDATES       │                              │
   through signaling server           │                              │
       │                              │                              │
7. Direct P2P connection established  │                              │
   (or via TURN relay if NAT          │                              │
    is symmetric)                     │                              │
       │                              │                              │
8. Audio/video streams flow P2P       ├─────────────────────────────►│
   (SRTP encrypted by WebRTC)         ◄─────────────────────────────┤
```

### 4. Key Bundle Exchange (X3DH Handshake)

This is how the two devices establish an encrypted session for the first time:

```
Device A                                   Supabase                           Device B
────────                                   ────────                           ─────────

1. On first launch:
   CryptoManager.generateLocalKeysIfNecessary()
   ├─ Generate Identity KeyPair
   ├─ Generate 100 One-Time PreKeys
   ├─ Generate Signed PreKey
   └─ Store in EncryptedSharedPreferences

2. BundleRepository.uploadLocalBundle()
   ├─ POST to /rest/v1/pre_key_bundles
   │  { user_id, identity_key,
   │    signed_pre_key, one_time_pre_keys }
   └─ Also creates profile row
       │
       ├────────────────────────────────►
       │                                 │
       │                                 │       3. Device B does the same
       │                                 │          (uploads own bundle)
       │                                 │
4. When sending first message:
   BundleRepository.fetchPartnerBundleAndBuildSession()
   ├─ GET /rest/v1/pre_key_bundles?user_id=eq.{partnerId}
   ├─ Retrieve partner's identity key, signed pre-key,
   │  and one one-time pre-key
   ├─ SessionBuilder.process(bundle)
   │  └─ X3DH handshake establishes shared secret
   └─ Double Ratchet session is now active
```

### 5. Kiss Screen / Presence Engine Sync

The kiss screen uses **two transport layers** simultaneously:

| Transport | Use Case | Latency |
|-----------|----------|---------|
| **Supabase Realtime Broadcast** | Physics mesh position, gesture frames, haptic triggers | ~50-100ms (ephemeral, no DB writes) |
| **WebSocket Signaling** | Kiss workflow triggers, session coordination | ~20-50ms (direct relay) |

The `SupabaseBroadcastTransport` joins a Realtime channel named `room_lovers_sync:{roomId}` and uses `broadcast()` / `broadcastFlow()` for ephemeral events. The `SignalingTransport` sends `KISS_WORKFLOW_TRIGGER` messages through the signaling server for workflow-level coordination (start/end kiss session).

### 6. Lounge Shared Features

Lounge features (music, drawings, scrapbook) use a **hybrid sync model**:

- **Real-time coordination** (play/pause sync, canvas strokes): WebSocket signaling via `LOUNGE_*` message types
- **Persistent data** (songs, drawings, scrapbook entries): Supabase PostgREST CRUD
- **File storage** (music files, drawing images, photos): Supabase Storage public buckets

The `LoungeSyncUseCase` is the coordinator — it sends real-time events over WebSocket and calls `BundleRepository` for persistent operations.

---

## Database Schema

### PostgreSQL (Server-Side, via Supabase)

These tables live in the Docker-hosted PostgreSQL and are accessed via PostgREST:

| Table | Purpose | RLS | Source File |
|-------|---------|-----|-------------|
| `auth.users` | Supabase Auth user accounts | Built-in | Supabase internal |
| `public.profiles` | User profiles + online status + push tokens | ✅ Per-user | `01-pre_key_bundles.sql` |
| `public.pre_key_bundles` | Signal Protocol key bundles (identity + signed + one-time) | ✅ Per-user | `01-pre_key_bundles.sql` |
| `public.status_stories` | 24-hour ephemeral stories | ✅ Per-user | `03-profile-extensions.sql` |
| `public.lounge_songs` | Shared music library index | ✅ Authenticated | `04-lounge-extensions.sql` |
| `public.lounge_drawings` | Shared drawing gallery index | ✅ Authenticated | `05-drawings-gallery.sql` |
| `public.lounge_scrapbook` | Collaborative photo scrapbook | ✅ Authenticated | `06-scrapbook-table.sql` |
| `public.lounge_music_queue` | Shared playlist queue | ✅ Authenticated | `07-playlist-queue.sql` |
| `public.lounge_vault_metadata` | E2EE vault file index | ✅ Authenticated | `08-vault-metadata.sql` |
| `public.turn_credentials` | STUN/TURN server credentials | ✅ Authenticated | `09-turn-credentials.sql` |

**Storage Buckets:**

| Bucket | Public | Purpose |
|--------|--------|---------|
| `music` | ✅ Yes | Shared music files |
| `drawings` | ✅ Yes | Shared canvas drawings |
| `scrapbook` | ✅ Yes | Scrapbook photos |
| `vault` | ✅ Yes | E2EE encrypted vault files (plaintext never stored) |
| `backups` | ❌ No | Private encrypted database backups |

### Room SQLite (Client-Side, on Android)

The local database (`enclave_db`) is version 13 with 9 entities:

| Entity (Table) | Purpose | Key Fields |
|----------------|---------|------------|
| `messages` | Decrypted message cache | id, senderId, encryptedPayload, timestamp, messageType, deliveryStatus, disappearingDuration, reaction, readAt |
| `media_metadata` | Vault media file index | mediaId, localEncryptedPath, mimeType, sizeBytes, isFavorite, folderName |
| `letters` | Love letters | id, payload, createdAt |
| `user_profiles` | Cached profile data | userId, username, displayName, bio, avatarUrl, isOnline, loveLanguage, locationCity |
| `status_stories` | Local story cache | id, authorId, contentType, encryptedPayload, expiresAt |
| `call_logs` | Call history | id, callType (VIDEO), direction (IN/OUT), status (MISSED/COMPLETED), durationSeconds |
| `outbox_messages` | Offline message retry queue | targetId, type, payload, timestamp |
| `time_capsules` | Scheduled future messages | id, targetId, payloadText, sendAt |
| `encrypted_notes` | E2EE encrypted notes | id, titlePayload (BLOB), contentPayload (BLOB), authorId |

---

## Encryption & Security Model

### Three-Layer Encryption Architecture

```
Layer 3: Transport                 Layer 2: E2EE              Layer 1: At-Rest
──────────────                     ────────────               ────────────────
WSS (TLS) wraps                    Signal Protocol            Hardware-backed
all WebSocket                      Double Ratchet             EncryptedSharedPreferences
traffic to the                     encrypts message           for local key storage +
signaling server                   content BEFORE             EncryptedFile for vault
                                   it hits the wire           files on disk

Server sees:                       Server sees:               Phone thief sees:
TLS-encrypted blobs                Base64 ciphertext          AES-256-GCM encrypted
(gibberish without TLS key)        (gibberish without         files + encrypted prefs
                                   ratchet state)             (gibberish without
                                                              hardware keystore)
```

### Signal Protocol Implementation

The app uses `org.signal:libsignal-client:0.39.2` directly — the same cryptographic library that powers Signal Messenger:

1. **Identity Keys** — Long-term identity key pair generated on first launch, stored in `EncryptedSharedPreferences` with hardware-backed `MasterKey` (AES256_GCM)
2. **X3DH Key Agreement** — When two users first message, they perform Extended Triple Diffie-Hellman using pre-key bundles fetched from Supabase
3. **Double Ratchet** — Every subsequent message uses a new encryption key derived from a ratcheting chain, providing forward secrecy
4. **Pre-Key Rotation** — `PreKeyRotationWorker` periodically refreshes one-time pre-keys and re-uploads the bundle to Supabase

### Local Encryption

| What | How | Key Storage |
|------|-----|-------------|
| Signal state (sessions, pre-keys) | `EncryptedSharedPreferences` (AES256_SIV keys, AES256_GCM values) | Android Hardware Keystore via `MasterKey` |
| App config (server URLs, credentials) | `EncryptedSharedPreferences` (separate file: `enclave_secure_config`) | Android Hardware Keystore via `MasterKeys` |
| Vault files (photos, documents) | `EncryptedFile` (AES256_GCM_HKDF_4KB) | Android Hardware Keystore via `MasterKey` |
| Local symmetric ops | AES/GCM/NoPadding with random IV | Key in EncryptedSharedPreferences |
| Vault cloud sync | AES-256-GCM with shared vault key | Base64 in SharedPreferences (`vault_key`) |

### App Lock

The app requires biometric authentication (fingerprint/PIN) on every foreground resume. `BiometricPromptManager` manages the auth state, and `AppLockScreen` overlays the entire UI when locked — preventing any interaction until authenticated.

### Partner Pairing Security

- **No discovery.** The app doesn't search for other users. Partner ID is resolved by querying the `profiles` table for the "other" user (exactly two users exist).
- **Cryptographic invite.** Deep link `enclave://invite` allows sharing partner ID securely.
- **Signup lockdown.** After both accounts are created, `GOTRUE_DISABLE_SIGNUP: "true"` prevents any new registrations.
- **Certificate pinning.** Both the signaling WebSocket client and the Supabase HTTP client pin specific SHA-256 certificate hashes.

---

## Background Workers & Services

### WorkManager Periodic Workers

| Worker | Schedule | Purpose |
|--------|----------|---------|
| `OutboxSyncWorker` | On-demand | Retries sending messages that failed due to disconnected WebSocket |
| `DisappearingMessagesWorker` | Periodic | Deletes messages past their `expiresAt` timestamp from Room DB |
| `PreKeyRotationWorker` | Periodic | Generates fresh Signal one-time pre-keys and uploads to Supabase |
| `DailyBackupWorker` | Daily | Encrypts Room DB and uploads to Supabase `backups` bucket (keeps last 7) |
| `TimeCapsuleWorker` | Periodic | Checks for time capsules whose `sendAt` has passed and delivers them |

### Foreground Services

| Service | Type | Purpose |
|---------|------|---------|
| `NtfyListenerService` | `remoteMessaging` | Persistent WebSocket to Ntfy server for push notification wakeups |
| `MusicPlaybackService` | `mediaPlayback` | Media3/ExoPlayer session for background music playback |
| `ScreenShareService` | `mediaProjection` | WebRTC screen sharing capture |

### Signaling Client Lifecycle

The `SignalingClient` stays connected even when the app is backgrounded (`ON_STOP` does **not** close the connection). It only disconnects on `destroy()`. This ensures:
- Messages are received while the app is minimized
- Incoming call notifications arrive in real-time
- The pending message queue (max 200) buffers messages if the connection temporarily drops

---

## Push Notifications

Enclave uses a **self-hosted push notification pipeline** — no Google FCM required (though FCM is optionally supported):

```
Partner A sends message
  → Signaling Server detects Partner B is offline
  → Signaling Server queries Supabase: GET /rest/v1/profiles?id=eq.{partnerB}
  → Gets partner B's push_token (which is their Ntfy topic URL)
  → Signaling Server POSTs to Ntfy: { action: "sync", type: "SIGNAL_PAYLOAD" }
  → Ntfy Server pushes to Partner B's NtfyListenerService (WebSocket)
  → NtfyListenerService triggers EnclaveSyncWorker
  → EnclaveSyncWorker wakes the SignalingClient to reconnect and fetch queued messages
```

**Privacy design:** Push notification payloads contain **no sender identity and no message content**. They only contain a generic `action` field (`sync` or `incoming_call`) to wake the device.

---

## How to Run / Deploy

### Option A: Local Development

#### Prerequisites

- Docker + Docker Compose
- Node.js 20+
- Android Studio (with SDK 34+)
- An Android device or emulator running API 34+

#### Step 1: Start the Backend

```bash
# Clone the repo
git clone https://github.com/saifmukhtar/enclave.git
cd enclave

# One-command setup (starts Docker, verifies services)
chmod +x setup-local.sh
./setup-local.sh
```

This starts:
- PostgreSQL on `localhost:5432`
- Kong API gateway on `localhost:8000`
- Supabase Studio on `localhost:3000`
- Signaling Server on `localhost:8085`
- Ntfy on `localhost:2586`

#### Step 2: Build the Signaling Server

```bash
cd backend/server/signaling-server
npm ci
npm run build
node dist/server.js   # Or: npm start
```

#### Step 3: Create User Accounts

Open Supabase Studio at `http://localhost:3000`, navigate to Auth → Users, and create exactly two user accounts (email/password). Then **disable signup** by setting `GOTRUE_DISABLE_SIGNUP=true` in your `.env` and restarting the Docker stack.

#### Step 4: Generate Keys

```bash
cd backend/server
node generate_keys.js
# Copy the output into .env and apps/android/local.properties
```

#### Step 5: Configure the Android App

```bash
cd apps/android
cp local.properties.example local.properties
```

Edit `local.properties`:
```properties
sdk.dir=/path/to/Android/Sdk
SUPABASE_URL=http://10.0.2.2:8000    # 10.0.2.2 = emulator's host loopback
SUPABASE_KEY=your-anon-key
SIGNALING_SERVER_URL=ws://10.0.2.2:8085
```

#### Step 6: Build & Install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Step 7: First-Run Bootstrap

When you launch the app for the first time, it shows a **Bootstrap Screen** where you enter:
- Supabase URL
- Supabase Anon Key
- Signaling Server WebSocket URL
- TURN server credentials (optional)
- Ntfy server credentials (optional)

These are stored in hardware-backed `EncryptedSharedPreferences` and never hardcoded.

### Option B: Production Deployment (VPS)

#### Prerequisites

- A VPS (DigitalOcean Droplet, etc.) with Ubuntu
- A domain name pointing to the VPS
- Docker + Docker Compose on the VPS
- SSH access configured as `enclave` host

#### Step 1: Server Setup

```bash
# On your VPS:
cd ~/backend/server
cp .env.example .env
# Edit .env with your production values:
# - POSTGRES_PASSWORD (strong random)
# - JWT_SECRET (from generate_keys.js)
# - ANON_KEY, SERVICE_ROLE_KEY (from generate_keys.js)
# - SUPABASE_PUBLIC_URL=https://api.your-domain.com
# - SITE_URL=https://your-domain.com
# - SSL cert paths
# - NTFY credentials
```

#### Step 2: Deploy

```bash
# From your local machine:
cd backend/server
./deploy_to_cloud.sh
```

This script:
1. **rsyncs** code to the VPS (excluding `.env`, `node_modules`, `.git`)
2. **Restarts** Docker Compose on the VPS
3. **Runs SQL migrations** inside the PostgreSQL container
4. **Synces TURN credentials** from `.env` to the database
5. **Rebuilds** the signaling server TypeScript
6. **Restarts** the signaling server via PM2 (or nohup fallback)

#### Step 3: Configure Coturn

```bash
# On your VPS:
sudo bash backend/server/setup_coturn.sh
# Edit /etc/turnserver.conf with your public IP
# Open firewall ports: 3478, 5349 (TCP/UDP) + 49152-65535 (UDP)
```

#### Step 4: Nginx Reverse Proxy (Recommended)

Set up Nginx with Let's Encrypt to terminate TLS:
- `api.your-domain.com` → `localhost:8000` (Kong/Supabase)
- `wss.your-domain.com` → `localhost:8085` (Signaling WebSocket, with upgrade headers)
- `ntfy.your-domain.com` → `localhost:2586` (Ntfy)

#### Step 5: Deploy the Landing Page

The React marketing site auto-deploys via GitHub Actions when a commit message contains `github-page` or the workflow is manually triggered:

```bash
git commit -m "github-page: update landing page"
git push
```

The workflow (`.github/workflows/deploy-pages.yml`):
1. Checks out the repo
2. Installs `apps/web` dependencies
3. Builds the Vite app
4. Deploys the `dist/` folder to GitHub Pages

### Service Port Map

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Kong API Gateway | 8000 | HTTP | Supabase REST, Auth, Storage, Realtime |
| Kong API Gateway | 8443 | HTTPS | Same as above (TLS) |
| Signaling Server | 8085 | WebSocket | Real-time message/call/kiss relay |
| PostgreSQL | 5432 | TCP | Database (internal) |
| Supabase Studio | 3000 | HTTP | Database admin UI |
| Postgres Meta | 8082 | HTTP | Database metadata API |
| Ntfy Server | 2586 | HTTP/WS | Push notification relay |
| Coturn STUN | 3478 | UDP | NAT discovery |
| Coturn TURN | 3478/5349 | UDP/TCP | Media relay for WebRTC |

---

## Signaling Server Message Types

The signaling server (`server.ts`) routes these message types between connected clients:

| Type | Direction | Queued Offline? | Push Sent? | Purpose |
|------|-----------|-----------------|------------|---------|
| `REGISTER` | Client → Server | No | No | Authenticates and registers a WebSocket connection |
| `PONG` | Client → Server | No | No | Heartbeat response |
| `SIGNAL_PAYLOAD` | Client → Client | ✅ Yes | ✅ Yes | E2EE encrypted message (text, image, voice, video) |
| `WEBRTC_OFFER` / `OFFER` | Client → Client | ✅ Yes | ✅ Yes | WebRTC call SDP offer |
| `WEBRTC_ANSWER` / `ANSWER` | Client → Client | No | No | WebRTC call SDP answer |
| `ICE_CANDIDATE` | Client → Client | No | No | WebRTC ICE candidate exchange |
| `WEBRTC_HANGUP` | Client → Client | No | No | End call signal |
| `WEBRTC_RINGING` | Client → Client | No | No | Callee phone is ringing |
| `TYPING_STATUS` | Client → Client | No | No | "User is typing..." indicator |
| `READ_RECEIPT` | Client → Client | ✅ Yes | No | Message read confirmation |
| `DELIVERY_RECEIPT` | Client → Client | ✅ Yes | No | Message delivered confirmation |
| `PROFILE_UPDATE` | Client → Client | ✅ Yes | No | Online/offline status broadcast |
| `KISS_WORKFLOW_TRIGGER` | Client → Client | No | ✅ Yes | Start/end kiss session |
| `STORY_SHARE` | Client → Client | ✅ Yes | ✅ Yes | Status story notification |
| `STORY_VIEWED` | Client → Client | ✅ Yes | No | Story view receipt |
| `LOUNGE_*` | Client → Client | No | No | Lounge sync events (music, canvas, etc.) |
| `MUSIC_SYNC` | Client → Client | No | No | Music playback state sync |
| `DELIVERY_STATUS` | Server → Client | — | — | Server confirms message relay status |

**Offline queue rules:**
- Max 100 messages per offline user
- Messages expire after 24 hours
- Inactive queues (7 days) are purged
- WebRTC ANSWER and ICE_CANDIDATE are intentionally **not queued** (stale SDP breaks ICE negotiation)

---

## App Navigation & Screens

The Android app uses a bottom navigation bar with these tabs:

| Tab | Screen | ViewModel | Purpose |
|-----|--------|-----------|---------|
| 💬 Chat | `ChatScreen` | `ChatViewModel` | E2EE messaging, voice notes, image sharing |
| 💋 Kiss | `KissScreen` | `KissViewModel` + `KissWorkflowViewModel` | Presence Engine: physics mesh, haptics, audio |
| 📞 Call | `CallLogScreen` / `VideoCallScreen` | `CallViewModel` | WebRTC video/voice calls + call history |
| 🏡 Lounge | `LoungeScreen` | `LoungeViewModel` (+ sub-ViewModels) | Music, drawings, scrapbook, letters, games, countdown |
| 👤 Profile | `ProfileScreen` | `ProfileViewModel` | Username, bio, avatar, love language, status stories |
| 🗃️ Vault | `VaultScreen` | `VaultViewModel` | Biometric-protected E2EE file vault |

### Lounge Sub-Tabs

| Tab | Content |
|-----|---------|
| 🎵 Music Lounge | Shared playlist, upload, queue, vinyl record visualizer |
| 🎨 Live Canvas | Real-time collaborative drawing board |
| 📸 Scrapbook | Shared photo gallery with captions and dates |
| 💌 Daily Letters | Love letter exchange |
| 🎲 Dice & Intimacy | Random game prompts |
| ⏱️ Countdown | Shared countdown timers |
| 🔒 Scratch to Reveal | Scratch-off card reveals |
| 💕 Profile Cards | Partner profile comparison |

---

## 🤝 Credits & Open Source

Maintained by **Saif Mukhtar** ([@saifmukhtar](https://github.com/saifmukhtar) | [saifmukhtar.dev](https://saifmukhtar.dev)).

Powered by [Signal Protocol](https://github.com/signalapp/libsignal), [Supabase](https://supabase.com), [WebRTC](https://webrtc.org), and [Ntfy](https://ntfy.sh). See the [README](README.md) for full credits and AGPLv3 License details.
