# Enclave Repository Architecture

This document provides a comprehensive overview of the `enclave` monorepo. It details the directory structure, the division of responsibilities between the backend (`enclave-server`) and frontend (`enclave-ui`), and the specific architectural patterns used. 

This guide is designed to help you quickly locate code for debugging, understand where to place new features, and grasp the overall system design.

---

## 🏗️ High-Level Monorepo Structure

The repository is divided into two primary directories:

*   **`enclave-server/`**: Contains the Docker-based self-hosted backend infrastructure (Supabase, Ntfy, TURN) and the custom Node.js WebRTC signaling server.
*   **`enclave-ui/`**: Contains the native Android application built with Kotlin and Jetpack Compose.

```
enclave/
├── enclave-server/       # Backend Infrastructure & Signaling
├── enclave-ui/           # Native Android Application (Kotlin/Jetpack Compose)
├── README.md             # Project introduction
├── SETUP_GUIDE.md        # Comprehensive VPS and local setup guide
├── setup-local.sh        # Script for setting up local development environment
└── repo-structure.md     # This file
```

---

## 🗄️ Backend: `enclave-server/`

The backend relies on a self-hosted Supabase stack for identity, database, and storage, alongside a custom Node.js signaling server for WebRTC, a local Ntfy instance for push notifications, and a Coturn instance for STUN/TURN relays.

### Key Directories & Files

```text
enclave-server/
├── docker-compose.yml       # Defines the entire backend stack (Supabase services, Ntfy, Postgres)
├── deploy.sh                # Script to start/restart the Docker stack locally
├── deploy_to_cloud.sh       # Script to rsync files, run migrations, and restart the stack on the VPS
├── generate_keys.js         # Script to generate secure JWT secrets for Supabase configuration
├── setup_coturn.sh          # Script to install and configure the STUN/TURN server
│
├── volumes/                 # Persistent storage and configuration mounts for Docker
│   ├── api/                 # Kong API Gateway configuration (kong.yml)
│   ├── db/                  # Postgres data and initialization scripts
│   │   ├── init/            # 📍 SQL Migrations (Tables, functions, RLS policies, seeds)
│   │   ├── jwt.sql          # Injects JWT configuration into Postgres
│   │   └── roles.sql        # Database roles setup
│   └── ntfy/                # Ntfy push notification storage
│
└── signaling-server/        # Custom Node.js WebRTC Signaling Server
    ├── server.ts            # Main WebSocket server logic for WebRTC handshakes
    ├── package.json         # Node dependencies
    └── tsconfig.json        # TypeScript configuration
```

### 📍 Where to add backend features?
*   **New Database Tables/Policies:** Add a new SQL file (e.g., `09-new-feature.sql`) inside `enclave-server/volumes/db/init/`. Run `deploy_to_cloud.sh` to execute the migration on the VPS.
*   **WebRTC/Socket Logic:** Modify `enclave-server/signaling-server/server.ts`. The signaling server acts as a relay for SDP offers/answers and ICE candidates.
*   **New Infrastructure (e.g., Redis):** Add the service to `enclave-server/docker-compose.yml`.

---

## 📱 Frontend: `enclave-ui/`

The Android app follows a strict **MVVM (Model-View-ViewModel)** architecture built entirely with **Jetpack Compose**. It leverages Coroutines/Flows for asynchronous operations, Room for local caching, and Retrofit/Supabase-KT for network requests.

### High-Level App Structure

```text
enclave-ui/
├── build.gradle.kts         # Project-level Gradle configuration
├── local.properties         # 📍 Local secrets (Turn password, Ntfy password) - Not in git!
├── local.properties.example # Template for required environment variables
│
└── app/                     # Main Android App Module
    ├── build.gradle.kts     # App-level dependencies and build configs
    └── src/main/java/com/enclave/app/
```

### Detailed Package Architecture

Inside `app/src/main/java/com/enclave/app/`, the code is organized by technical domain and feature:

