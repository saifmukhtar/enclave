# Enclave Monorepo Structure

This document explains the workspace layout and the role of each major file/directory so contributors can quickly find what to edit.

---

## Workspace Root

| Path | Type | Purpose |
|---|---|---|
| `README.md` | Doc | Project overview, architecture summary, and quick-start commands. |
| `LICENSE` | Legal | GNU AGPLv3 license text. |
| `CNAME` | Config | GitHub Pages custom domain mapping. |
| `.gitignore` | Config | Ignore patterns for local secrets/build artifacts. |
| `apps/` | Directory | Monorepo apps subdirectory (Android app & landing page website). |
| `backend/` | Directory | Monorepo backend services subdirectory (Supabase stack & signaling server). |
| `docs/` | Directory | Architectural guides, setup notes, and release logs. |
| `scripts/` | Directory | Operational orchestration and helper scripts. |
| `.github/workflows/` | Directory | CI/CD workflows for CodeQL analysis, testing, and deployment. |

---

## `apps/android/` (Android Client App)

| Path | Purpose |
|---|---|
| `apps/android/build.gradle.kts` | Root Gradle plugin declarations. |
| `apps/android/settings.gradle.kts` | Project/module inclusion and repositories. |
| `apps/android/gradle.properties` | Gradle runtime options. |
| `apps/android/gradlew`, `gradlew.bat` | Gradle wrappers. |
| `apps/android/local.properties.example` | Required SDK and runtime endpoints configuration template. |
| `apps/android/app/build.gradle.kts` | App module build configuration, dependencies, and build properties. |
| `apps/android/app/src/main/AndroidManifest.xml` | App manifest (permissions, declarations, FileProvider authority). |
| `apps/android/app/src/main/java/dev/saifmukhtar/enclave/` | Android Kotlin source tree. |

---

## `apps/web/` (Landing & Promotional Website)

| Path | Purpose |
|---|---|
| `apps/web/package.json` | Next.js build scripts and frontend dependencies. |
| `apps/web/index.html` | Main landing page document root. |
| `apps/web/src/App.tsx` | Main application routes and view layout. |
| `apps/web/src/styles.css` | visual system styling. |
| `apps/web/public/install` | Sync-copied version of VPS production droplet install script. |

---

## `backend/server/` (Self-Hosted Backend Infrastructure)

| Path | Purpose |
|---|---|
| `backend/server/docker-compose.yml` | Defines Supabase containers, Ntfy, storage mounts, and networks. |
| `backend/server/.env.example` | Template for required production environment variables. |
| `backend/server/deploy.sh` | Local deployment entrypoint for container startup. |
| `backend/server/deploy_to_cloud.sh` | Rsync / VPS deployment automation script. |
| `backend/server/setup_coturn.sh` | Coturn STUN/TURN daemon configuration helper. |
| `backend/server/signaling-server/` | Node.js signaling WebSocket server source tree (TypeScript). |
| `backend/server/volumes/api/kong.yml` | Kong declarative API gateway routing rules. |
| `backend/server/volumes/db/init/` | App schema tables and database migration scripts. |

---

## `docs/` (Consolidated Documentation)

| Path | Purpose |
|---|---|
| `docs/SYSTEM_ARCHITECTURE.md` | In-depth layout of E2EE cryptographic and transport layers. |
| `docs/SETUP_GUIDE.md` | step-by-step instructions for local development and cloud production VPS deployment. |
| `docs/REPO_STRUCTURE.md` | This file: repository layout and map. |
| `docs/release-notes-v1.1.0.md` | Logs and details of the v1.1.0 release. |

---

## `scripts/` (Orchestration & Tools)

| Path | Purpose |
|---|---|
| `scripts/setup-local.sh` | Developer local orchestration script (starts Supabase database and configures client). |
| `scripts/enclave.sh` | VPS production master setup script (fully provisions a fresh Ubuntu server). |
| `scripts/log-devices.sh` | ADB device logging script for offline USB debugging. |
