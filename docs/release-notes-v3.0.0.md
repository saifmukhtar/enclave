# 🔒 Enclave v3.0.0 Release Notes & Monorepo Changelog

We are proud to present **Enclave v3.0.0 (versionCode 4)** — a major milestone release. This release marks the transition of Enclave into a production-grade, sovereign monorepo architecture, introduces strict zero-trust security hardening, and achieves 100% compliance with official F-Droid open-source compilation standards.

This documentation serves as a comprehensive changelog and architectural feature matrix for version 3.0.0, incorporating all iterations and improvements since the v1.1.0 stable release.

---

## 🌟 What's New in v3.0.0

### 📦 1. Modular Monorepo Reorganization
The flat workspace has been fully restructured into a clean, modular monorepo to isolate development layers, facilitate independent client deployments, and scale backend services:
* **`apps/android/`**: The native Android application module (previously `enclave-ui`), isolated with localized Gradle scripts and assets.
* **`apps/web/`**: The Next.js landing page and documentation site (previously `enclave-react`).
* **`backend/server/`**: The core self-hosted server components, separating database schemas, signaling servers, and container configurations.
* **`docs/`**: Consolidated central directory for project architecture blueprints, setup guides, and security audits.
* **`scripts/`**: Centralized operational tools for local orchestration and VPS droplet deployment.

### 🏷️ 2. Namespace & Application ID Refactoring
To establish a distinct open-source namespace and resolve package namespace conflicts:
* Re-routed Java/Kotlin package paths to: `dev.saifmukhtar.enclave` (migrated from `com.enclave.app`).
* Standardized the `applicationId` and namespace references to `dev.saifmukhtar.enclave` across all app modules and Gradle scripts.
* Configured Fastlane metadata directories at `apps/android/app/src/main/fastlane/metadata/android/en-US/` containing title, short descriptions, and full descriptions to automate app store publishing.

### 🛡️ 3. Multi-Layer Security Hardening & Zero-Trust Architecture
Incorporated security hardening policies to protect user metadata, prevent data brute-forcing, and safeguard administrative databases:
* **Visual Debug Mode Screenshot Warnings ([LOW-5]):** Integrated float-warning banners in `AppLockScreen.kt` and settings cards in `ProfileScreen.kt` to warn users when running the app in `Debug` mode (since screenshot blocking is bypassed during development).
* **Upstream Supply Chain Immutability ([MEDIUM-2]):** Pinned Supabase PostgreSQL and GoTrue Authentication Docker images in `docker-compose.yml` with their immutable, cryptographic SHA256 digests to prevent supply-chain attacks.
* **Edge Rate-Limiting Policy ([LOW-3]):** Hardened the Nginx reverse proxy configurations in `enclave.sh` to restrict database API requests to 30 req/sec (burst 50) and WebSocket handshakes to 10/sec (burst 20) to defend against Denial of Service (DoS) and brute-force events.
* **Standardized Supabase Environments:** Completely standardized environment parameters from legacy aliases (`ANON_KEY`, `SERVICE_ROLE_KEY`) to standard `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` across runtime, client configurations, and setup scripts.

### 📲 4. F-Droid Compliance & Submodule Compilation
Achieved full compatibility with F-Droid’s strict, offline-only, zero-binary policy by introducing a dynamic submodule build pipeline for cryptographic libraries:
* **Clang Toolchain Binding:** Configured the rust-bindgen step to target the Android NDK's prebuilt LLVM/Clang compiler using NDK-specific clang and include paths. This avoids host compilation conflicts and missing header references.
* **Workspace Scanner Sanitization:** Implemented post-build cleanups to wipe intermediate build caches and configured strict `scanignore` and `scandelete` rules to safely skip JNI libraries and remove non-source backend components before F-Droid's verification scanner runs.
* **Dynamic NDK Toolchain Alignment:** Wrote a dynamic Gradle hook in `app/build.gradle.kts` to parse the version from F-Droid's injected `ndk.dir` and set `android.ndkVersion` to it. This silences the AGP ndkVersion mismatch warning (`[CXX1104]`).
* **Unsigned APK Build Outputs:** Standardized the build recipe target to `app/build/outputs/apk/release/app-release-unsigned.apk` so F-Droid's post-build script can locate and sign the compiled binaries.

---

## 🛠️ Complete Feature Matrix

| Feature Module | Technology Stack | Security Level | Details |
| :--- | :--- | :--- | :--- |
| **E2EE Chat** | Double Ratchet, X3DH (Signal Protocol) | Cryptographic Zero-Knowledge | Client-side encrypted messages, emojis, and media. Supports delete-for-everyone and delivery statuses. |
| **Secure Vault** | SQLCipher, Android Biometric Prompt | Device Hardware Level | Encrypted local database storing private albums, secure photo viewers with swipe-to-dismiss, and drag-to-select galleries. |
| **Shared Spaces (Lounge)** | HTML5 Canvas, WebSocket Realtime | Client-Server E2EE | Interactive drawing canvas, secret view-once photos (scratch-to-reveal canvas panels), and daily delayed letter capsules. |
| **WebRTC Calls** | WebRTC JNI, Acoustic Echo Cancellation (AEC) | Peer-to-Peer (P2P) / TURN | Ultra-low-latency calling with rear/front camera switching and cheek-detection proximity sensors. |
| **Sovereign Backend** | Docker, Supabase, Kong, Nginx, NTFY | Full Self-Host Isolation | Dockerized stack bypasses public cloud infrastructures. Communicates over edge-rate-limited HTTPS/WSS. |

---

## 📈 Changelog Summary (v1.1.0 ➔ v3.0.0)

### v3.0.0
* **Feature:** Added `CODE_OF_CONDUCT.md` based on Contributor Covenant v2.1 to project root.
* **Fix:** Resolved F-Droid build path find failure by adding `output` configuration paths.
* **Fix:** Dynamically mapped `ndkVersion` in Gradle script to prevent NDK version mismatch warnings.
* **Fix:** Purged Kotlin warnings (redundant Elvis operator in Lounge VM, unused expression in Scratch Compose).
* **Chore:** Committed updated compilation recipe to local `fdroiddata/metadata` repository and force-updated git tag `v3.0.0`.

### v2.1.0 (Security Release)
* **Feature:** Visual warnings for inactive screenshot restrictions in debug mode.
* **Feature:** Digest-pinning for third-party postgres and auth docker containers.
* **Feature:** Nginx edge rate-limiting rules for API and WebSockets.

### v2.0.0 (Monorepo Release)
* **Feature:** Relocated codebase into a structured, unified monorepo.
* **Feature:** Application package renamed to `dev.saifmukhtar.enclave`.
* **Feature:** Standardized Supabase environment variables globally.
* **Feature:** Fastlane configuration folders created.
* **Fix:** Resolved React documentation asset imports path errors.

---

## 📜 Community Standards & Licensing
Enclave remains dedicated to sovereign, open-source principles:
* **License:** GNU Affero General Public License v3.0 ([AGPL-3.0-only](file:///home/saif/enclave/LICENSE))
* **Code of Conduct:** Contributor Covenant v2.1 ([CODE_OF_CONDUCT.md](file:///home/saif/enclave/CODE_OF_CONDUCT.md))
