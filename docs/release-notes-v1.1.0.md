# 🔒 Enclave v1.1.0 Production Security Release

We are thrilled to present the stable production release of **Enclave** — an elite, self-hosted, zero-knowledge private communication platform engineered specifically for couples navigating long-distance relationships. 

This release introduces comprehensive security patches resolving **High and Medium severity issues identified during static security audits (MobSF)**, solidifying Enclave's local storage and network relay layers against third-party observation or interference.

---

## 🛡️ Security Hardening & Patches (v1.1.0)

1. **Production Network Hardening (Strict HTTPS)**:
   - Re-architected network security domains. The production build configuration completely blocks cleartext loopback channels, enforcing **100% strict SSL (HTTPS/WSS)** across all connection relays.
   - Development loopback connections (e.g. plain HTTP/WS to emulator `10.0.2.2`) are strictly isolated to a secure local `/debug/` resource manifest and never compiled into production binaries.
2. **Exported Service Binder Verification**:
   - Hardened `MusicPlaybackService` connection handling. Implemented deep package-level authorization inside the service's `onGetSession` controller callback. 
   - Connections from unauthorized third-party apps are explicitly rejected, while permitting only the application's internal package, Android system UIs, media controllers, and Android Auto projection layers.
3. **Clean-Slate Compilation**:
   - Cleaned out stale cache configurations and bumped compile/target SDK versions, ensuring a completely warning-free, highly optimized assembly.

---

## 🌟 Core Features

### 💬 1. Cryptographic E2EE Chat
* **Double Ratchet & X3DH**: Absolute forward-secrecy chat engine built on verified cryptographic protocol implementations. All texts, emojis, and media elements are fully encrypted client-side.
* **Premium Multi-Selection Context Bar**: Signal-inspired select, copy, reply, export, and delete features (including **Delete for Everyone** revocation policies and secure offline queue synchronization).

### 🔑 2. Secure Vault & Media Shredder
* **Local E2EE Storage**: Secure local databases holding private partner-shared photo albums, isolated via multi-layer biometric/PIN authentication.
* **Premium Gallery Selection**: Multi-image touch selection and seamless **slide-to-select (drag-to-select)** gestures inspired by traditional gallery designs.
* **Biometric Authentication & Fullscreen Viewer**: Features smooth double-tap-to-zoom, dynamic swipe-down-to-dismiss transitions, and inline select/deselect checkboxes within the high-performance media pager.

### 💖 3. The Interactive Lounge (Shared Spaces)
* **Live Shared Canvas**: High-performance interactive drawing tablet using low-latency Canvas rendering to doodle, write, and trace artwork together in real-time.
* **Daily Letters**: Intimate delayed-delivery text capsules that unlock simultaneously, encouraging anticipation and daily connection.
* **Secret Photos (Scratch to Reveal)**: Mask private, shared photos with interactive scratch-to-reveal canvas panels.

### 📞 4. Dynamic WebRTC Video & Audio Calling
* **Audio & Video Streams**: Ultra-low-latency calls leveraging Front/Rear camera switching, modern in-communication devices audio routing, and hardware Acoustic Echo Cancellation (AEC).
* **Cheek-Detection & WakeLocks**: Integrates physical proximity sensor listeners that automatically dim the screen and transition speaker states when held up to the ear.

---

## ⚙️ Sovereign Backend Infrastructure

Enclave runs entirely on self-hosted dockerized architecture, allowing you to bypass commercial servers:
* **Dockerized Compose Stack**: Fully packaged Supabase (PostgreSQL, GoTrue Auth, Storage API), Kong API Gateway, and high-performance relay servers.
* **Custom Node.js Signaling**: Relays WebRTC signaling payloads, typing statuses, and delivery receipts in real-time.
* **NTFY Integration**: Dispatches encrypted background sync triggers and low-latency push notifications when devices are offline.

---

## 📲 F-Droid Compatibility
This release maintains 100% compliance with **F-Droid's open-source guidelines**. It contains **zero closed-source proprietary dependencies** (bypassing Google Play Services and FCM entirely by leveraging self-hosted WebSocket connections and open Ntfy nodes).