```text
com.enclave.app/
├── MainActivity.kt          # App entry point, handles OS lifecycle and intents.
│
├── crypto/                  # 🔐 Cryptography & E2EE Implementation
│   ├── CryptoManager.kt     # Signal Protocol orchestration (encryption/decryption of messages)
│   ├── EnclaveSignalStore.kt# Implementation of Signal Protocol data stores
│   ├── VaultCipher.kt       # Symmetric encryption for local Vault files
│   └── ExifStripper.kt      # Privacy utility to remove metadata from photos
│
├── data/                    # 🗃️ Local Data Layer
│   ├── local/               # Room Database (Entities, DAOs, Database class)
│   └── vault/               # Local encrypted file management (Vault repository)
│
├── network/                 # 🌐 Network Layer (Supabase interactions)
│   └── BundleRepository.kt  # Handles uploading/fetching PreKey bundles from Supabase
│
├── notifications/           # 🔔 Push Notifications
│   ├── NtfyListenerService.kt # Native WebSocket listener for real-time Ntfy push events
│   └── EnclaveSyncWorker.kt # Triggered by pushes to sync background data
│
├── worker/                  # ⚙️ Background Tasks (WorkManager)
│   ├── DailyBackupWorker.kt # Handles local/remote backups
│   ├── PreKeyRotationWorker.kt # Rotates Signal PreKeys automatically
│   ├── OutboxSyncWorker.kt  # Retries failed message sending
│   └── TimeCapsuleWorker.kt # Unlocks scheduled messages
│
├── webrtc/                  # 📞 Real-Time Communications
│   ├── WebRtcManager.kt     # Core PeerConnection setup, tracks, and Coturn ICE config
│   ├── SignalingClient.kt   # WebSocket client connecting to the Node.js signaling-server
│   └── ScreenShareService.kt# Foreground service for screen capturing
│
├── media/                   # 🎵 Media & Audio Playback
│   ├── MusicPlaybackService.kt # Foreground service for synchronized Spotify/Local playback
│   ├── EncryptedDataSource.kt  # ExoPlayer custom data source for decrypting media on the fly
│   └── VoiceMemoController.kt  # Audio recording utilities
│
└── ui/                      # 🎨 UI Layer (Jetpack Compose)
    ├── main/                # Top-level UI shells
    │   ├── EnclaveMainScreen.kt # 📍 Central Navigation Graph (NavHost) and UI Shell
    │   └── EnclaveApp.kt    # Root Compose setup, Theme provider, and Snackbar host
    │
    ├── auth/                # Login, Signup, and Setup screens
    ├── chat/                # Chat interface, message bubbles, encrypted input
    ├── call/                # Video/Audio call screens and controls
    ├── lounge/              # The shared "Lounge" screens
    │   ├── LoungeScreen.kt  # Main tab host container
    │   └── tabs/            # 📍 Modular tabs (ProfileCards, Scrapbook, Drawings, Music)
    ├── vault/               # Encrypted local file gallery and viewer
    ├── kiss/                # Special intimate features (Intimate Audio, Touch/Heartbeat sync)
    ├── profile/             # Settings and profile management
    └── components/          # Reusable UI widgets (Buttons, Dialogs, Loading states)
```

### 📍 Where to add frontend features?
*   **New Screen:** 
    1. Create a package inside `ui/` (e.g., `ui/calendar/CalendarScreen.kt`).
    2. Add the route to `ui/main/EnclaveMainScreen.kt`.
*   **New Lounge Tab:** 
    1. Create a file inside `ui/lounge/tabs/` (e.g., `CalendarTab.kt`).
    2. Add it to the tab array in `ui/lounge/LoungeScreen.kt`.
*   **Database Change:** 
    1. Update/Add the Entity in `data/local/`.
    2. Increment the database version in `EnclaveDatabase.kt`.
*   **New Background Task:** 
    1. Create a new `CoroutineWorker` in `worker/`.
    2. Enqueue it from `MainActivity` or a `ViewModel`.

---

## 🛠️ Typical Development Workflows

### 1. Modifying the Database Schema
1. Write the new `CREATE TABLE` script in `enclave-server/volumes/db/init/`.
2. Run `cd enclave-server && ./deploy_to_cloud.sh`.
3. Update the corresponding Kotlin Entity in `enclave-ui/app/src/main/java/com/enclave/app/data/local/`.

### 2. Adding a New Real-Time Feature
1. If it requires signaling (like custom drawing sync), add a new event type to `enclave-server/signaling-server/server.ts`.
2. Rebuild the signaling server (handled automatically via `deploy_to_cloud.sh`).
3. Update `enclave-ui/.../webrtc/SignalingClient.kt` to handle the new event.

### 3. Debugging UI/Navigation Issues
*   Check `enclave-ui/app/src/main/java/com/enclave/app/ui/main/EnclaveMainScreen.kt` for routing logic.
*   Ensure that large screens (like `LoungeScreen`) remain modularized by breaking complex UI components into smaller functions or files.

### 4. Background Sync / Push Notifications
*   `NtfyListenerService.kt` runs continuously to keep a WebSocket open to the Ntfy server.
*   When a push arrives, it wakes up `EnclaveSyncWorker.kt` to fetch new data from the DB. Use this pattern to sync new types of data silently.
