# Enclave Master Roadmap: Ecosystem Pillars

This document details the logical, sequential development phases to fully realize the Master Vision of Enclave, our bespoke, two-user hardware-secured private digital ecosystem.

---

## Phase 16: Signal-Grade Chat Core & Real-time Delivery/Read/Typing Engine
* **Goal**: Establish the local persistent E2EE history foundation and real-time state synchronization.
* **Architecture**:
  - Instantiate and wire `EnclaveDatabase` (Room) into the DI container and `ChatViewModel` to persist E2EE history.
  - Introduce `deliveryStatus` field ("sent", "delivered", "read") to local database entities.
  - Extend the WebSocket signaling system to handle:
    - `"TYPING_STATUS"`: Real-time typing indicators with a 3-second auto-expire.
    - `"READ_RECEIPT"`: Real-time read verification.
  - UI Styling: Render a beautiful typing bubble animation and custom accent checkmark symbols (`✓` and `✓✓`) styled in our "Minimalist Blush" palette.

---

## Phase 17: Secure Voice Memos & Disappearing Messages [COMPLETED]
* **Goal**: Expand rich media communication with secure voice messaging and temporal confidentiality.
* **Architecture**:
  - Integrate a secure audio recording/playback framework using Android's `MediaRecorder` & `MediaPlayer` (supporting API 24+ gracefully).
  - Pipe raw voice memo bytes strictly through `EncryptedFileManager` without leaking cleartext to storage.
  - Add Disappearing Messages: Define a state machine with a countdown timer (e.g., 5s, 1m, 1hr) that triggers automatic DB deletion and filesystem shredding upon expiry.
  - Design sliding haptic message reactions and swipe-to-dismiss UI controls.

---

## Phase 18: Fossify-Grade Vault & Premium Multimedia [COMPLETED]
* **Goal**: Build a premium secure media vault featuring video playback and metadata privacy.
* **Architecture**:
  - Construct a hardware-accelerated video player inside the vault using custom `Media3` bindings.
  - Implement a dynamic horizontal pager with pinch-to-zoom (using Compose `pointerInput` transform gestures).
  - Add custom folder/album organization layers in local DB.
  - Build a privacy filter that automatically strips EXIF metadata (JPEG tags) on visual media ingestion before writing to the vault.

---

## Phase 19: Premium VoIP Engine [COMPLETED]
* **Goal**: Implement premium audio/video calling with advanced system-level integration.
* **Architecture**:
  - Implement modern Android 12+ Auto-Enter Picture-in-Picture (PiP) support for active video calls, automatically hiding all controls in PiP mode.
  - Implement dynamic screen sharing using WebRTC's `ScreenCapturerAndroid` via the `MediaProjection` API, coordinated by a secure foreground service (`ScreenShareService`).
  - Configure WebRTC's `JavaAudioDeviceModule` for native hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS).
  - Build proximity screen-off cheek lock (`PROXIMITY_SCREEN_OFF_WAKE_LOCK`) combined with modern available communication devices routing to route audio between Speaker, Bluetooth, and Earpiece.

---

## Phase 20: Intimacy & Interactive Suite [COMPLETED]
* **Goal**: Enrich shared moments with games and interactive tools.
* **Architecture**:
  - Build encrypted Daily Letters capsule (Room version 6 auto-migration database security) decrypted strictly in-memory during flow collections.
  - Build real-time Profile Status Cards synchronizing active presence statuses, mood emojis, battery levels, and "Now Listening" media titles.
  - Build collaborative live Drawing Canvas using 80ms WebSocket Stroke Batching to prevent network flooding, drawing in Pink (local) and Purple (partner).
  - Orchestrate synchronized, secure mini-games: 3D tumbling Dice with dynamic haptics (`HapticFeedbackConstants.CLOCK_TICK`) and intimate Truth or Dare card swipers.
  - Build View-Once scratch reveal overlay utilizing offscreen strategy (`CompositingStrategy.Offscreen`) to prevent render holes, heavy blurs via `RenderEffect.createBlurEffect`, and a Touch-Activated 5-second self-destruct timer.
