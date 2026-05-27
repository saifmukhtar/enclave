# Enclave Repository Structure

This document explains the workspace layout and the role of each major file/directory so contributors can quickly find what to edit.

---

## Workspace root

| Path | Type | Purpose |
|---|---|---|
| `README.md` | Doc | Project overview, architecture summary, and quick-start commands. |
| `SETUP_GUIDE.md` | Doc | Full local + production setup procedures. |
| `REPO_STRUCTURE.md` | Doc | This file: repository map and file responsibilities. |
| `setup-local.sh` | Script | Local bootstrap script for Docker stack + app local endpoint defaults. |
| `enclave.png` | Asset | Project/branding image used in docs/site. |
| `CNAME` | Config | GitHub Pages custom domain mapping. |
| `.gitignore` | Config | Ignore patterns for local secrets/build artifacts. |
| `LICENSE` | Legal | GNU AGPLv3 license text. |
| `enclave-page/` | Directory | Public landing page sources (HTML/CSS/JS). |
| `enclave-server/` | Directory | Self-hosted backend stack and server deployment scripts. |
| `enclave-ui/` | Directory | Android client app and Gradle project. |
| `.github/workflows/` | Directory | CI workflows (CodeQL and Pages deployment). |

---

## `enclave-page/` (website)

| Path | Purpose |
|---|---|
| `enclave-page/index.html` | Main landing page markup and content sections. |
| `enclave-page/style.css` | Visual system: colors, layout, glassmorphism, responsiveness. |
| `enclave-page/script.js` | Small UI behavior: smooth scroll and section reveal animation. |

---

## `enclave-server/` (backend)

### Core files

| Path | Purpose |
|---|---|
| `enclave-server/docker-compose.yml` | Defines Supabase services, Ntfy, volumes, and network. |
| `enclave-server/.env.example` | Template for required environment variables. |
| `enclave-server/deploy.sh` | Main deployment entrypoint for backend + signaling process startup. |
| `enclave-server/deploy_to_cloud.sh` | Rsync/remote execution helper for cloud updates. |
| `enclave-server/generate_keys.js` | Utility for generating secure key material. |
| `enclave-server/setup_coturn.sh` | Helper script for TURN server setup. |

### Signaling server

| Path | Purpose |
|---|---|
| `enclave-server/signaling-server/server.ts` | WebSocket signaling logic (session relay/events). |
| `enclave-server/signaling-server/package.json` | Node scripts/dependencies (`typecheck`, `build`, `start`). |
| `enclave-server/signaling-server/tsconfig.json` | TypeScript compiler configuration. |
| `enclave-server/signaling-server/firebase-adminsdk.json.example` | Firebase admin credentials template. |

### Supabase and DB assets

| Path | Purpose |
|---|---|
| `enclave-server/volumes/api/kong.yml` | Kong declarative gateway configuration. |
| `enclave-server/volumes/db/*.sql` | Core Supabase bootstrap SQL (roles, JWT, realtime, pooler, etc.). |
| `enclave-server/volumes/db/init/*.sql` | App-level schema and feature migrations. |
| `enclave-server/volumes/ntfy/` | Persistent Ntfy auth/storage mount. |

---

## `enclave-ui/` (Android app)

### Project/build files

| Path | Purpose |
|---|---|
| `enclave-ui/build.gradle.kts` | Root Gradle plugin declarations. |
| `enclave-ui/settings.gradle.kts` | Project/module inclusion and repositories. |
| `enclave-ui/gradle.properties` | Gradle runtime options. |
| `enclave-ui/gradlew`, `gradlew.bat` | Gradle wrappers. |
| `enclave-ui/local.properties.example` | Required app configuration template (SDK and runtime endpoints). |
| `enclave-ui/app/build.gradle.kts` | App build config, dependencies, required env keys. |

### Android source tree

| Path | Purpose |
|---|---|
| `enclave-ui/app/src/main/java/com/enclave/app/MainActivity.kt` | Android entry activity. |
| `.../ui/main/EnclaveMainScreen.kt` | Main navigation and route wiring. |
| `.../ui/chat/` | E2EE chat UI + message workflows. |
| `.../ui/call/` | Calling screens and call state logic. |
| `.../ui/lounge/` | Shared lounge experiences and tabs. |
| `.../ui/kiss/` | Kiss interaction module and related transport/audio/haptics. |
| `.../ui/vault/` | Encrypted vault browsing and controls. |
| `.../ui/profile/` | Profile and status-story interfaces. |
| `.../crypto/` | Encryption utilities and Signal store adapters. |
| `.../data/local/` | Room entities, DAOs, and database definitions. |
| `.../webrtc/` | WebRTC connection + signaling client integration. |
| `.../worker/` | WorkManager background jobs. |
| `.../notifications/` | Ntfy listener + sync trigger workers. |
| `.../media/` | Audio/media playback and encrypted sources. |

### Resource/config files

| Path | Purpose |
|---|---|
| `enclave-ui/app/src/main/res/` | App resources (XML layouts, drawables, values, raw assets). |
| `.../res/xml/network_security_config.xml` | Allows local/emulator cleartext development endpoints. |
| `enclave-ui/app/schemas/...` | Room migration schema snapshots by DB version. |

---

## CI and automation

| Path | Purpose |
|---|---|
| `.github/workflows/codeql.yml` | Static analysis/security scanning workflow. |
| `.github/workflows/deploy-pages.yml` | GitHub Pages deployment workflow for site content. |

---

## Where to make common changes

- **Update onboarding/setup docs:** `README.md`, `SETUP_GUIDE.md`
- **Change landing page visuals/content:** `enclave-page/index.html`, `style.css`, `script.js`
- **Adjust backend services:** `enclave-server/docker-compose.yml`, `enclave-server/.env`, `deploy.sh`
- **Add database schema feature:** `enclave-server/volumes/db/init/*.sql`
- **Modify signaling behavior:** `enclave-server/signaling-server/server.ts`
- **Add Android UI features:** `enclave-ui/app/src/main/java/com/enclave/app/ui/*`
