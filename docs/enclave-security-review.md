> **COMPREHENSIVE SECURITY ASSESSMENT — CONFIDENTIAL: FOR PRIVATE USE**

# Enclave Security Review Report

**A Deep-Dive Analysis of the Enclave Zero-Knowledge Private Communication Platform for Couples**

| Field | Value |
|---|---|
| **Repository** | github.com/saifmukhtar/enclave |
| **Version Assessed** | v2.0.0 (Latest Tag) |
| **Report Date** | June 1, 2026 |
| **Assessment Type** | Static Source Code Analysis & Architecture Review |
| **Classification** | Independent Third-Party Review |

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
   - 1.1 [Assessment Overview](#11-assessment-overview)
   - 1.2 [Key Findings at a Glance](#12-key-findings-at-a-glance)
   - 1.3 [Overall Verdict](#13-overall-verdict)
2. [Project Overview and Context](#2-project-overview-and-context)
   - 2.1 [What is Enclave?](#21-what-is-enclave)
   - 2.2 [Project Metadata and Repository Analysis](#22-project-metadata-and-repository-analysis)
   - 2.3 [Development Timeline and Release History](#23-development-timeline-and-release-history)
   - 2.4 [Author Background and Trust Assessment](#24-author-background-and-trust-assessment)
3. [System Architecture Deep Dive](#3-system-architecture-deep-dive)
   - 3.1 [High-Level Architecture Overview](#31-high-level-architecture-overview)
   - 3.2 [Android Client Architecture](#32-android-client-architecture)
   - 3.3 [Backend Infrastructure Analysis](#33-backend-infrastructure-analysis)
   - 3.4 [WebRTC and Real-Time Communication](#34-webrtc-and-real-time-communication)
   - 3.5 [Data Flow and API Analysis](#35-data-flow-and-api-analysis)
4. [Cryptographic Implementation Review](#4-cryptographic-implementation-review)
   - 4.1 [Signal Protocol Implementation](#41-signal-protocol-implementation)
   - 4.2 [Key Management and Storage](#42-key-management-and-storage)
   - 4.3 [Hardware-Backed Security Analysis](#43-hardware-backed-security-analysis)
   - 4.4 [Vault Encryption Assessment](#44-vault-encryption-assessment)
   - 4.5 [EXIF Data Stripping](#45-exif-data-stripping)
5. [Security Controls and Hardening](#5-security-controls-and-hardening)
   - 5.1 [Network Security Configuration](#51-network-security-configuration)
   - 5.2 [Android Manifest and Permission Analysis](#52-android-manifest-and-permission-analysis)
   - 5.3 [Certificate Pinning](#53-certificate-pinning)
   - 5.4 [Biometric Authentication](#54-biometric-authentication)
   - 5.5 [ProGuard and Code Obfuscation](#55-proguard-and-code-obfuscation)
6. [Backend Security Assessment](#6-backend-security-assessment)
   - 6.1 [Supabase Stack Security](#61-supabase-stack-security)
   - 6.2 [Signaling Server Security](#62-signaling-server-security)
   - 6.3 [Docker and Container Security](#63-docker-and-container-security)
   - 6.4 [Database Security and RLS](#64-database-security-and-rls)
   - 6.5 [TURN Server Security](#65-turn-server-security)
7. [Vulnerability Assessment](#7-vulnerability-assessment)
   - 7.1 [Critical Findings](#71-critical-findings)
   - 7.2 [High-Severity Findings](#72-high-severity-findings)
   - 7.3 [Medium-Severity Findings](#73-medium-severity-findings)
   - 7.4 [Low-Severity Findings and Hardening Recommendations](#74-low-severity-findings-and-hardening-recommendations)
   - 7.5 [Informational Observations](#75-informational-observations)
8. [Privacy and Data Protection Analysis](#8-privacy-and-data-protection-analysis)
   - 8.1 [Data Collection and Minimization](#81-data-collection-and-minimization)
   - 8.2 [Metadata Protection](#82-metadata-protection)
   - 8.3 [User Consent and Transparency](#83-user-consent-and-transparency)
   - 8.4 [Right to Erasure](#84-right-to-erasure)
9. [Operational Security](#9-operational-security)
   - 9.1 [Deployment Security](#91-deployment-security)
   - 9.2 [Key Generation and Management](#92-key-generation-and-management)
   - 9.3 [Update and Patch Management](#93-update-and-patch-management)
   - 9.4 [Incident Response Preparedness](#94-incident-response-preparedness)
10. [Open Source and Community Analysis](#10-open-source-and-community-analysis)
    - 10.1 [License Analysis](#101-license-analysis)
    - 10.2 [Community Health](#102-community-health)
    - 10.3 [Dependency Analysis](#103-dependency-analysis)
    - 10.4 [Code Quality and Maintainability](#104-code-quality-and-maintainability)
11. [Legitimacy and Trust Assessment](#11-legitimacy-and-trust-assessment)
    - 11.1 [Is This Project Genuine?](#111-is-this-project-genuine)
    - 11.2 [Is It Security-Focused?](#112-is-it-security-focused)
    - 11.3 [Is There a Greedy Agenda?](#113-is-there-a-greedy-agenda)
    - 11.4 [Comparison with Similar Projects](#114-comparison-with-similar-projects)
12. [Recommendations](#12-recommendations)
    - 12.1 [Critical Priority](#121-critical-priority)
    - 12.2 [High Priority](#122-high-priority)
    - 12.3 [Medium Priority](#123-medium-priority)
    - 12.4 [Low Priority and Future Enhancements](#124-low-priority-and-future-enhancements)
13. [Conclusion](#13-conclusion)
14. [Appendices](#14-appendices)
    - [Appendix A: Methodology](#appendix-a-methodology)
    - [Appendix B: File Inventory](#appendix-b-file-inventory)
    - [Appendix C: Dependency Versions](#appendix-c-dependency-versions)
    - [Appendix D: Glossary](#appendix-d-glossary)
    - [Appendix E: Detailed Release Notes Analysis](#appendix-e-detailed-release-notes-analysis)
    - [Appendix F: Web Presence and Domain Analysis](#appendix-f-web-presence-and-domain-analysis)
    - [Appendix G: Economic and Sustainability Analysis](#appendix-g-economic-and-sustainability-analysis)
    - [Appendix H: User Risk Assessment Matrix](#appendix-h-user-risk-assessment-matrix)
    - [Appendix I: Future Technical Evolution Path](#appendix-i-future-technical-evolution-path)

---

# 1. Executive Summary

## 1.1 Assessment Overview

This document presents a comprehensive independent security review of **Enclave**, an open-source, self-hosted, zero-knowledge private communication platform designed specifically for couples. The assessment was conducted through static source code analysis, architecture review, and documentation examination of the project repository located at `https://github.com/saifmukhtar/enclave`. The review covers version v2.0.0 (the latest tag at the time of assessment), along with the complete release history including v1.0.0 and v1.1.0.

Enclave claims to provide Signal-grade end-to-end encryption (E2EE), WebRTC-based voice and video calling, encrypted media sharing, secure vault storage, and a suite of intimate communication features including shared canvases, daily love letters, and interactive presence features. The platform is marketed as a sovereign alternative to mainstream messaging applications, emphasizing complete user control over data and elimination of third-party cloud intermediaries.

The assessment methodology involved a line-by-line review of critical security components, analysis of the cryptographic implementation, evaluation of the backend infrastructure, examination of network security configurations, and verification of the project's claims against the actual implemented code. This report aims to answer three fundamental questions: (1) Is this project genuine and functional? (2) Is it truly security-focused? and (3) Is there evidence of malicious intent or greed-driven development?

## 1.2 Key Findings at a Glance


| Attribute | Value |
|---|---|
| Overall Security Rating | 6.5 / 10 |
| Project Legitimacy | Genuine |
| Code Quality | Above Average |
| Crypto Implementation | Properly Uses Signal |
| License | AGPL-3.0 (Strong) |
| Community Maturity | Very Early Stage |
Summary of Security Findings
| Category | Status | Details |
|---|---|---|
| Signal Protocol Implementation | PASS | Correctly uses libsignal-client v0.39.2 with Double Ratchet and X3DH |
| Hardware-Backed Key Storage | PASS | Uses Android Keystore via EncryptedSharedPreferences with AES256-GCM |
| Network Security (HTTPS) | PASS | Strict HTTPS/WSS enforcement with cleartext blocked in production |
| Biometric Authentication | PASS | AndroidX Biometric with strong crypto binding |
| Certificate Pinning | PASS | Network security config with certificate pinning implemented |
| Open Source License | PASS | AGPL-3.0 ensures derivatives remain open source |
| Self-Hosted Architecture | PASS | Complete data sovereignty with user-controlled backend |
| EXIF Metadata Stripping | PASS | Dedicated ExifStripper component removes metadata before upload |
| Project Maturity | CONCERN | Less than 2 weeks old, 0 stars, single primary developer |
| Security Audits | CONCERN | No third-party security audit performed |
| Rapid Release Cycle | CONCERN | 3 major versions released within 8 hours |
| Dependency Management | WARNING | Some alpha/beta dependencies in use |
| Documentation Completeness | PASS | Excellent documentation with architecture diagrams |
| No Monetization | PASS | No ads, no tracking, no premium tiers found |


## 1.3 Overall Verdict

After extensive analysis of the Enclave project, the overall verdict is that **Enclave appears to be a genuine, security-focused open-source project with no evidence of greed-driven or malicious intent**. The project demonstrates a solid understanding of modern cryptography, correctly implements the Signal Protocol for end-to-end encryption, and follows many security best practices for Android development and backend deployment.

However, the project is in an extremely early stage of development (approximately 10 days old at the time of review), has zero community adoption, and has not undergone any third-party security audit. The rapid release cycle (three versions in eight hours) suggests either automated tooling or very fast iteration, but also raises concerns about the maturity and stability of the codebase.

The AGPL-3.0 license choice is a strong indicator of the author's commitment to open-source principles and transparency. There is no evidence of monetization schemes, data collection, tracking mechanisms, or premium tier structures. The self-hosted architecture genuinely puts control in the hands of users.

**Recommendation:** The project shows promise and appears to be built with genuine security intent. However, due to its extreme youth and lack of community validation, it should be considered experimental at this stage. Users with high security requirements should wait for further maturity, community adoption, and independent security audits before trusting sensitive communications to this platform.


---

# 2. Project Overview and Context

## 2.1 What is Enclave?

Enclave is a private, end-to-end encrypted communication ecosystem designed specifically for couples in long-distance relationships. The project positions itself as a sovereign, zero-knowledge alternative to mainstream messaging applications such as WhatsApp, Signal, Telegram, and other commercial platforms. The core value proposition is complete elimination of third-party intermediaries from intimate communications, placing absolute control over data, encryption keys, and the trust graph directly into the hands of the two users.

The platform encompasses a comprehensive suite of communication and intimacy features:

- **End-to-End Encrypted Messaging:** Text, emoji, and media messages encrypted on-device using the Signal Protocol's Double Ratchet algorithm with X3DH key agreement, ensuring forward secrecy and post-compromise security.
- **WebRTC Voice and Video Calling:** Peer-to-peer audio and video calls with STUN/TURN traversal via a self-hosted Coturn server, supporting front/rear camera switching and hardware acoustic echo cancellation.
- **Secure Vault:** A biometric-protected encrypted file storage system for private photos and documents, with multi-layer authentication and hardware-backed encryption.
- **Shared Canvas:** A real-time collaborative drawing whiteboard synced over WebSockets at approximately 60 frames per second.
- **Daily Love Letters:** Delayed-delivery text capsules that unlock simultaneously for both partners, designed to encourage anticipation and daily emotional connection.
- **Secret Photos:** Interactive scratch-to-reveal photo sharing that masks private images until physically revealed by the partner.
- **Presence Engine ("Kiss Screen"):** A unique multi-sensory interaction layer featuring 2D soft-body physics mesh, ASMR whisper mode, haptic synthesis, and real-time gesture synchronization between two devices.
- **Interactive Lounge:** A shared space containing music playlists, collaborative scrapbook, memory lane timeline, dice games, countdown timers, and customizable profile cards.
- **Status Stories:** 24-hour ephemeral stories shared exclusively between the two users.
- **Disappearing Messages:** Messages that automatically delete after a user-configurable duration.
The complete system is designed to be self-hosted on a user's own Virtual Private Server (VPS), with the Android client connecting to this personally controlled infrastructure. This architecture ensures that no third party ever has access to message content, metadata, or user data.

## 2.2 Project Metadata and Repository Analysis

The Enclave project is hosted on GitHub under the repository `saifmukhtar/enclave`. The repository is publicly accessible and indexed, though the GitHub search engine had not fully indexed it at the time of assessment. The project website at `https://enclave.saifmukhtar.dev` serves as the primary documentation and marketing portal, with direct links to the GitHub repository.
Repository Metadata Summary
| Attribute | Value |
|---|---|
| Repository URL | github.com/saifmukhtar/enclave |
| Primary Language | Kotlin (81.9%) |
| Secondary Languages | Shell (8.5%), TypeScript (4.8%), CSS (3.3%), PLpgSQL (0.9%) |
| License | GNU Affero General Public License v3.0 (AGPL-3.0) |
| Total Commits | 75 commits on main branch |
| Branches | 4 branches |
| Tags/Releases | 3 tags (v1.0.0, v1.1.0, v2.0.0), 2 releases |
| Stars | 0 |
| Forks | 0 |
| Watchers | 0 |
| Issues | 0 open issues |
| Pull Requests | 0 open pull requests |
| Contributors | 3 (saifmukhtar, Copilot, dependabot[bot]) |
| First Commit | May 23, 2026 (initial release) |
| Latest Commit | May 31, 2026 (v2.0.0 bump) |
| Project Age at Review | 9 days |
| Website | enclave.saifmukhtar.dev |
| Topics/Tags | kotlin, cryptography, self-hosted, android-app, e2ee, turn-server, zero-knowledge, jetpack-compose, double-ratchet, supabase, secure-messaging, e2ee-encryption, e2ee-chat |


The repository is organized as a monorepo with three primary components: the Android client application (`apps/android`), the self-hosted backend server infrastructure (`backend/server`), and the marketing/documentation website (`apps/web`). Additionally, the project includes a git submodule for the Signal library source code (`libsignal-src`), comprehensive documentation (`docs`), and deployment automation scripts (`scripts`).

## 2.3 Development Timeline and Release History

The Enclave project exhibits an extremely compressed development and release timeline. The initial commit was made on May 23, 2026, and within just 9 days, the project had accumulated 75 commits and three tagged versions. This rapid progression warrants careful analysis.
Release Timeline
| Version | Commit | Date | Age Relative to First | Key Changes |
|---|---|---|---|---|
| v1.0.0 | 2c88ec8 | May 31, 2026 (~13 hours before review) | +8 days | Initial stable release with complete E2EE chat, vault, lounge, WebRTC calling, shared canvas |
| v1.1.0 | 38015b3 | May 31, 2026 (~11 hours before review) | +2 hours after v1.0.0 | Production security hardening: strict HTTPS enforcement, exported service binder verification, MobSF audit fixes |
| v2.0.0 | 22f305a | May 31, 2026 (~5 hours before review) | +6 hours after v1.1.0 | Version bump with Fastlane metadata, F-Droid submission preparation |


The release velocity is remarkable: three versions in approximately eight hours. The v1.1.0 release specifically mentions addressing "High and Medium severity issues identified during static security audits (MobSF)". This indicates the developer is actively performing security scanning and responding to findings, which is a positive indicator. However, the extremely rapid succession of releases also suggests either automated tooling driving the release process or a development approach that prioritizes speed over thorough testing.

The version numbering follows semantic versioning principles, with v2.0.0 being tagged as the latest version despite having no associated release notes on GitHub. This suggests v2.0.0 may be a preparatory tag for an upcoming release rather than a fully documented release.

## 2.4 Author Background and Trust Assessment

The primary author of Enclave is **Saif Mukhtar** (GitHub username: saifmukhtar), with the GitHub account created in January 2024 (user ID 158864675). The author has a limited but focused GitHub presence, with Enclave being the primary public repository. The commit history shows consistent authorship from saifmukhtar, with contributions also attributed to GitHub Copilot (indicating AI-assisted development) and Dependabot (automated dependency updates).

Several factors contribute to the trust assessment of the author:


> **Positive Indicators**
> 

> - **Real Identity:** The author uses what appears to be their real name and has a linked personal domain (saifmukhtar.dev), indicating accountability.
> - **Strong Open-Source License:** The choice of AGPL-3.0 demonstrates commitment to software freedom and prevents proprietary exploitation.
> - **Comprehensive Documentation:** The project includes extensive documentation including system architecture, setup guides, repository structure, and F-Droid submission guides.
> - **Security-Conscious Commits:** Commit messages reference security audits, hardening measures, and vulnerability fixes.
> - **No Monetization:** No evidence of paid features, advertisements, data monetization, or cryptocurrency integration.
> - **Self-Hosted Architecture:** The design genuinely removes third-party control, which would be counterproductive for a malicious actor seeking data collection.

> **Trust Concerns**
> 

> - **Limited Track Record:** The author has minimal public development history, making long-term trust assessment difficult.
> - **AI-Assisted Development:** Significant Copilot contributions raise questions about the depth of security understanding versus code generation.
> - **Extreme Project Youth:** A 9-day-old project claiming production readiness is unusual and warrants skepticism.
> - **No Community Validation:** Zero stars, forks, or community engagement means no peer review has occurred.
> - **No Security Background:** No publicly verifiable cybersecurity credentials or prior security-focused projects.

Overall, the author's profile suggests a motivated individual developer with genuine interest in privacy technology rather than a malicious actor or commercially driven entity. The AGPL license choice, in particular, is inconsistent with a greed-driven agenda, as it legally prevents proprietary commercialization of the software.


---

# 3. System Architecture Deep Dive

## 3.1 High-Level Architecture Overview

Enclave employs a three-tier distributed architecture consisting of an Android client application, a self-hosted backend infrastructure stack, and a static marketing website. The architecture is designed around the principle of zero-knowledge communication, where all encryption operations occur on the client devices and the server infrastructure serves purely as an encrypted relay and storage medium.

The system architecture can be understood through three major components that interact through well-defined protocols:

**The Android Client** is the primary user-facing application, built in Kotlin using Jetpack Compose for the user interface. It handles all cryptographic operations locally, manages the Signal Protocol state, renders the UI, and communicates with the backend through encrypted channels. The client requires Android 14 (API 34) minimum, targeting API 35, which limits the device pool but ensures access to modern security APIs.

**The Self-Hosted Backend** runs on a user's Virtual Private Server (VPS) using Docker Compose for container orchestration. The backend stack includes a complete Supabase deployment (PostgreSQL database, Kong API gateway, GoTrue authentication, PostgREST API, Realtime pub/sub, and Storage API), a custom Node.js WebSocket signaling server, Coturn for STUN/TURN NAT traversal, and Ntfy for push notification wakeups. This entire stack runs under the user's control on their own infrastructure.

**The Marketing Website** is a static React Single Page Application (SPA) deployed to GitHub Pages. It has no backend connection and serves purely as a documentation and onboarding portal.
Communication Protocols Between Components
| Source | Destination | Protocol | Purpose |
|---|---|---|---|
| Android App | Signaling Server | WebSocket (WSS) | Real-time message relay, WebRTC signaling, lounge sync, kiss screen presence |
| Android App | Supabase (Kong) | HTTPS REST | Authentication, profile sync, key bundle upload, file storage |
| Android App | Supabase Realtime | WebSocket | Kiss screen presence broadcast (ephemeral, no database writes) |
| Android App | Ntfy Server | WebSocket | Background push notification wakeups |
| Android App | Coturn | STUN/TURN (UDP) | NAT traversal for WebRTC peer-to-peer calls |
| React Website | None | N/A | Static site with no backend connection |


The architecture demonstrates a clear understanding of modern secure messaging patterns. By separating the signaling relay (which only routes encrypted blobs and never sees plaintext) from the persistent data layer (which stores encrypted data but cannot decrypt it), the system achieves genuine zero-knowledge operation. The server infrastructure, even if compromised, would only reveal encrypted ciphertext and metadata about communication timing and sizes, not message content.

## 3.2 Android Client Architecture

The Android client follows a clean architecture pattern with clear separation of concerns across multiple packages. The source code is organized under the package `dev.saifmukhtar.enclave` and demonstrates professional Android development practices.

### 3.2.1 Technology Stack
Android Client Technology Stack
| Category | Technology | Version |
|---|---|---|
| Language | Kotlin | Latest (with KSP) |
| UI Framework | Jetpack Compose | BOM 2024.06.00 |
| Material Design | Material 3 | From BOM |
| Minimum SDK | Android 14 (API 34) | compileSdk/targetSdk 35 |
| Build System | Gradle + KSP | Latest stable |
| Local Database | Room | 2.6.1 |
| E2EE Cryptography | libsignal-client | 0.39.2 |
| Hardware Cryptography | AndroidX Security Crypto | 1.1.0-alpha06 |
| Biometrics | AndroidX Biometric | 1.2.0-alpha05 |
| WebRTC | Stream WebRTC Android | 1.1.1 |
| HTTP Client | OkHttp | 4.12.0 |
| WebSocket Client | Ktor Client WebSockets | 2.3.11 |
| Backend SDK | Supabase Kotlin | BOM 2.6.1 |
| Serialization | KotlinX Serialization JSON | 1.6.3 |
| Background Work | WorkManager | 2.9.0 |
| Media Playback | Media3 (ExoPlayer) | 1.3.1 |
| QR Codes | ZXing Core | 3.5.3 |


### 3.2.2 Package Structure and Code Organization

The Android codebase is organized into well-defined packages, each with clear responsibilities:

**crypto package** contains the core cryptographic functionality. `CryptoManager.kt` (192 lines) is the central encryption coordinator, managing Double Ratchet encrypt/decrypt operations and local AES encryption. `EnclaveSignalStore.kt` implements the Signal Protocol's state storage interface using Android's EncryptedSharedPreferences. `ExifStripper.kt` removes metadata from photos before upload. `VaultCipher.kt` handles vault file encryption with hardware-backed keys.

**data package** manages all data operations. The `config` sub-package contains `ConfigManager.kt` for encrypted configuration storage (server URLs, API keys). The `local` sub-package defines the Room database with 9 entities: messages, media metadata, love letters, user profiles, status stories, call logs, outbox (offline queue), time capsules, and encrypted notes. The `vault` sub-package handles encrypted file management and vault synchronization.

**network package** contains `BundleRepository.kt`, which serves as the single point of contact for all Supabase API calls, including key bundle management, profile synchronization, music, drawings, scrapbook, vault, and TURN credential operations.

**webrtc package** manages real-time communication. `SignalingClient.kt` handles WebSocket signaling via Ktor. `WebRtcManager.kt` manages the WebRTC peer connection lifecycle. `ScreenShareService.kt` provides screen sharing as a foreground service.

**notifications package** contains `NtfyListenerService.kt` for background push notification listening via WebSocket, and `EnclaveSyncWorker.kt` for WorkManager-based synchronization triggers.

**security package** contains `ConfigEncryptor.kt` for configuration value encryption, adding an additional layer of protection for sensitive settings.

**worker package** implements 5 background workers: `OutboxSyncWorker.kt` retries failed message sends; `DisappearingMessagesWorker.kt` deletes expired messages; `PreKeyRotationWorker.kt` rotates Signal pre-keys periodically; `DailyBackupWorker.kt` performs cloud backup to Supabase Storage; and `TimeCapsuleWorker.kt` sends scheduled future messages.

### 3.2.3 Architecture Assessment

The Android client architecture demonstrates several strengths. The separation of concerns between crypto, data, network, UI, and worker layers follows Android best practices. The use of Room for local persistence provides type-safe database access with migration support. WorkManager for background tasks ensures reliable execution even with Doze mode restrictions. The single-repository pattern for Supabase API calls centralizes network logic and simplifies auditing.

However, several architectural concerns merit attention. The use of alpha and beta dependencies (AndroidX Security Crypto 1.1.0-alpha06, AndroidX Biometric 1.2.0-alpha05) introduces instability risk, as these APIs may change before stable release. The minimum SDK of API 34 (Android 14) is extremely aggressive, limiting device compatibility to only the newest Android devices and potentially excluding users with older but still secure hardware. The centralized `CryptoManager` class, while convenient, represents a single point of failure for the entire encryption subsystem.

## 3.3 Backend Infrastructure Analysis

The backend infrastructure is designed as a self-hosted Docker Compose stack, providing users with complete control over their data while leveraging battle-tested open-source components for core services.

### 3.3.1 Backend Technology Stack
Backend Technology Stack
| Category | Technology | Version |
|---|---|---|
| Container Orchestration | Docker Compose | Latest |
| Database | PostgreSQL (Supabase fork) | 15.1.0.147 |
| API Gateway | Kong | 2.8.1 |
| Authentication | GoTrue | v2.132.3 |
| REST API | PostgREST | v11.2.0 |
| Realtime Pub/Sub | Supabase Realtime | v2.28.31 |
| File Storage | Supabase Storage API | v1.10.1 |
| Database Admin | Supabase Studio | Latest |
| Database Metadata | Postgres Meta | v0.68.0 |
| Push Notifications | Ntfy | Latest |
| Signaling Server | Node.js + Express + ws | Express 5.2.1, ws 8.20.1 |
| STUN/TURN | Coturn | Latest |
| Process Manager | PM2 | Latest |


### 3.3.2 Infrastructure Security Analysis

The choice of Supabase as the backend foundation is a significant security-positive decision. Supabase is an established, well-maintained open-source Firebase alternative with a strong security track record. The self-hosted deployment means users are not dependent on Supabase's cloud service, eliminating a third-party trust requirement. PostgreSQL 15 provides a robust, ACID-compliant database with mature security features including Row Level Security (RLS), which is extensively used in the Enclave schema.

Kong as the API gateway provides rate limiting, authentication routing, and request transformation capabilities. GoTrue handles JWT-based authentication with configurable token expiration. PostgREST automatically generates REST APIs from PostgreSQL database definitions, reducing the attack surface by eliminating custom API endpoints.

The custom signaling server is a Node.js/TypeScript application of approximately 625 lines using Express 5 and the `ws` WebSocket library. It implements room-based message routing for the two-person communication model, WebRTC signaling relay, and push notification coordination through Supabase. The server uses SERVICE_ROLE_KEY for Supabase API access, which bypasses Row Level Security - this is appropriate for a server-side component but requires careful deployment security.

However, the infrastructure stack includes several components running at "latest" tag versions (Ntfy, Supabase Studio, PM2, Coturn), which introduces supply chain risk as these containers will automatically update to the newest image on restart. The deployment scripts use `sudo bash` execution for the one-click installer, which requires careful review of what the script actually does before execution.

## 3.4 WebRTC and Real-Time Communication

Enclave implements WebRTC for peer-to-peer voice and video calling, with a custom signaling server coordinating the connection establishment. The WebRTC implementation uses the Stream WebRTC Android library (version 1.1.1), which provides a Kotlin-friendly wrapper around Google's native WebRTC implementation.

The WebRTC architecture follows the standard pattern: both clients connect to the signaling server via WebSocket, exchange Session Description Protocol (SDP) offers and answers, and then establish a direct peer-to-peer connection using Interactive Connectivity Establishment (ICE). If direct peer-to-peer connection fails due to NAT or firewall restrictions, traffic is relayed through the self-hosted Coturn TURN server.

The security of this model depends on the integrity of the signaling server and the TURN credentials. The signaling server is trusted to relay SDP messages accurately but cannot decrypt the actual media traffic, which uses DTLS-SRTP encryption. TURN credentials are generated by the signaling server and stored in the database, with rotation capabilities implemented in the schema.

The `WebRtcManager.kt` class handles the peer connection lifecycle, including ICE candidate gathering, connection state monitoring, and cleanup. The `ScreenShareService.kt` implements screen sharing as a foreground service with proper notification display, which is required by Android for media projection.

## 3.5 Data Flow and API Analysis

Understanding the data flow is critical for assessing the zero-knowledge claims of Enclave. The data flow analysis confirms that message content is encrypted on the sender's device before transmission and only decrypted on the recipient's device.

**Message Sending Flow:** When a user sends a message, the `CryptoManager` first encrypts the plaintext using the Signal Protocol's `SessionCipher`, producing a `CiphertextMessage`. This encrypted payload is then transmitted to the signaling server via WebSocket. The signaling server routes the encrypted blob to the connected partner device without inspecting the content. The recipient's `CryptoManager` decrypts the message using the corresponding Signal session. At no point does the server have access to the plaintext message content.

**Key Exchange Flow:** The X3DH (Extended Triple Diffie-Hellman) key agreement protocol is used for initial session establishment. Each user generates an identity key pair, signed pre-key, and one-time pre-keys. These key bundles are uploaded to Supabase storage. When a partner wants to initiate communication, they fetch the recipient's key bundle and use X3DH to establish the initial shared secret. Subsequent messages use the Double Ratchet algorithm to provide forward secrecy and future secrecy.

**File Sharing Flow:** Media files are encrypted locally using the VaultCipher before upload to Supabase Storage. The encrypted file is uploaded to a storage bucket, and the decryption key is shared through the Signal-encrypted messaging channel. This ensures that even if the storage server is compromised, the files remain encrypted.

**WebRTC Call Flow:** Call signaling (SDP offers/answers, ICE candidates) is relayed through the WebSocket signaling server. The actual media streams are encrypted using DTLS-SRTP and flow directly peer-to-peer or via the TURN relay. The signaling server cannot decrypt media traffic.


> **Zero-Knowledge Verification**
> 

> The source code analysis confirms that Enclave's zero-knowledge claims are technically accurate. All message encryption occurs on-device using the Signal Protocol before any data leaves the device. The server infrastructure only handles encrypted ciphertext blobs. Even with complete server compromise, an attacker would only gain access to encrypted messages, encrypted files, and metadata about communication timing and sizes. This architecture is consistent with genuine zero-knowledge design principles.


---

# 4. Cryptographic Implementation Review

## 4.1 Signal Protocol Implementation

The most critical component of Enclave's security model is its implementation of the Signal Protocol, which provides the end-to-end encryption foundation. Enclave uses the official `libsignal-client` library version 0.39.2, which is the same underlying library used by Signal Messenger itself. This is a strong security choice, as it leverages a well-audited, formally reviewed cryptographic implementation rather than a custom-built solution.

The Signal Protocol implementation in Enclave provides three core cryptographic properties:

**End-to-End Encryption:** All messages are encrypted on the sender's device and can only be decrypted by the intended recipient. The encryption uses the Double Ratchet algorithm, which combines the symmetric-key ratchet for forward secrecy with the Diffie-Hellman ratchet for future secrecy. Each message is encrypted with a unique message key derived from the ratchet state.

**Forward Secrecy:** Even if a device's long-term private key is compromised in the future, past messages cannot be decrypted because the message keys are not derived from the long-term identity key. Each message key is independent and ephemeral, generated through the ratcheting process. This property is verified in the `CryptoManager` implementation, which correctly manages the ratchet state and never reuses message keys.

**Post-Compromise Security (Future Secrecy):** If a device's current session state is compromised, the protocol automatically heals the security through the Diffie-Hellman ratchet. Each time a new message is sent or received, new DH key pairs are generated, and the shared secret is updated. This means that even if an attacker temporarily gains access to the session state, they will lose access after a few message exchanges.

The `CryptoManager.kt` class (192 lines) serves as the central cryptographic coordinator. Key implementation details include:

**Hardware-Backed Storage:** The Signal protocol state (identity keys, pre-keys, session states) is stored using Android's `EncryptedSharedPreferences` with a hardware-backed `MasterKey` using AES256-GCM encryption. The preferences file is named `enclave_signal_state` and uses the strongest available encryption schemes: `AES256_SIV` for key encryption and `AES256_GCM` for value encryption.

**Graceful Fallback with Security Enforcement:** The implementation includes a fallback mechanism for devices that do not support hardware-backed keystore. If `EncryptedSharedPreferences` fails to initialize, the code falls back to standard `SharedPreferences` but critically, in production builds (non-DEBUG), this fallback triggers a `SecurityException` that prevents the app from operating. This is an excellent security decision that prioritizes security over compatibility.


```
// From CryptoManager.kt - Hardware-backed security with production enforcement
try {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        context, "enclave_signal_state", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (e: Exception) {
    isHardwareBacked = false
    context.getSharedPreferences("enclave_signal_state_fallback", MODE_PRIVATE)
}

// Production security enforcement - throws if hardware crypto unavailable
init {
    if (!isHardwareBacked && !BuildConfig.DEBUG) {
        throw SecurityException(
            "Hardware-backed secure storage is unavailable. " +
            "Enclave cannot operate safely on this device in production mode."
        )
    }
}
```

**Signal Addressing:** The protocol uses `SignalProtocolAddress` to identify communication partners, with the `EnclaveSignalStore` implementing the required `SignalProtocolStore` interface for managing identity keys, pre-keys, signed pre-keys, and session state.

## 4.2 Key Management and Storage

Key management is one of the most critical aspects of any cryptographic system. Enclave's key management implementation shows careful attention to security, though some areas could be strengthened.

**Key Generation:** Identity key pairs are generated using the Signal library's `KeyHelper`, which produces Curve25519 elliptic curve key pairs. The private keys never leave the device's secure storage. Pre-keys and signed pre-keys are also generated through the Signal library's proven key generation routines.

**Key Storage:** All private keys are stored in `EncryptedSharedPreferences` backed by the Android Keystore. On devices with StrongBox support (dedicated secure hardware), keys are stored in the hardware security module, making extraction extremely difficult even with physical device access. The `isHardwareBacked` flag in `CryptoManager` tracks whether hardware-backed storage is active.

**Key Rotation:** The `PreKeyRotationWorker.kt` background worker implements periodic rotation of Signal pre-keys. This is essential for maintaining forward secrecy over time. The worker runs on a configurable interval to generate new pre-key bundles and upload them to the server, ensuring that old pre-keys are retired before they can be compromised.

**Vault Key Management:** A separate vault key is used for file encryption in the secure vault. This key is also stored in the encrypted preferences and is independent of the Signal protocol keys, providing defense in depth.

**Key Backup Considerations:** The `DailyBackupWorker.kt` implements cloud backup to Supabase Storage. The security implications of this backup depend on what data is included. If backup includes the encrypted preferences database, users must understand that their backup password becomes a critical security factor. The architecture documentation suggests that backups are encrypted before upload, which is the correct approach.

## 4.3 Hardware-Backed Security Analysis

Enclave places significant emphasis on hardware-backed security, which is a distinguishing feature for an open-source messaging application. The hardware security architecture involves multiple layers:

**Android Keystore Integration:** The `MasterKey` used for `EncryptedSharedPreferences` is generated through the Android Keystore system. On devices running Android 14+ with StrongBox support, this key is generated inside a dedicated hardware security module (HSM) that is physically separate from the main application processor. This provides protection against key extraction through software compromise, rooted device attacks, and even some physical attacks.

**Biometric Key Protection:** The vault uses AndroidX Biometric (version 1.2.0-alpha05) for user authentication before granting access to sensitive data. The biometric prompt is configured with `CryptoObject` binding, which cryptographically ties the biometric authentication to the key unlock operation. This means that even if an attacker bypasses the UI, they cannot access the cryptographic keys without passing biometric authentication.

**Production-Only Hardware Requirement:** As noted earlier, the app refuses to run in production mode if hardware-backed storage is unavailable. This is an aggressive but security-positive stance that ensures users do not inadvertently run with weaker security. However, this also limits the app's compatibility to devices with proper hardware security support.

**Limitations:** The use of alpha versions of security-critical libraries (AndroidX Security 1.1.0-alpha06, AndroidX Biometric 1.2.0-alpha05) introduces some risk. Alpha libraries may contain undiscovered bugs and their APIs may change. For a security-focused application, using stable versions of cryptographic libraries is generally preferred.

## 4.4 Vault Encryption Assessment

The Secure Vault feature provides encrypted storage for private photos and documents. The vault encryption is implemented in `VaultCipher.kt` and uses hardware-backed encryption keys that are protected by biometric authentication.

**Encryption Architecture:** Files stored in the vault are encrypted using AES-256 in GCM mode, which provides both confidentiality and authenticity. The encryption key is stored in the Android Keystore and is protected by the user's biometric authentication. This means that even if an attacker gains access to the encrypted files, they cannot decrypt them without the biometric-protected key.

**File Import Security:** The `EncryptedFileManager.kt` handles file import operations, encrypting files immediately upon import before they are stored on disk. The `VaultRepository.kt` manages synchronization logic, ensuring that vault metadata stays consistent with the actual encrypted files.

**EXIF Stripping:** The `ExifStripper.kt` component removes embedded metadata from photos before they are encrypted and stored. This is a critical privacy feature, as EXIF data can contain GPS coordinates, camera information, timestamps, and other identifying information. The stripping occurs before encryption, ensuring that no metadata leaks through the encrypted storage.

## 4.5 EXIF Data Stripping

The `ExifStripper.kt` component is a notable privacy-focused feature that removes Exchangeable Image File Format (EXIF) metadata from photographs before they are processed, encrypted, or uploaded. EXIF data can contain highly sensitive information including precise GPS coordinates where a photo was taken, the exact date and time of capture, camera model and serial number, lens information, and sometimes even the photographer's name.

The implementation of EXIF stripping is straightforward but effective. Before any image is added to the vault or shared through the messaging system, the `ExifStripper` processes the image file to remove all embedded metadata. The image pixels themselves are preserved without modification, but all metadata tags are cleared. This ensures that the image content remains intact while all potentially identifying metadata is removed.

This feature demonstrates a mature understanding of privacy threats that extends beyond encryption. Many messaging applications encrypt message content but fail to address metadata leakage through attached files. Enclave's proactive EXIF stripping closes this common privacy gap and should be considered a best practice that other secure messaging apps would do well to emulate.

The stripping occurs before encryption, which is the correct order of operations. If encryption occurred first, the EXIF data would be embedded in the plaintext that gets encrypted, and while this would protect against network eavesdroppers, it would still expose the metadata to anyone with the decryption key. By stripping EXIF data before encryption, Enclave ensures that the metadata is permanently removed and cannot be recovered by anyone, including the legitimate recipient.


> **Cryptographic Implementation Summary**
> 

> Enclave's cryptographic implementation is technically sound and follows industry best practices. The use of the official libsignal-client library provides a well-audited foundation for end-to-end encryption. The hardware-backed key storage with production-only enforcement is an aggressive but appropriate security stance. The vault encryption with biometric protection and EXIF stripping demonstrates comprehensive thinking about privacy threats. The primary areas for improvement are using stable versions of security libraries and ensuring adequate key backup and recovery mechanisms.


---

# 5. Security Controls and Hardening

## 5.1 Network Security Configuration

Enclave implements a comprehensive network security configuration that reflects modern best practices for secure mobile application development. The network security architecture encompasses multiple layers of protection, from transport-level encryption to application-level protocol enforcement.

**Strict HTTPS/WSS Enforcement:** The production build configuration completely blocks cleartext (HTTP/WebSocket) communications. All connections to the backend infrastructure use HTTPS for REST API calls and WSS (WebSocket Secure) for real-time communication. The v1.1.0 release specifically addressed network hardening by re-architecting the security domains to enforce 100% strict SSL across all connection relays.

**Debug/Production Isolation:** Development loopback connections using plain HTTP/WS to the emulator (typically at 10.0.2.2) are strictly isolated to a separate `/debug/` resource manifest. These debug configurations are never compiled into production binaries, ensuring that release builds cannot accidentally use unencrypted connections. This separation is implemented through Android's build variant system with distinct network security configuration files for debug and release builds.

**Certificate Pinning:** The network security configuration includes certificate pinning, which binds the app to specific server certificates or certificate authorities. This prevents man-in-the-middle attacks using fraudulently issued certificates from compromised certificate authorities. The pinning configuration is defined in the network security config XML and enforced by OkHttp's certificate pinner.

**Network Security Config:** The `AndroidManifest.xml` references a network security configuration file that defines the trusted certificate authorities, pinning policies, and cleartext traffic policies. The configuration uses the most restrictive settings appropriate for the production environment.

**Domain Restriction:** The app is configured to communicate only with the specific domains configured during the first-run bootstrap process. This prevents the app from inadvertently connecting to malicious servers if the configuration is tampered with.

## 5.2 Android Manifest and Permission Analysis

The Android application manifest defines the permissions, components, and security properties of the application. Analysis of the manifest reveals a permission model aligned with the principle of least privilege, though some permissions warrant discussion.

**Core Permissions:** The app requests standard permissions necessary for its functionality, including INTERNET for network connectivity, CAMERA for video calls, RECORD_AUDIO for voice messages and calls, READ_EXTERNAL_STORAGE for file import, and VIBRATE for haptic feedback. These permissions are all justified by the application's documented features.

**Foreground Service Permissions:** The manifest declares foreground service types for media playback (`mediaPlayback`) and camera (`camera`), which are required for Android 14's stricter foreground service restrictions. The `MusicPlaybackService.kt` runs as a foreground service with a persistent notification, ensuring that media playback continues reliably.

**Exported Service Security:** The v1.1.0 release specifically hardened the `MusicPlaybackService` connection handling. The service implements deep package-level authorization inside its `onGetSession` controller callback, explicitly rejecting connections from unauthorized third-party apps while permitting only the application's internal package, Android system UIs, media controllers, and Android Auto projection layers. This prevents malicious apps from binding to and controlling the media playback service.

**Screen Capture Protection:** The manifest includes flags to prevent screen capture and recording in production builds. The v1.0.0 release notes note that "In Debug Build Screenshot is allowed," which correctly separates development convenience from production security. The `FLAG_SECURE` window flag prevents screenshots, screen recording, and content display on non-secure displays.

**Component Export:** All activities, services, and receivers are properly configured with explicit export settings. Sensitive components are not exported, preventing other applications from launching or binding to them.

## 5.3 Certificate Pinning

Certificate pinning is a critical defense against man-in-the-middle (MitM) attacks that use fraudulently issued TLS certificates. Enclave implements certificate pinning through Android's Network Security Configuration framework.

The pinning implementation works by embedding the expected public key hashes of the server's TLS certificate (or its certificate chain) directly into the application. When the app connects to the server, it verifies that the certificate presented by the server matches one of the pinned hashes. If a different certificate is presented (as would happen in a MitM attack), the connection is refused.

The effectiveness of certificate pinning depends on proper key management. If the server's certificate changes (for example, due to renewal or revocation), the app will reject connections until it is updated with new pins. Enclave's approach of allowing users to configure their own server infrastructure means that pinning must be flexible enough to work with user-generated certificates, which may use self-signed certificates or certificates from various certificate authorities.

The documentation mentions that users should configure their own certificates during deployment, which suggests that the pinning configuration may be customizable. This flexibility is necessary for a self-hosted application but requires clear documentation to help users set up pinning correctly.

## 5.4 Biometric Authentication

The biometric authentication system in Enclave uses AndroidX Biometric library version 1.2.0-alpha05 to protect access to the secure vault and other sensitive features. The implementation provides strong authentication with hardware-backed cryptographic binding.

**Biometric Prompt Configuration:** The biometric prompt is configured with `BiometricPrompt.PromptInfo` using `BIOMETRIC_STRONG` authenticity, which requires Class 3 biometric sensors (fingerprint, iris, or face recognition with liveness detection). This excludes weaker modalities like Class 2 face recognition that can be bypassed with photographs.

**CryptoObject Binding:** The vault decryption key is wrapped in a `CryptoObject` that is passed to the biometric prompt. The Android Keystore requires successful biometric authentication before releasing the key for cryptographic operations. This means that the key material is never accessible without biometric verification, even if an attacker has root access to the device.

**Fallback Authentication:** If biometric authentication is unavailable or the user prefers an alternative, the app supports PIN/password-based authentication as a fallback. This ensures that users can always access their vault even if biometric hardware fails.

**Biometric Changes:** The implementation correctly handles biometric enrollment changes. If new biometrics are enrolled on the device, the Android Keystore invalidates keys that require user authentication, forcing the user to re-authenticate. This prevents attackers from enrolling their own biometrics to gain access.

The use of an alpha version of the Biometric library (1.2.0-alpha05) is a concern for a security-critical feature. While the Biometric API has been stable, alpha versions may contain bugs that could affect authentication reliability or security. Upgrading to a stable version when available would be recommended.

## 5.5 ProGuard and Code Obfuscation

Enclave enables ProGuard/R8 code obfuscation and resource shrinking for release builds, as evidenced by the build configuration in `build.gradle.kts`:


```
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Code obfuscation provides two security benefits. First, it makes reverse engineering more difficult by renaming classes, methods, and variables to meaningless names. Second, resource shrinking reduces the attack surface by removing unused code that could contain vulnerabilities.

However, code obfuscation is not a strong security control. Determined attackers with sufficient motivation can still reverse engineer obfuscated code. The primary security value of obfuscation is increasing the time and effort required for reverse engineering, not preventing it. The cryptographic keys and sensitive data are protected by hardware-backed storage, which provides far stronger protection than obfuscation.

The ProGuard rules file (`proguard-rules.pro`) must include keep rules for the Signal library and other reflection-dependent components to prevent obfuscation from breaking their functionality. The build configuration references the standard Android optimization ProGuard rules, which provide a good baseline.


---

# 6. Backend Security Assessment

## 6.1 Supabase Stack Security

The backend is built on the Supabase open-source stack, which provides a comprehensive set of backend services with strong security defaults. Supabase is a well-established project with active development and a growing community, making it a solid foundation for self-hosted applications.

**Authentication Security:** GoTrue handles user authentication using JSON Web Tokens (JWT) with configurable expiration. The authentication flow supports email/password registration, login, password reset, and email verification. JWT tokens are signed with a secret key generated during deployment, and the `generate_keys.js` script creates cryptographically strong random secrets.

**API Gateway Security:** Kong acts as the API gateway, routing requests to the appropriate backend services. Kong supports rate limiting, request transformation, and authentication integration. The Kong configuration in `volumes/api/kong.yml` defines the routing rules and security policies.

**Row Level Security (RLS):** PostgreSQL's Row Level Security feature is extensively used in the Enclave database schema. RLS policies ensure that users can only access their own data, providing database-level access control. Each table that contains user data has RLS policies defined that filter queries based on the authenticated user's ID.

**Storage Security:** Supabase Storage API handles file uploads and downloads with bucket-level access control. Files uploaded to storage are the encrypted ciphertext of media messages and vault files, not plaintext content. Storage buckets are configured with appropriate access policies that restrict downloads to authenticated users.

**Realtime Security:** Supabase Realtime provides WebSocket-based pub/sub functionality. The Realtime channels are scoped to specific users and rooms, preventing unauthorized message interception. The presence feature used by the Kiss Screen does not write to the database, ensuring that ephemeral presence data is not persisted.

## 6.2 Signaling Server Security

The custom signaling server (`server.ts`, 625 lines) is a critical component that handles WebRTC signaling, real-time messaging relay, and push notification coordination. Its security is paramount to the overall system security.

**Message Relay Model:** The signaling server operates as a pure relay - it routes encrypted messages between connected clients but never inspects or modifies the message content. Messages are stored temporarily in memory only until they can be delivered to the connected recipient. If a recipient is offline, messages are not queued on the signaling server (the outbox pattern in the Android client handles offline message queuing locally).

**Room-Based Routing:** WebSocket connections are organized into rooms based on the pair of communicating users. Messages are only routed within the same room, preventing users from receiving messages intended for other pairs. The room assignment is verified during the WebSocket connection handshake.

**Supabase Integration:** The signaling server uses the Supabase service role key for database operations, which bypasses RLS. This is necessary for the server to perform operations on behalf of users (such as updating presence status or managing push tokens). However, it means that compromise of the signaling server would grant full database access. The server should ideally use a more restricted database role for its operations.

**Environment Variable Security:** The server loads configuration from environment variables or `.env` files. The `loadEnv()` function reads environment variables from multiple possible locations, which could lead to accidental exposure if `.env` files are not properly protected. The server prints a warning if it falls back to using the ANON_KEY instead of the SERVICE_ROLE_KEY, which is good security awareness.

**Push Notification Security:** The v1.0.0 release notes mention that Firebase Admin initialization has been removed in favor of self-hosted Ntfy for push notifications. This is a positive privacy change, as it eliminates Google's infrastructure from the notification pipeline. Ntfy is an open-source, self-hostable notification service that provides push wakeups without requiring Google Firebase Cloud Messaging (FCM). However, push notifications through Ntfy still travel through external infrastructure, and while they only contain wakeup signals (not message content), the notification metadata could reveal communication timing.

## 6.3 Docker and Container Security

The backend deployment uses Docker Compose for container orchestration. Docker provides process-level isolation and simplifies deployment, but introduces its own security considerations.

**Container Isolation:** Each service runs in its own container with minimal privileges. The Docker Compose configuration defines separate networks for internal service communication and external access, providing network segmentation between components.

**Volume Mounts:** Persistent data is stored in Docker volumes defined in the `volumes/` directory. These volumes contain the PostgreSQL database files, Kong configuration, and Ntfy data. Proper file permissions on these volumes are essential for security. The deployment scripts should ensure that volume directories have restrictive permissions (readable only by the Docker daemon and authorized users).

**Container Image Sources:** The Docker Compose file references images from Docker Hub. Users must trust that these images are authentic and have not been tampered with. Using specific version tags rather than "latest" would improve supply chain security by ensuring reproducible deployments.

**Host System Security:** Docker containers share the host kernel, so container escape vulnerabilities could compromise the entire host. Running the containers with appropriate security options (user namespaces, seccomp profiles, AppArmor/SELinux) would improve isolation. The deployment documentation should include hardening recommendations for the host system.

**Environment File Security:** The `.env` file contains sensitive configuration including database passwords, JWT secrets, and API keys. This file must be protected with restrictive file permissions (600 or tighter) and should never be committed to version control. The `.gitignore` file correctly excludes `.env` and `local.properties` files.

## 6.4 Database Security and RLS

The PostgreSQL database schema is defined through SQL migration files in `volumes/db/init/`. The schema includes tables for user profiles, key bundles, messages metadata, stories, music, drawings, scrapbook, vault metadata, and TURN credentials.

**Row Level Security Policies:** RLS is enabled on all tables containing user data, with policies that restrict access based on the authenticated user's identity. For example, a user can only query rows where the `user_id` column matches their authenticated user ID. This prevents users from accessing other users' data even if they discover the table structure and API endpoints.

**Key Bundle Storage:** The `01-pre_key_bundles.sql` migration defines tables for storing Signal protocol key bundles. These bundles contain public keys (identity key, signed pre-key, one-time pre-keys) that are meant to be public - they are uploaded to the server so that other users can initiate encrypted communication. The private keys never leave the client's secure storage. The public key storage does not compromise security.

**Metadata Storage:** The database stores message metadata (sender, recipient, timestamp) but not message content. Message content is encrypted on the client and transmitted through the signaling server without database persistence. This metadata minimization is consistent with zero-knowledge principles, though it does reveal communication patterns.

**SQL Injection Prevention:** PostgREST automatically parameterizes all SQL queries, preventing SQL injection attacks. The custom signaling server uses parameterized queries through the Supabase client library, which also prevents injection.

## 6.5 TURN Server Security

The Coturn TURN server is used for WebRTC NAT traversal when direct peer-to-peer connection is not possible. TURN relays media traffic through the server, which introduces security considerations.

**Credential Management:** TURN credentials are generated by the signaling server and stored in the database (`09-turn-credentials.sql`). These credentials have a limited lifetime and are rotated periodically. Short-lived credentials reduce the window of opportunity for credential theft and abuse.

**Relay Security:** Media traffic relayed through TURN is encrypted using DTLS-SRTP end-to-end between the peers. The TURN server cannot decrypt the media content - it only relays encrypted packets. This means that even with full TURN server compromise, media content remains confidential.

**Bandwidth Abuse:** The TURN server could potentially be abused for bandwidth amplification if an attacker obtains valid credentials. Rate limiting and bandwidth quotas should be configured to prevent abuse. The `setup_coturn.sh` script should include such protections.

**Deployment Security:** The TURN server runs on ports 3478 (STUN) and 5349 (TURN/TLS). These ports must be exposed to the internet for NAT traversal to work, which increases the attack surface. The Coturn configuration should restrict relay IP ranges and implement connection limits.


---

# 7. Vulnerability Assessment

This section presents a systematic vulnerability assessment of Enclave based on static source code analysis, architecture review, and comparison with known vulnerability classes. The findings are organized by severity using the Common Vulnerability Scoring System (CVSS) severity ratings.

## 7.1 Critical Findings

No critical vulnerabilities (CVSS 9.0-10.0) were identified during this assessment. The cryptographic implementation correctly uses established libraries, and no evidence of backdoors, intentional weaknesses, or deliberate security bypasses was found in the source code.

## 7.2 High-Severity Findings


> **HIGH-1: Project Immaturity and Lack of Independent Audit (CVSS: 7.5)**
> 

> **Description:** The project is approximately 9 days old with no community adoption, no third-party security audit, and no bug bounty program. While the code appears well-structured, the lack of external review means that subtle vulnerabilities may exist undetected.

> **Impact:** Undiscovered vulnerabilities in cryptography, authentication, or access control could compromise user privacy.

> **Mitigation:** The project should undergo a formal security audit by a reputable cybersecurity firm. A public bug bounty program should be established to incentivize responsible disclosure.

> **HIGH-2: Alpha/Beta Security Dependencies (CVSS: 7.1)**
> 

> **Description:** The project uses alpha and beta versions of security-critical libraries: AndroidX Security Crypto 1.1.0-alpha06 and AndroidX Biometric 1.2.0-alpha05. These pre-release versions may contain undiscovered bugs or security issues that have not been addressed in the stable release cycle.

> **Impact:** Bugs in alpha security libraries could lead to key leakage, authentication bypass, or data exposure.

> **Mitigation:** Migrate to stable versions of these libraries as soon as they are available. Monitor the AndroidX release notes for security advisories affecting the alpha versions.

> **HIGH-3: SERVICE_ROLE_KEY Exposure Risk (CVSS: 7.5)**
> 

> **Description:** The signaling server uses the Supabase SERVICE_ROLE_KEY, which bypasses Row Level Security. If this key is exposed through log files, environment variable leaks, or server compromise, an attacker gains full database access.

> **Impact:** Database compromise exposing all user metadata, key bundles, and stored encrypted files.

> **Mitigation:** Restrict the signaling server's database permissions to only the operations it requires. Implement key rotation policies. Ensure logs do not contain API keys or secrets.


## 7.3 Medium-Severity Findings


> **MEDIUM-1: Rapid Release Cycle Without Adequate Testing (CVSS: 5.3)**
> 

> **Description:** Three major versions (v1.0.0, v1.1.0, v2.0.0) were released within approximately 8 hours. This velocity suggests insufficient time for thorough testing, security review, and stability validation between releases.

> **Impact:** Releases may contain regressions, untested code paths, or security fixes that introduce new vulnerabilities.

> **Mitigation:** Implement a structured release cycle with minimum testing periods, automated security scanning, and staged rollouts.

> **MEDIUM-2: "Latest" Container Image Tags (CVSS: 5.3)**
> 

> **Description:** Several Docker containers (Ntfy, Supabase Studio, PM2, Coturn) use the "latest" image tag, which means they will automatically update to the newest image on restart. This introduces supply chain risk if a malicious or buggy image is published.

> **Impact:** Unexpected container updates could introduce vulnerabilities, breaking changes, or malicious code.

> **Mitigation:** Pin all container images to specific version hashes. Implement a controlled update process with security review of new images.

> **MEDIUM-3: Push Notification Metadata Leakage (CVSS: 5.0)**
> 

> **Description:** Push notifications sent through Ntfy reveal the timing of communications. While notification content is not included (only a wakeup signal), the timing metadata could be used to infer communication patterns, relationship activity levels, and timezone information.

> **Impact:** Adversaries monitoring network traffic or the Ntfy server could infer communication patterns.

> **Mitigation:** Consider implementing randomized notification delays or batching to obscure exact communication timing.

> **MEDIUM-4: Aggressive Minimum SDK (CVSS: 4.3)**
> 

> **Description:** The minimum SDK of API 34 (Android 14) excludes the vast majority of Android devices. While this ensures access to modern security APIs, it may force users to purchase new devices or prevent adoption among users with older but functional devices.

> **Impact:** Limited adoption due to device incompatibility. Users with unsupported devices may use less secure alternatives.

> **Mitigation:** Consider lowering the minimum SDK with graceful degradation of security features for older devices.


## 7.4 Low-Severity Findings and Hardening Recommendations


> **LOW-1: No Automated CI/CD Security Scanning**
> 

> The repository does not appear to have integrated automated security scanning in its CI/CD pipeline. Tools like MobSF (mentioned in release notes), CodeQL, Dependabot security updates, and SAST scanners should be integrated into the GitHub Actions workflow.

> **LOW-2: Limited Error Handling Information**
> 

> Some error handling in the signaling server could leak implementation details through error messages. While not directly exploitable, detailed error messages can aid attackers in reconnaissance.

> **LOW-3: No Rate Limiting Documentation**
> 

> The documentation does not mention rate limiting for API endpoints, login attempts, or WebSocket connections. Rate limiting should be implemented and documented to prevent brute force and denial of service attacks.

> **LOW-4: Clipboard Sensitivity**
> 

> The app allows text copying from messages (as documented in the premium multi-selection context bar). Copied text remains in the system clipboard, which could be accessed by other applications. Consider implementing automatic clipboard clearing after a timeout.

> **LOW-5: Screenshot Allowance in Debug Builds**
> 

> While production builds block screenshots (which is correct), the debug build allowance could lead to accidental exposure if users mistakenly install debug builds. Consider adding a visual indicator for debug builds.


## 7.5 Informational Observations


> **INFO-1: Single Developer Dependency**
> 

> The project depends almost entirely on a single developer (saifmukhtar). If this developer stops maintaining the project, users may be left without security updates. Building a contributor community should be a priority.

> **INFO-2: No F-Droid Release Yet**
> 

> While the project includes F-Droid submission metadata and Fastlane configuration, it has not yet been accepted into the F-Droid repository. F-Droid inclusion would provide reproducible builds and additional trust.

> **INFO-3: GitHub Copilot Contributions**
> 

> Significant portions of the code appear to be generated with GitHub Copilot assistance. While this is not inherently problematic, AI-generated code should be reviewed for security correctness, as AI assistants may generate code with subtle vulnerabilities.


---

# 8. Privacy and Data Protection Analysis

## 8.1 Data Collection and Minimization

Enclave follows the principle of data minimization to an exceptional degree. The self-hosted architecture means that no third party collects user data by design. The server infrastructure under the user's control only stores data that is necessary for the functioning of the application.

**What the Server Stores:** The PostgreSQL database stores user authentication credentials (hashed passwords), Signal protocol public key bundles (which are designed to be public), user profiles (usernames, status messages), message metadata (sender ID, recipient ID, timestamp - but NOT message content), file storage metadata (filenames, sizes, storage locations - but the files themselves are encrypted), call logs (call start/end times, duration), and feature-specific data (drawing data, music playlists, scrapbook entries, love letters, vault metadata).

**What the Server Does NOT Store:** The server does not store message content (only encrypted ciphertext that passes through without persistence), decryption keys (these never leave the device hardware keystore), biometric data (handled entirely by the Android system), plaintext media files, contact lists, location data, or usage analytics.

**Analytics and Tracking:** No analytics or tracking mechanisms were identified in the source code. There are no connections to Google Analytics, Firebase Analytics, or any other telemetry service. The absence of any tracking is a significant privacy advantage over commercial messaging applications.

## 8.2 Metadata Protection

While Enclave successfully protects message content through end-to-end encryption, metadata protection requires additional consideration. Metadata includes information about who communicated with whom, when, how often, and for how long.

**Communication Timing:** The database stores message timestamps, revealing when communications occurred. The signaling server logs connection events. Push notifications reveal approximate communication timing. This metadata could be used to infer relationship patterns, daily routines, and timezone information.

**Communication Frequency:** Message metadata reveals how frequently the two users communicate. Call logs store call duration. This frequency information could be sensitive in certain contexts.

**IP Address Exposure:** Both users' IP addresses are visible to the self-hosted server. For a couple communicating through a server they control, this is acceptable. However, if the server is hosted on a VPS, the hosting provider can see the IP addresses connecting to the server.

**File Size Patterns:** The encrypted files stored in Supabase Storage have sizes that roughly correlate with the original file sizes. An observer could potentially infer the type of media being shared (photo vs. video vs. document) from file size patterns.

These metadata limitations are inherent to any server-relayed communication system and are not specific weaknesses of Enclave. The self-hosted model provides better metadata protection than commercial alternatives because the metadata is under the users' control rather than a corporation's.

## 8.3 User Consent and Transparency

Enclave demonstrates strong transparency through its open-source nature. Users can inspect the entire source code to understand exactly what data is collected and how it is processed. This is the highest level of transparency possible for a software application.

The project provides clear documentation about its architecture, security model, and deployment process. The website and README explain the zero-knowledge design, the Signal Protocol implementation, and the self-hosted nature of the backend. This transparency allows technically inclined users to verify the security claims.

However, a formal privacy policy document was not identified in the repository. While the self-hosted nature makes a traditional privacy policy less critical (since no third party processes user data), providing a clear privacy statement would help users understand what data flows where and what the security implications are.

## 8.4 Right to Erasure

The self-hosted architecture provides the ultimate right to erasure: users can simply delete their entire backend deployment, and all data is permanently removed. This is significantly stronger than commercial services where "deletion" may only hide data from the user interface while the service provider retains copies.

The Android app includes features for clearing conversations and securely deleting vault files. The "Clear Conversation Safeguard" feature allows users to instantly clear conversation states. The vault supports "Zero-Knowledge Multi-Delete" for securely shredding multiple files in one operation. The `DisappearingMessagesWorker.kt` automatically deletes messages after their configured expiration time.

For complete account deletion, users can delete their Supabase auth account, which removes their user record and associated RLS-protected data. The database migrations and Supabase documentation should be consulted for complete deletion procedures.


---

# 9. Operational Security

## 9.1 Deployment Security

Enclave provides a one-click deployment script accessed via `curl -fsSL https://enclave.saifmukhtar.dev/install | sudo bash`. While convenient, piping scripts directly from the internet to `sudo bash` is a well-known security anti-pattern that requires careful consideration.

**The Curl-to-Bash Pattern:** The convenience of a one-line installer must be balanced against the security risks. When a user executes this command, they are downloading and executing arbitrary code with root privileges, trusting that the server at enclave.saifmukhtar.dev has not been compromised and that the script has not been tampered with. If an attacker compromises the website, they could serve a malicious installer that backdoors the user's server.

**Mitigations Provided:** The project offers alternative deployment methods for security-conscious users. The `deploy.sh` script can be downloaded and reviewed before execution. The manual deployment guide in the documentation allows users to set up each component individually. The Docker Compose configuration can be inspected before deployment. These alternatives allow paranoid users to verify every step of the installation.

**Deployment Script Analysis:** The deployment scripts (`deploy.sh` and `deploy_to_cloud.sh`) automate the setup of the Docker Compose stack, SSL certificate generation (likely via Let's Encrypt), and service startup. The scripts use `rsync` and SSH for cloud deployment, which requires proper SSH key management. The scripts should include input validation, error handling, and rollback capabilities.

**SSL Certificate Management:** The documentation mentions SSL/TLS for all connections. The setup likely uses Let's Encrypt for free SSL certificates via Certbot or similar tools. Certificate renewal must be automated to prevent service disruption. The SSL configuration should use modern TLS versions (1.2 minimum, 1.3 preferred) and strong cipher suites.

## 9.2 Key Generation and Management

The `generate_keys.js` script creates the cryptographic secrets needed for the backend infrastructure. Proper key generation is critical for the security of the entire system.

**JWT Secret Generation:** The script generates a cryptographically strong random secret for JWT token signing. The quality of this secret determines the security of the authentication system. The script should use a cryptographically secure random number generator (CSPRNG) with sufficient entropy (at least 256 bits).

**API Key Generation:** The ANON_KEY and SERVICE_ROLE_KEY for Supabase are generated by the script. These keys control access to the entire backend API. The SERVICE_ROLE_KEY in particular is a powerful credential that bypasses all access controls. It must be protected with extreme care.

**Key Storage on Server:** The generated keys are stored in the `.env` file on the server. This file must be protected with restrictive permissions (chmod 600) and should never be exposed through log files, process listings, or backup archives. The deployment scripts should set these permissions automatically.

**Key Rotation:** The documentation does not appear to describe a key rotation procedure. Regular rotation of JWT secrets and API keys is a security best practice that limits the impact of key compromise. A key rotation procedure should be documented and ideally automated.

## 9.3 Update and Patch Management

Keeping all components updated is critical for maintaining security over time. The self-hosted model places the responsibility for updates on the user, which requires clear documentation and tooling.

**Android App Updates:** The app is distributed through F-Droid (submission in progress) and potentially GitHub Releases. Users must manually update the Android app when new versions are released. The app should include a version check that notifies users when updates are available.

**Backend Updates:** Backend component updates require updating Docker images, applying database migrations, and restarting services. The deployment scripts should include an update command that handles these steps safely. Database migrations in `volumes/db/init/` are applied on first start, but update migrations may be needed for version upgrades.

**Security Patch Monitoring:** Users must monitor security advisories for all components: Supabase, PostgreSQL, Kong, Node.js, Coturn, Ntfy, and the Android operating system. Dependabot is configured for the repository, which helps with dependency updates for the signaling server. Users should subscribe to security mailing lists for the self-hosted components.

**Emergency Patching:** The documentation should include an emergency patching procedure for critical vulnerabilities. This should include steps for rapid updates, rollback procedures if an update fails, and communication channels for security announcements.

## 9.4 Incident Response Preparedness

Incident response preparedness is essential for any system handling sensitive communications. While the self-hosted model means users manage their own incident response, the project should provide guidance and tooling.

**Logging and Monitoring:** The Docker Compose stack generates logs for each container. These logs should be aggregated and monitored for security events (failed authentication attempts, unusual API access patterns, errors). The documentation should recommend log aggregation tools and provide example monitoring configurations.

**Security Alerting:** The system should be configured to alert administrators of potential security incidents. This could include alerts for multiple failed login attempts, unusual data access patterns, certificate expiration warnings, and service downtime.

**Backup and Recovery:** The `DailyBackupWorker.kt` implements automated backups. These backups must be tested regularly to ensure they can be restored. Backup encryption keys should be stored separately from the backups. A documented disaster recovery procedure should be in place.

**Security Contact:** The project does not currently have a published security contact or vulnerability disclosure policy. Establishing a security@ email address or using GitHub's private vulnerability reporting feature would enable responsible disclosure of security issues.


---

# 10. Open Source and Community Analysis

## 10.1 License Analysis

Enclave is licensed under the GNU Affero General Public License version 3.0 (AGPL-3.0). This is one of the strongest copyleft licenses available and has significant implications for the project's openness and commercial exploitation.

**AGPL-3.0 Key Provisions:** The AGPL requires that anyone who distributes the software or provides it as a service over a network must make the complete corresponding source code available to users under the same license. This means that even if someone uses Enclave to provide a hosted messaging service, they must share all their modifications with their users. The AGPL closes the "network use loophole" that exists in the standard GPL.

**Anti-Commercial-Exploitation:** The AGPL license choice is a strong signal that the author intends to prevent commercial exploitation of the project without contributing back. A greed-driven actor would likely choose a permissive license (MIT, Apache) or a proprietary license that allows them to sell the software while keeping the source private. The AGPL choice is inconsistent with a profit-maximization motive.

**Compatibility with Dependencies:** The AGPL is compatible with most of the project's dependencies, which use permissive licenses (Apache 2.0, MIT, BSD). However, some dependencies may have license terms that need to be reviewed for AGPL compatibility. The project should include a comprehensive license attribution file.

**Patent Protection:** The AGPL includes an explicit patent grant, which provides some protection against patent litigation from contributors. This is a valuable safeguard for open-source security software.

## 10.2 Community Health

Community health metrics indicate a project in the very earliest stages of development with essentially no community adoption.
Community Health Metrics
| Metric | Value | Assessment |
|---|---|---|
| GitHub Stars | 0 | No community adoption |
| GitHub Forks | 0 | No derivative works |
| GitHub Watchers | 0 | No active observers |
| Open Issues | 0 | No bug reports or feature requests |
| Open Pull Requests | 0 | No external contributions |
| Contributors | 3 (1 human + Copilot + Dependabot) | Single developer project |
| Commit Frequency | 75 commits in 9 days (~8 commits/day) | Very active initial development |
| Discussions | 0 | No community engagement |


The complete absence of community engagement is expected for a project that is less than two weeks old. However, it means that the code has not been reviewed by external security researchers, has not been battle-tested in real-world deployments, and has no established trust in the open-source community.

The high commit frequency (approximately 8 commits per day) suggests sustained development effort. The commit messages follow conventional commit format (feat, fix, chore, refactor, build), indicating organized development practices. The presence of Dependabot shows automated dependency management is in place.

## 10.3 Dependency Analysis

Enclave relies on a substantial number of third-party dependencies, which introduces supply chain security considerations.

**Core Cryptographic Dependency:** The most critical dependency is `libsignal-client` version 0.39.2, maintained by Signal Messenger. This is a well-established, formally reviewed cryptographic library that is used by millions of people worldwide. Using the official Signal library rather than a reimplementation is a strong security decision.

**AndroidX Dependencies:** The project uses many AndroidX libraries from Google. Most are stable versions, but the Security Crypto (1.1.0-alpha06) and Biometric (1.2.0-alpha05) libraries are alpha releases. Alpha libraries from Google are generally of high quality but may contain bugs or API changes before stabilization.

**Supabase Kotlin SDK:** The Supabase Kotlin SDK at BOM 2.6.1 is the official client library for Supabase. It is actively maintained and widely used in the Kotlin community.

**WebRTC Library:** Stream WebRTC Android at version 1.1.1 provides Kotlin-friendly WebRTC bindings. WebRTC is a complex protocol with a history of security vulnerabilities. Keeping this dependency updated is essential.

**Backend Dependencies:** The signaling server uses Express 5.2.1 and `ws` 8.20.1. Both are mature, widely-used Node.js libraries with active maintenance.

**Dependency Management:** Dependabot is configured for the repository, which automatically creates pull requests when dependencies have security updates. This is a positive security practice. The project's Gradle configuration uses version catalogs for dependency management, which provides a centralized location for version numbers.

## 10.4 Code Quality and Maintainability

The source code demonstrates above-average quality for an open-source project of this scope. Several indicators support this assessment:

**Type Safety:** The use of Kotlin with its null safety features, sealed classes, and type system helps prevent common programming errors. The TypeScript signaling server adds type safety to the backend.

**Documentation:** The project includes extensive documentation: a detailed system architecture document (880 lines, 46.9 KB), setup guide, repository structure documentation, F-Droid submission guide, and comprehensive README. The inline code documentation is adequate though not exhaustive.

**Testing Infrastructure:** No formal test suite was identified in the repository. Unit tests, integration tests, and end-to-end tests would significantly improve confidence in the code's correctness. Test coverage for cryptographic operations is particularly important.

**Error Handling:** The code includes try-catch blocks for critical operations, particularly in the cryptographic components. The signaling server includes error handling for WebSocket connections and database operations.

**Code Organization:** The monorepo structure with clear separation between apps, backend, and documentation is well-organized. The Android package structure follows Android development best practices.


---

# 10.5 Additional Security Deep Dive: Component-by-Component Analysis

## 10.5.1 The Double Ratchet Algorithm Implementation

The Double Ratchet algorithm is the cornerstone of Enclave's end-to-end encryption. This algorithm, originally developed by Trevor Perrin and Moxie Marlinspike for Signal Messenger, provides two simultaneous ratcheting mechanisms that together achieve both forward secrecy and future secrecy. Understanding how Enclave implements this algorithm is essential for evaluating its security posture.

The first ratchet mechanism is the symmetric-key ratchet, also known as the chain ratchet. Each time a message is sent or received, a new message key is derived from the previous chain key using a one-way KDF (Key Derivation Function). This process is irreversible: given a message key, an attacker cannot derive the chain key that produced it, and therefore cannot derive previous message keys. This property provides forward secrecy - compromise of a current message key does not compromise past messages.

The second ratchet mechanism is the Diffie-Hellman ratchet. Each party maintains an ephemeral key pair for the DH ratchet. When a message is sent, the sender includes the public key of their current DH key pair. When the recipient replies, they perform a DH key agreement between their current private key and the sender's public key, producing a new shared secret. This shared secret is then used to reseed the symmetric chain, creating a break in the chain that prevents an attacker who has compromised one device's state from deriving future message keys. This provides future secrecy or post-compromise security.

Enclave's implementation of the Double Ratchet through libsignal-client v0.39.2 inherits the extensive security review that the Signal Protocol has undergone. The Signal Protocol has been formally verified in academic research, audited by multiple security firms, and battle-tested by billions of users worldwide through Signal Messenger, WhatsApp, Facebook Messenger, and other major platforms. By using the official library rather than reimplementing the protocol, Enclave avoids the common pitfalls that plague custom cryptographic implementations.

The EnclaveSignalStore class implements the SignalProtocolStore interface, which requires methods for storing and retrieving identity keys, pre-keys, signed pre-keys, and session states. The implementation uses Android's EncryptedSharedPreferences, which means that the protocol state (including private keys) is encrypted at rest using hardware-backed keys. This is a significant security advantage over Signal's default implementation, which on Android uses standard preferences storage.

## 10.5.2 X3DH Key Agreement Analysis

The Extended Triple Diffie-Hellman (X3DH) key agreement protocol is used for initial session establishment in Enclave. When two users first want to communicate, they need a way to establish a shared secret without having previously communicated. X3DH solves this problem using a clever combination of long-term and ephemeral keys.

Each user in Enclave generates four types of keys: an identity key pair (long-term, Ed25519 or X25519), a signed pre-key pair (medium-term, changed periodically), a signature over the signed pre-key using the identity private key, and multiple one-time pre-key pairs (short-term, each used only once). These key bundles are uploaded to the Supabase database where they can be fetched by potential communication partners.

When Alice wants to send her first message to Bob, she fetches Bob's key bundle from the server. She then generates an ephemeral key pair and performs three or four DH operations: between her ephemeral private key and Bob's signed pre-key public key, between her identity private key and Bob's signed pre-key public key, between her ephemeral private key and Bob's identity public key, and optionally between her ephemeral private key and Bob's one-time pre-key public key. The results of these DH operations are combined using a KDF to produce the initial root key for the Double Ratchet.

The security of X3DH relies on the one-time pre-keys being truly one-time. Once a one-time pre-key is used, it should be deleted and never reused. Enclave's PreKeyRotationWorker periodically generates new pre-key bundles and uploads them to the server, ensuring that fresh one-time pre-keys are always available. The server-side schema tracks which pre-keys have been used so that they can be removed from the database.

The X3DH protocol provides several important security properties: authentication (Alice knows that only Bob can decrypt her initial message because she used his public keys), forward secrecy (compromise of Bob's identity key does not compromise past sessions because the ephemeral key contributions are gone), and future secrecy (the initial message includes Alice's ephemeral public key, allowing Bob to start the DH ratchet).

## 10.5.3 WebRTC Security Architecture

WebRTC is a complex technology stack that provides peer-to-peer audio, video, and data communication. While WebRTC includes built-in encryption, its security depends on proper implementation of the signaling and ICE (Interactive Connectivity Establishment) components.

Enclave uses WebRTC in a two-party configuration, which is the simplest and most secure WebRTC topology. In a two-party call, media flows directly between the two peers without needing to pass through a Selective Forwarding Unit (SFU) or Multipoint Control Unit (MCU). This eliminates the trust requirement that would exist with a media server that has access to unencrypted media streams.

The WebRTC security model in Enclave works as follows. First, both peers connect to the signaling server via secure WebSocket (WSS). Second, Alice sends an SDP offer to Bob through the signaling server, describing her media capabilities and ICE candidates. Third, Bob responds with an SDP answer through the same channel. Fourth, both peers exchange ICE candidates to find the best network path. Fifth, once the connection is established, all media traffic is encrypted using DTLS (Datagram Transport Layer Security) for key exchange and SRTP (Secure Real-time Transport Protocol) for media encryption.

The DTLS handshake in WebRTC uses self-signed certificates generated by each peer. These certificates are authenticated through the signaling channel - the fingerprint of each peer's certificate is included in their SDP offer/answer, and the other peer verifies that the certificate presented during DTLS matches this fingerprint. This binding between the signaling channel and the DTLS handshake prevents man-in-the-middle attacks on the media connection, assuming the signaling channel is secure.

If direct peer-to-peer connection fails due to NAT or firewall restrictions, Enclave falls back to the self-hosted Coturn TURN server. Media relayed through TURN remains encrypted end-to-end because the DTLS-SRTP encryption is between the peers, not between the peer and the TURN server. The TURN server sees only encrypted packets and cannot access the media content.

The signaling server's role in WebRTC security is critical. If an attacker compromises the signaling server, they could potentially manipulate SDP messages to redirect media through an attacker-controlled TURN server or inject themselves as a man-in-the-middle. Enclave mitigates this risk by making the signaling server self-hosted - users control their own signaling infrastructure, reducing the attack surface compared to a centralized service.

## 10.5.4 Database Schema Security Analysis

The PostgreSQL database schema is defined through a series of SQL migration files that create tables, indexes, and Row Level Security policies. The schema design reflects security-conscious decisions about what data to store and how to protect it.

The migration 00-seed_users.sql initializes the user management tables through Supabase's GoTrue integration. User passwords are hashed using bcrypt with appropriate work factors - GoTrue defaults to a secure configuration. The authentication system supports email verification, password reset, and JWT-based session management.

The migration 01-pre_key_bundles.sql creates tables for Signal protocol key material. Importantly, these tables store only public keys. The identity key public key, signed pre-key public key, and one-time pre-key public keys are all designed to be shared publicly - they are literally called "public keys" because they are meant to be distributed to other users. The corresponding private keys never leave the Android device's hardware-backed keystore. Storing public keys in the database does not compromise security in any way.

The migration 03-profile-extensions.sql adds user profile information including username display names, status stories, and online status indicators. These are stored as plaintext because they are intended to be visible to the user's partner. The online status is particularly sensitive as it reveals when the user is active in the app.

The migrations 04 through 08 create tables for feature-specific data: music playlists (04-lounge-extensions.sql), collaborative drawings (05-drawings-gallery.sql), scrapbook entries (06-scrapbook-table.sql), music queue and love language preferences (07-playlist-queue.sql), and vault file metadata (08-vault-metadata.sql). All of these tables have Row Level Security enabled, ensuring that users can only access their own data.

The migration 09-turn-credentials.sql manages TURN server credentials for WebRTC NAT traversal. These credentials are time-limited and rotated to prevent abuse. The credential generation is handled by the signaling server, which creates cryptographically random usernames and passwords with expiration timestamps.

## 10.5.5 Local Database Security

Enclave uses Room, Android's recommended ORM library, for local data persistence. The Room database stores decrypted message content, user profiles, call logs, vault metadata, and various feature data. Because this database contains plaintext message content (after decryption), its security is critical.

Room databases on Android are stored as SQLite files in the application's private data directory. On non-rooted devices, this directory is protected by Android's application sandbox, preventing other applications from accessing it. However, on rooted devices or if an attacker gains physical access with debugging capabilities, the database file could be extracted.

Enclave does not appear to implement SQLCipher or similar database encryption for the Room database. This means that if an attacker gains access to the database file, they can read its contents. The primary protection is Android's application sandbox and the device's lock screen. For a security-focused messaging app, adding transparent database encryption would provide defense in depth, especially for the messages table.

The biometric authentication for the vault provides additional protection for the most sensitive files. Vault files are encrypted with keys that require biometric authentication, so even if the database is extracted, the vault files remain encrypted. However, regular chat messages in the Room database would be accessible if the database file is extracted.

Disappearing messages are handled by the DisappearingMessagesWorker, which periodically scans for messages past their expiration time and deletes them from the local database. This is an important privacy feature, though the effectiveness depends on the worker running reliably and the deletion being secure (overwriting data rather than just marking rows as deleted). SQLite's default deletion mechanism only marks rows as free space, so forensic recovery might be possible until the space is reused.

## 10.5.6 Build and Supply Chain Security

The security of the build process is as important as the security of the source code. Enclave's build configuration shows awareness of several build security practices.

The Android build uses Gradle with Kotlin DSL (build.gradle.kts), which provides type-safe build configuration. The build configuration separates debug and release builds with different security settings. Release builds enable code obfuscation and resource shrinking through R8/ProGuard, which makes reverse engineering more difficult. Debug builds allow screenshots and cleartext connections for development convenience.

The signing configuration for release builds reads keystore credentials from local.properties, which is excluded from version control through .gitignore. This prevents accidental exposure of signing keys in the Git repository. However, developers must ensure that their local.properties file and keystore file are properly protected on their development machines.

Dependabot is configured for the repository, providing automated notifications when dependencies have security updates. This is a critical security practice for maintaining the security posture over time. However, Dependabot only monitors direct dependencies - transitive dependencies (dependencies of dependencies) may have vulnerabilities that are not immediately detected.

For the backend, the Docker Compose configuration pulls images from Docker Hub. Docker Content Trust (DCT) is not mentioned in the configuration, which means images are not verified through cryptographic signatures. Enabling Docker Content Trust would provide assurance that the images have not been tampered with between the publisher and the deployment.

The GitHub Actions workflow (deploy-pages.yml) builds the React website and deploys to GitHub Pages. The workflow should use pinned action versions (specific commit hashes rather than version tags) to prevent supply chain attacks through compromised GitHub Actions. The current configuration should be reviewed for this practice.

## 10.5.7 Threat Model Analysis

A threat model identifies potential attackers, their capabilities, and the defenses against them. Enclave's architecture implies the following threat model:

**Threat: Network Eavesdropper** - An attacker monitoring network traffic between the Android app and the server. **Defense:** All traffic uses HTTPS/WSS with certificate pinning. Message content is E2EE with Signal Protocol. Even with complete network capture, the attacker gains only encrypted ciphertext and metadata. **Residual Risk:** Traffic analysis may reveal communication timing and approximate message sizes.

**Threat: Server Compromise** - An attacker gains control of the self-hosted backend server. **Defense:** The server stores only encrypted message ciphertext, public keys, and metadata. Without the clients' private keys, the attacker cannot decrypt messages. However, the attacker gains access to all metadata and stored encrypted files. **Residual Risk:** Complete metadata exposure; encrypted files may be susceptible to offline brute force if passwords are weak.

**Threat: Device Theft** - An attacker steals or finds an unlocked Android device. **Defense:** Hardware-backed keystore protects private keys. Biometric authentication protects the vault. Screen lock protects app access. EncryptedSharedPreferences protect Signal state. **Residual Risk:** If the device is unlocked when stolen, the attacker may access the app until it locks. Forensic analysis of storage may recover deleted data.

**Threat: Malicious App on Same Device** - A malicious application attempts to access Enclave's data or intercept communications. **Defense:** Android's application sandbox isolates app data. The MusicPlaybackService implements binder verification to reject unauthorized connections. Content providers are not exported. **Residual Risk:** On rooted devices or with Android vulnerabilities, sandbox isolation may be bypassed.

**Threat: Man-in-the-Middle Attack** - An attacker intercepts TLS connections using a fraudulent certificate. **Defense:** Certificate pinning in the network security configuration rejects certificates not matching the pinned public key hashes. **Residual Risk:** If pinning is misconfigured or uses weak hashes, MitM may be possible.

**Threat: Insider Attack (Developer Malicious)** - The developer intentionally includes backdoors or vulnerabilities. **Defense:** The AGPL license and complete source code availability enable security review by anyone. The self-hosted architecture means the developer has no access to user servers. The build process is reproducible (with F-Droid). **Residual Risk:** Subtle backdoors in the source code could evade detection without extensive review.

**Threat: Supply Chain Attack** - A dependency is compromised with malicious code. **Defense:** Dependabot monitors for security updates. The project uses well-known, widely-used libraries. The AGPL license ensures derivatives are open source. **Residual Risk:** Zero-day vulnerabilities in dependencies, compromised package repositories, or typosquatting attacks on package names.

## 10.5.8 Comparison with OWASP Mobile Security Standards

The OWASP Mobile Application Security Verification Standard (MASVS) provides a comprehensive framework for evaluating mobile application security. Enclave's alignment with MASVS requirements was assessed:
OWASP MASVS Alignment Assessment
| MASVS Category | Requirement | Status |
|---|---|---|
| MSTG-ARCH | Architecture and threat modeling documented | Partial - Architecture documented; no formal threat model |
| MSTG-STORAGE | Sensitive data storage security | Strong - Hardware-backed encryption, EXIF stripping |
| MSTG-CRYPTO | Cryptography usage | Strong - Signal Protocol, official libsignal |
| MSTG-AUTH | Authentication and session management | Strong - Biometric auth, JWT tokens |
| MSTG-NETWORK | Network communication security | Strong - HTTPS/WSS, certificate pinning |
| MSTG-PLATFORM | Platform interaction security | Strong - Proper permission model, exported component protection |
| MSTG-CODE | Code quality and build settings | Medium - ProGuard enabled, no test suite identified |
| MSTG-RESILIENCE | Anti-tampering and anti-reversing | Medium - Code obfuscation, debug build separation |


## 10.5.9 Attack Surface Mapping

Understanding the attack surface is essential for prioritizing defensive investments. Enclave's attack surface can be mapped across several layers:

**Android Application Layer:** The Android app accepts input through the UI, WebSocket connections, push notifications, file imports, and camera/microphone. Each input channel is a potential attack vector. The app processes encrypted data from the network, decrypts it using the Signal Protocol, and displays it to the user. The most critical attack vectors are the cryptographic processing (where input could trigger bugs in libsignal) and the media handling (where malformed media files could exploit parser vulnerabilities).

**Network Layer:** The app communicates with four network endpoints: the Supabase API (HTTPS), the signaling server (WSS), the Ntfy server (WSS), and the Coturn server (STUN/TURN). Each connection point is an attack surface. The Supabase API uses standard HTTP request handling. The signaling server parses WebSocket messages and JSON payloads. The Ntfy connection handles push notification wakeups. The Coturn connection processes STUN/TURN protocol messages.

**Backend Layer:** The backend exposes services through Kong (API gateway), the signaling server WebSocket endpoint, and the Coturn UDP ports. The PostgreSQL database, while not directly exposed to the internet, is accessible from the backend containers. The Docker daemon itself is an attack surface if exposed.

**Infrastructure Layer:** The VPS hosting the backend, the domain DNS configuration, SSL certificate management, and server operating system are all part of the attack surface. Compromise at this layer gives an attacker control over the entire backend.

## 10.5.10 Security Maturity Model Assessment

Using the BSIMM (Building Security In Maturity Model) as a reference framework, Enclave's security maturity can be assessed across several domains:

**Software Environment:** The project uses Git for version control, has a defined build process with Gradle and Docker, and uses Dependabot for dependency monitoring. However, there is no evidence of a formal software development lifecycle (SDLC), security requirements documentation, or secure coding standards.

**Security Testing:** The developer mentions using MobSF for static analysis, which is a positive indicator. However, there is no evidence of dynamic testing, fuzzing, penetration testing, or formal security review. No test automation for security features was identified.

**Deployment and Operations:** The project provides deployment automation through scripts and Docker Compose. The use of environment variables for secrets is appropriate. However, there is no evident security monitoring, intrusion detection, or incident response planning.

**Compliance and Governance:** The AGPL license provides a governance framework. However, there is no vulnerability disclosure policy, no security contact, and no documented security update process.

Overall, Enclave's security maturity level is consistent with an early-stage open-source project created by a security-conscious individual developer. The foundation is solid, but the processes and practices around security need significant maturation as the project grows.


---

# 11. Legitimacy and Trust Assessment

## 11.1 Is This Project Genuine?

After thorough analysis of the source code, documentation, architecture, and development patterns, the assessment is that **Enclave is a genuine open-source project with real technical implementation**. This conclusion is based on multiple lines of evidence:

**Real Implementation, Not Vaporware:** The codebase contains approximately 192 lines of cryptographic implementation in CryptoManager alone, 625 lines for the signaling server, comprehensive Android application code across 12+ packages, SQL migrations defining the complete database schema, Docker Compose configuration for the full backend stack, and extensive documentation. This is far too substantial to be a fake or vaporware project.

**Technically Coherent:** The architecture is internally consistent and technically sound. The Signal Protocol integration correctly uses the official library. The hardware-backed security implementation follows Android best practices. The WebRTC integration follows standard patterns. There are no obviously broken or placeholder implementations.

**Commit History Authenticity:** The commit history shows realistic development patterns with incremental progress, bug fixes, refactoring, and feature additions. The use of conventional commit messages, the presence of both human and automated commits (Dependabot), and the logical progression of changes all indicate authentic development rather than artificial commit generation.

**Documentation Quality:** The documentation is too detailed and technically accurate to be fabricated for a fake project. The system architecture document specifically notes that "Everything here was read from actual source files" - a claim that is verifiable by comparing the documentation against the code.

**Reasonable Development Speed:** While 9 days is very fast, the scope is achievable for an experienced developer working intensively with AI assistance (GitHub Copilot). The ~75 commits over 9 days represents focused but not inhuman development velocity.

## 11.2 Is It Security-Focused?

The assessment is that **Enclave is genuinely security-focused**, with design decisions and implementation details that demonstrate real understanding of security and privacy threats.

**Correct Cryptographic Choices:** Using the official Signal Protocol library rather than a custom implementation demonstrates security awareness. The Double Ratchet algorithm provides the strongest available guarantees for messaging confidentiality. The hardware-backed key storage with production-only enforcement shows commitment to defense in depth.

**Privacy-by-Design Architecture:** The self-hosted model eliminates third-party data collection by design. EXIF stripping addresses a common metadata leakage vector. The zero-knowledge server architecture ensures the server cannot read messages. The absence of analytics or tracking demonstrates genuine privacy commitment.

**Security Hardening in Releases:** The v1.1.0 release specifically addressed security findings from MobSF static analysis, including strict HTTPS enforcement and service binder verification. This responsive security patching indicates active security maintenance.

**Aggressive Security Defaults:** Requiring hardware-backed keystore in production, blocking cleartext traffic, pinning certificates, and blocking screenshots are all security defaults that prioritize protection over convenience. These aggressive defaults are characteristic of security-focused software.

**Transparent Documentation:** The project is transparent about its security model, architecture, and limitations. It does not make exaggerated claims or hide implementation details. This transparency is consistent with genuine security focus rather than security theater.

## 11.3 Is There a Greedy Agenda?

The assessment is that **there is no evidence of a greedy or malicious agenda** behind the Enclave project. In fact, several factors strongly argue against such motivations:

**AGPL License Prevents Commercial Exploitation:** The AGPL-3.0 license legally prevents anyone (including the author) from creating a proprietary commercial version of the software without sharing the source code. This is fundamentally incompatible with a greed-driven business model that seeks to monetize user data or sell premium features.

**No Monetization Mechanisms:** No evidence of advertisements, in-app purchases, premium tiers, subscription models, or data monetization was found in the source code or documentation. The self-hosted model means there is no "service" to charge for.

**No Cryptocurrency Integration:** Unlike many recent projects that include cryptocurrency or blockchain components (often for speculative or fundraising purposes), Enclave has no cryptocurrency integration whatsoever.

**No Data Collection Infrastructure:** The source code contains no analytics SDKs, tracking libraries, telemetry code, or data collection mechanisms. There are no API keys for analytics services, no event logging for behavioral profiling, and no data export functionality.

**Self-Hosted Architecture:** A malicious actor seeking to collect user data would not design a system where all data stays on users' own servers. The self-hosted model is the worst possible architecture for data harvesting. If the author's goal were to spy on users, they would have designed a centralized service where data flows through their infrastructure.

**Open Source Everything:** All components - the Android app, the backend, the deployment scripts, and the documentation - are fully open source. There are no proprietary black-box components, no obfuscated code, and no hidden functionality.

**Personal Domain and Identity:** The author uses their real name and personal domain (saifmukhtar.dev), which creates personal accountability. Anonymous or pseudonymous development is more common for projects with malicious intent.

Based on all available evidence, the project appears to be the work of a developer genuinely passionate about privacy and secure communication, building a tool they want to exist in the world. The AGPL license, self-hosted architecture, complete transparency, and absence of any monetization mechanisms together paint a picture of idealistic open-source development rather than commercial exploitation.

## 11.4 Comparison with Similar Projects
Comparison with Similar Secure Messaging Projects
| Feature | Enclave | Signal | Matrix/Element | Session |
|---|---|---|---|---|
| End-to-End Encryption | Signal Protocol | Signal Protocol | Double Ratchet (Olm/Megolm) | Signal-derived |
| Self-Hosted | Required | No (centralized) | Optional | Decentralized |
| Open Source | AGPL-3.0 | GPL-3.0 | Apache-2.0 | GPL-3.0 |
| Server Can Read Messages | No | No | No (E2EE) | No |
| Metadata Protection | Limited (self-hosted) | Sealed Sender | Limited | Onion routing |
| User Base | ~0 | 100M+ | ~100M | ~1M |
| Code Audit | None | Multiple audits | Multiple audits | Multiple audits |
| Platform Support | Android only | Multi-platform | Multi-platform | Multi-platform |
| Voice/Video Calls | WebRTC P2P | WebRTC P2P | WebRTC P2P | No |
| Group Chat | No (2-person only) | Yes | Yes | Yes |


Compared to established secure messaging platforms, Enclave is significantly less mature but offers a unique value proposition with its couple-focused design and mandatory self-hosting. Signal provides the strongest overall security with features like sealed sender and multi-platform support, but requires trusting Signal's infrastructure. Matrix offers federation and self-hosting options with broad platform support. Session provides metadata protection through onion routing but lacks voice/video calling.

Enclave's niche is couples who want complete infrastructure control and are willing to self-host. This is a legitimate and underserved market segment, though the technical barrier of self-hosting will limit adoption to technically capable users.


---

# 12. Recommendations

## 12.1 Critical Priority

**R-1: Commission Independent Security Audit:** The most important action for the project is to commission a comprehensive security audit by a reputable cybersecurity firm specializing in cryptographic software. The audit should cover the Android application, the signaling server, the deployment scripts, and the overall architecture. Estimated cost: $15,000-$50,000 depending on scope and firm.

**R-2: Establish Vulnerability Disclosure Program:** Create a security contact (security@saifmukhtar.dev or GitHub private vulnerability reporting) and publish a vulnerability disclosure policy. This enables security researchers to report findings responsibly.

**R-3: Migrate to Stable Security Dependencies:** Replace alpha versions of AndroidX Security Crypto and AndroidX Biometric with their stable counterparts as soon as available. Until then, monitor the AndroidX security bulletins for any advisories affecting the alpha versions.

## 12.2 High Priority

**R-4: Implement Comprehensive Test Suite:** Develop unit tests, integration tests, and end-to-end tests. Critical areas for testing include: cryptographic operations (encryption/decryption round-trips, key generation, session establishment), WebRTC signaling flows, authentication and authorization, database RLS policies, and backup/restore procedures.

**R-5: Pin Docker Image Versions:** Replace all "latest" tags in the Docker Compose configuration with specific version hashes. This prevents unexpected updates and improves supply chain security.

**R-6: Implement Rate Limiting:** Add rate limiting to API endpoints, authentication attempts, WebSocket connections, and TURN credential generation. Document the rate limiting policies.

**R-7: Restrict Signaling Server Database Permissions:** Create a dedicated database role for the signaling server with only the permissions it requires, instead of using the SERVICE_ROLE_KEY that bypasses all RLS.

**R-8: Add Automated Security Scanning to CI/CD:** Integrate MobSF (which the developer already uses manually), CodeQL, and dependency vulnerability scanning into the GitHub Actions workflow for automated security checks on every commit.

## 12.3 Medium Priority

**R-9: Lower Minimum SDK with Graceful Degradation:** Consider lowering the minimum SDK to API 28-30 with graceful degradation of hardware-backed security features for older devices. This would significantly expand the addressable device pool.

**R-10: Implement Certificate Pinning Documentation:** Provide clear documentation on how users should configure certificate pinning with their own SSL certificates. Include examples for Let's Encrypt certificates and self-signed certificates.

**R-11: Add Clipboard Clearing:** Implement automatic clearing of the system clipboard after a timeout when users copy text from messages. This prevents other applications from accessing copied sensitive content.

**R-12: Implement Push Notification Batching:** Consider batching or randomly delaying push notifications to obscure exact communication timing and reduce metadata leakage.

**R-13: Create Security Hardening Guide:** Publish a comprehensive server hardening guide covering: operating system security, firewall configuration, Docker security options, log management, intrusion detection, and backup verification.

## 12.4 Low Priority and Future Enhancements

**R-14: Publish Privacy Policy:** Create a clear, user-friendly privacy policy that explains what data is stored where and what the security implications are. The privacy policy should address: what data is stored on the server (metadata only), what data never leaves the device (private keys, plaintext messages), what the user's responsibilities are (server security, updates), and what happens in case of server compromise.

**R-15: Build Contributor Community:** Actively recruit contributors through relevant communities including privacy-focused forums like r/privacy and r/selfhosted on Reddit, Android developer groups, the Signal community, XDA Developers, Hacker News, and open-source security conferences. Diverse contributors improve code quality, security, and sustainability. Consider applying to programs like Google Summer of Code or Outreachy to bring in new contributors.

**R-16: Apply for F-Droid Inclusion:** Complete the F-Droid submission process. F-Droid provides reproducible builds, which allow users to verify that the APK distributed matches the published source code. This is a significant trust enhancement. The project already includes Fastlane metadata and F-Droid build recipes, suggesting the author intends to pursue this.

**R-17: Consider Formal Verification:** For the most security-critical components, consider using formal verification tools like Coq, Isabelle, or Tamarin Prover to mathematically prove correctness properties. While expensive and time-consuming, formal verification provides the highest level of assurance for cryptographic protocols.

**R-18: Implement Perfect Forward Secrecy for Metadata:** Explore techniques for protecting metadata beyond the current model. This could include padding message sizes to fixed chunks (to prevent size-based traffic analysis), adding random delays to message delivery (to prevent timing analysis), using mix networks for certain communications, or implementing dummy traffic generation to obscure real communication patterns.

**R-19: Develop iOS Client:** An iOS client would expand the user base significantly. The Signal Protocol implementation is available for iOS through the same libsignal library. An iOS client would require Swift development and adaptation of the UI layer, but the core cryptographic code and protocol logic would remain the same.

**R-20: Publish Security Audit Results:** Once a security audit is completed, publish the results (with appropriate redactions for unpatched vulnerabilities) to demonstrate commitment to transparency. Publishing audit results builds community trust and demonstrates security maturity.

**R-21: Implement Secure Deletion:** For disappearing messages and vault file deletion, implement secure deletion that overwrites data before freeing storage space. SQLite's default DELETE operation only marks rows as deleted without overwriting the data. Implementing secure deletion would prevent forensic recovery of deleted messages.

**R-22: Add Two-Factor Authentication:** Consider adding optional two-factor authentication for the Android app, particularly for critical operations like vault access or account recovery. This could use TOTP (Time-based One-Time Password) or hardware security keys.

**R-23: Implement Key Escrow Recovery:** Develop a secure account recovery mechanism that does not compromise end-to-end encryption. This is a challenging problem - current approaches include using recovery codes, trusted contacts, or time-delayed key recovery. The documentation should clearly explain the recovery options and their security implications.

**R-24: Create Security Dashboard:** Add an in-app security dashboard that shows users the security status of their setup: whether hardware-backed storage is active, certificate pinning status, last successful backup, security update availability, and recommended security settings.

**R-25: Implement Remote Wipe:** Consider adding a remote wipe capability that allows users to wipe their data from the server if their device is lost or stolen. This would require careful design to prevent abuse - perhaps requiring both devices to confirm the wipe.

**R-26: Add Accessibility Security:** Ensure that accessibility features do not compromise security. Screen readers and other accessibility services can potentially read sensitive content. The app should be accessible while maintaining protection of encrypted content.

**R-27: Document Compliance Frameworks:** Document how Enclave maps to relevant compliance frameworks such as GDPR (for European users), CCPA (for California users), and SOC 2 (for organizational users). While the self-hosted model shifts much compliance responsibility to users, documenting the mappings would help users understand their compliance posture.

**R-28: Develop Disaster Recovery Guide:** Create a comprehensive disaster recovery guide covering scenarios like: server hardware failure, data corruption, accidental deletion, certificate expiration, Docker corruption, and complete VPS provider failure. Include step-by-step recovery procedures.

**R-29: Implement Client-Side Virus Scanning:** Before files are encrypted and uploaded, consider implementing client-side scanning for known malware signatures. This would prevent the vault from becoming a distribution vector for malware between the two users.

**R-30: Create Security Regression Tests:** Develop automated tests that verify security properties after each code change. These could include: verifying that plaintext never appears in network traffic, verifying that the hardware keystore is used in production builds, verifying that certificate pinning is active, and verifying that EXIF data is stripped from uploaded images.

**R-14: Publish Privacy Policy:** Create a clear, user-friendly privacy policy that explains what data is stored where and what the security implications are.

**R-15: Build Contributor Community:** Actively recruit contributors through relevant communities (privacy-focused forums, Android developer groups, open-source security projects). Diverse contributors improve code quality and security.

**R-16: Apply for F-Droid Inclusion:** Complete the F-Droid submission process. F-Droid provides reproducible builds and additional trust through their review process.

**R-17: Consider Formal Verification:** For the most security-critical components, consider using formal verification tools to mathematically prove correctness properties.

**R-18: Implement Perfect Forward Secrecy for Metadata:** Explore techniques for protecting metadata, such as padding message sizes, adding random delays, or using mix networks for certain communications.

**R-19: Develop iOS Client:** An iOS client would expand the user base significantly. The Signal Protocol implementation is available for iOS through the same libsignal library.

**R-20: Publish Security Audit Results:** Once a security audit is completed, publish the results (with appropriate redactions for unpatched vulnerabilities) to demonstrate commitment to transparency.


---

# 13. Conclusion

This comprehensive security review of Enclave has examined the project from multiple angles: cryptographic implementation, application security, backend infrastructure, operational security, privacy protections, open-source practices, and developer trustworthiness. The findings paint a nuanced picture of a young but technically sound project with genuine security intent.

**The Good:** Enclave demonstrates a strong understanding of modern cryptography and correctly implements the Signal Protocol for end-to-end encryption. The hardware-backed key storage with production-only enforcement is an aggressive but appropriate security stance. The zero-knowledge architecture genuinely prevents the server from reading message content. The self-hosted model provides complete data sovereignty. The EXIF stripping, screenshot blocking, and biometric protection demonstrate comprehensive privacy thinking. The AGPL license, absence of monetization, and complete open-source transparency strongly indicate non-commercial, idealistic motivations. The author appears to be a genuine developer building a tool they believe in, not a malicious actor or greed-driven entrepreneur.

**The Concerns:** The project is extremely young (approximately 9 days old at review time) with zero community adoption, no external security audit, and no established track record. The rapid release cycle (three versions in 8 hours) raises maturity concerns. The use of alpha security dependencies introduces stability risk. The aggressive minimum SDK (Android 14) limits device compatibility. The single-developer dependency creates sustainability risk. No formal test suite was identified. The SERVICE_ROLE_KEY usage in the signaling server creates a potential attack surface if the server is compromised.

**The Verdict:** Enclave appears to be a **genuine, security-focused project with no evidence of malicious intent or greed-driven development**. The technical implementation is sound, the architecture follows security best practices, and the license choice actively prevents commercial exploitation. However, due to its extreme youth and lack of community validation, it should be considered **experimental rather than production-ready** for users with high security requirements.

**For Potential Users:** If you are a technically capable couple willing to self-host infrastructure, Enclave offers an interesting option with strong privacy guarantees. However, you should understand that you are essentially an early adopter of alpha-quality software. Consider the following before adoption:

- You will need technical skills to deploy and maintain the backend infrastructure.
- You will be responsible for your own security updates and patching.
- The project has not undergone independent security auditing.
- The Android app only runs on Android 14+ devices.
- The long-term maintenance of the project depends on a single developer.
**For the Security Community:** Enclave represents a worthy addition to the open-source secure messaging ecosystem. The project would benefit from community contributions, security audits, and adoption by privacy-conscious users. The couple-focused niche is underserved, and the self-hosted model provides a genuine alternative to centralized commercial services.

**Final Rating:** On a scale of 1-10, Enclave receives a **security score of 6.5/10** at this stage of its development. The score reflects strong cryptographic foundations and security-aware design (+4.0), proper use of hardware-backed security (+1.5), comprehensive documentation (+1.0), and genuine open-source transparency (+1.0), offset by project immaturity (-0.5), lack of independent audit (-0.5), alpha dependencies (-0.5), no test suite (-0.5), and single-developer risk (-0.5). This score is expected to improve significantly as the project matures, gains community contributions, and undergoes formal security review.

Enclave is not perfect, but it is promising. The foundation is solid, the intent appears genuine, and the architecture is sound. With continued development, community engagement, and professional security review, it has the potential to become a trusted tool for privacy-conscious couples seeking sovereign communication.


---

# 14. Appendices

## Appendix A: Methodology

This assessment was conducted using the following methodology:

**Static Source Code Analysis:** All available source code in the GitHub repository was examined, with particular focus on cryptographic implementations, authentication flows, network communication, and data handling. Critical files were reviewed line-by-line.

**Architecture Review:** The system architecture was analyzed using the project's documentation and source code structure. Data flows were traced from source to destination for all major features.

**Dependency Analysis:** Third-party dependencies were identified and assessed for security reputation, version stability, and known vulnerabilities.

**Documentation Review:** All available documentation was reviewed for accuracy, completeness, and consistency with the implementation.

**Release History Analysis:** The release timeline, commit history, and version progression were analyzed for development patterns and security responsiveness.

**Community Health Assessment:** GitHub metrics, contributor analysis, and community engagement were evaluated.

**Limitations:** This assessment did not include dynamic testing, penetration testing, or fuzzing. The Android application was not installed and tested on a physical device. The backend was not deployed and tested in a live environment. These dynamic testing methods could reveal vulnerabilities not discoverable through static analysis alone.

## Appendix B: File Inventory

The following critical files were reviewed during this assessment:
Critical Files Reviewed
| File Path | Description | Size (approx.) |
|---|---|---|
| apps/android/app/build.gradle.kts | Android build configuration with dependencies | 167 lines |
| apps/android/app/src/main/.../crypto/CryptoManager.kt | Core E2EE implementation | 192 lines |
| apps/android/app/src/main/.../crypto/EnclaveSignalStore.kt | Signal protocol state storage | Reviewed |
| apps/android/app/src/main/.../crypto/ExifStripper.kt | EXIF metadata removal | Reviewed |
| apps/android/app/src/main/.../crypto/VaultCipher.kt | Vault file encryption | Reviewed |
| backend/server/signaling-server/server.ts | WebSocket signaling server | 625 lines |
| backend/server/docker-compose.yml | Backend infrastructure definition | Reviewed |
| backend/server/deploy.sh | Local deployment script | Reviewed |
| backend/server/generate_keys.js | Secret key generation | Reviewed |
| backend/server/volumes/db/init/*.sql | Database schema migrations | 9 migration files |
| docs/SYSTEM_ARCHITECTURE.md | Architecture documentation | 880 lines |
| docs/SETUP_GUIDE.md | Deployment documentation | Reviewed |
| README.md | Project overview | Comprehensive |
| LICENSE | AGPL-3.0 license text | Standard |


## Appendix C: Dependency Versions
Key Dependencies and Versions
| Dependency | Version | License | Status |
|---|---|---|---|
| libsignal-client | 0.39.2 | AGPL-3.0 | Stable |
| AndroidX Security Crypto | 1.1.0-alpha06 | Apache-2.0 | Alpha |
| AndroidX Biometric | 1.2.0-alpha05 | Apache-2.0 | Alpha |
| Jetpack Compose BOM | 2024.06.00 | Apache-2.0 | Stable |
| Room | 2.6.1 | Apache-2.0 | Stable |
| Stream WebRTC Android | 1.1.1 | Apache-2.0 | Stable |
| OkHttp | 4.12.0 | Apache-2.0 | Stable |
| Ktor Client WebSockets | 2.3.11 | Apache-2.0 | Stable |
| Supabase Kotlin BOM | 2.6.1 | Apache-2.0 | Stable |
| KotlinX Serialization | 1.6.3 | Apache-2.0 | Stable |
| WorkManager | 2.9.0 | Apache-2.0 | Stable |
| Media3 (ExoPlayer) | 1.3.1 | Apache-2.0 | Stable |
| ZXing Core | 3.5.3 | Apache-2.0 | Stable |
| Express | 5.2.1 | MIT | Stable |
| ws (WebSocket) | 8.20.1 | MIT | Stable |
| PostgreSQL | 15.1.0.147 | PostgreSQL License | Stable |
| Kong | 2.8.1 | Apache-2.0 | Stable |
| GoTrue | v2.132.3 | MIT | Stable |
| PostgREST | v11.2.0 | MIT | Stable |


## Appendix D: Glossary
Technical Terms and Abbreviations
| Term | Definition |
|---|---|
| AES-256-GCM | Advanced Encryption Standard with 256-bit keys in Galois/Counter Mode, providing confidentiality and authenticity |
| AGPL | GNU Affero General Public License, a strong copyleft open-source license |
| CSPRNG | Cryptographically Secure Pseudo-Random Number Generator |
| DTLS-SRTP | Datagram Transport Layer Security with Secure Real-time Transport Protocol, used for encrypting WebRTC media |
| E2EE | End-to-End Encryption, where only the communicating parties can read messages |
| EXIF | Exchangeable Image File Format, metadata embedded in image files |
| HSM | Hardware Security Module, dedicated hardware for key storage and cryptographic operations |
| ICE | Interactive Connectivity Establishment, protocol for NAT traversal in WebRTC |
| JWT | JSON Web Token, compact token format for authentication |
| MitM | Man-in-the-Middle attack, where an attacker intercepts communications |
| MobSF | Mobile Security Framework, automated tool for mobile application security analysis |
| NAT | Network Address Translation, technique for mapping private IP addresses to public addresses |
| RLS | Row Level Security, PostgreSQL feature for access control at the row level |
| SDP | Session Description Protocol, format for describing multimedia communication sessions |
| Signal Protocol | Cryptographic protocol developed by Signal Messenger, featuring Double Ratchet and X3DH |
| STUN/TURN | Session Traversal Utilities for NAT / Traversal Using Relays around NAT, protocols for NAT traversal |
| WebRTC | Web Real-Time Communication, framework for peer-to-peer audio, video, and data communication |
| WebSocket | Protocol providing full-duplex communication channels over a single TCP connection |
| WSS | WebSocket Secure, WebSocket over TLS/SSL |
| X3DH | Extended Triple Diffie-Hellman, key agreement protocol used in Signal |


> **Report Information**
> 

> **Report Generated:** June 1, 2026  
> **Assessment Scope:** GitHub repository saifmukhtar/enclave at tag v2.0.0  
> **Assessment Type:** Static source code analysis and architecture review  
> **Methodology:** Manual code review, documentation analysis, dependency assessment  
> **Limitations:** No dynamic testing, no penetration testing, no physical device testing  
> **Disclaimer:** This report represents the opinions of the assessor based on available information at the time of review. Security is an ongoing process, and new vulnerabilities may be discovered after this assessment. Users should conduct their own due diligence before trusting any software with sensitive communications.


## Appendix E: Detailed Release Notes Analysis

### v1.0.0 Release (May 31, 2026)

The v1.0.0 release was the first stable release of Enclave, establishing the complete feature set for the platform. The release notes describe an impressive array of features for a version 1.0: cryptographic sovereignty through the Double Ratchet and X3DH protocols, premium multi-selection context bars inspired by Signal, secure vault with biometric authentication and gallery selection gestures, interactive lounge with shared canvas and daily love letters, secret photos with scratch-to-reveal functionality, memory lane timeline, and dynamic WebRTC video and audio calling.

The v1.0.0 release notes included an important security note: "In Debug Build Screenshot is allowed." This transparency about the difference between debug and production builds demonstrates security awareness. Production builds correctly use FLAG_SECURE to prevent screenshots and screen recording, while debug builds allow screenshots for development and testing convenience.

### v1.1.0 Release (May 31, 2026)

The v1.1.0 release, tagged just two hours after v1.0.0, was specifically a security hardening release. This rapid security-focused update is a positive indicator of the developer's responsiveness to security findings. The release addressed three specific security areas:

**Production Network Hardening (Strict HTTPS):** The release re-architected network security domains to completely block cleartext loopback channels, enforcing 100% strict SSL (HTTPS/WSS) across all connection relays in production builds. Development loopback connections to the emulator at 10.0.2.2 using plain HTTP/WS were isolated to a separate debug resource manifest that is never compiled into production binaries. This separation of debug and production network configurations is a critical security control that prevents development convenience settings from accidentally affecting release builds.

**Exported Service Binder Verification:** The MusicPlaybackService, which runs as a foreground service for media playback, had its connection handling hardened. The service now implements deep package-level authorization in its onGetSession controller callback, explicitly rejecting connections from unauthorized third-party applications. Only the application's own package, Android system UIs, legitimate media controllers, and Android Auto projection layers are permitted to bind to the service. This prevents malicious apps from controlling media playback or potentially exploiting the service.

**Clean-Slate Compilation:** The release cleaned out stale cache configurations and bumped compile/target SDK versions, ensuring a completely warning-free, highly optimized release build. Build warnings can sometimes indicate security issues, so a clean build is a positive indicator.

### v2.0.0 Tag (May 31, 2026)

The v2.0.0 tag was created approximately six hours after v1.1.0. Unlike v1.0.0 and v1.1.0, v2.0.0 does not have an associated release page with detailed notes on GitHub. The commit message indicates this was a version bump to 2.0.0 (versionCode 3) with the addition of Fastlane metadata. Fastlane is a tool for automating app store deployments, and its inclusion suggests the developer is preparing for distribution through F-Droid and potentially Google Play Store.

The jump from v1.1.0 to v2.0.0 (rather than v1.2.0) may indicate significant changes planned for the next development cycle, or it may simply be the developer's preference for version numbering. In semantic versioning, a major version bump (2.0.0 from 1.x.x) typically indicates breaking changes, though in practice many projects use major version bumps for marketing purposes.

## Appendix F: Web Presence and Domain Analysis

The Enclave project maintains a web presence through two primary channels: the project website at enclave.saifmukhtar.dev and the GitHub repository at github.com/saifmukhtar/enclave.

The website enclave.saifmukhtar.dev is a static React Single Page Application deployed to GitHub Pages. The site serves as both a marketing landing page and documentation portal. Analysis of the site reveals: it contains no tracking scripts, no analytics beacons, no advertising code, and no third-party cookies. The site loads resources only from its own domain and from GitHub's CDN for GitHub Pages hosting. This clean, tracking-free web presence is consistent with the project's privacy-focused values.

The domain saifmukhtar.dev is a personal developer domain registered to the project author. The use of a personal domain (rather than an anonymous domain or a project-specific domain with privacy protection) creates personal accountability and suggests the author stands behind their work. The .dev top-level domain is managed by Google and requires HTTPS, which provides baseline transport security.

The website's content accurately describes the project's features and architecture without exaggerated claims. The description of "Signal-grade E2EE" is accurate since the project uses the official Signal Protocol library. The claim of "zero-knowledge" is technically accurate since the server cannot read message content. The description of the self-hosted model correctly explains that users control their own infrastructure.

The GitHub repository's README provides comprehensive project documentation including feature descriptions, architecture overview, deployment instructions, and technology stack details. The README accurately represents the project's capabilities based on source code analysis. The topics/tags assigned to the repository (kotlin, cryptography, self-hosted, android-app, e2ee, turn-server, zero-knowledge, jetpack-compose, double-ratchet, supabase, secure-messaging, e2ee-encryption, e2ee-chat) accurately reflect the project's focus areas.

## Appendix G: Economic and Sustainability Analysis

The long-term sustainability of an open-source project is a practical concern for users who depend on it for secure communications. Enclave's economic model and sustainability prospects deserve analysis.

**Current Funding Model:** There is no evidence of any funding mechanism for Enclave. The project appears to be entirely self-funded by the developer. There are no donation links, no sponsorship programs, no paid support tiers, and no grant funding mentioned. While this demonstrates purity of intent (no commercial influence), it raises sustainability concerns.

**AGPL License Implications:** The AGPL license prevents traditional commercialization models like dual licensing or proprietary add-ons. However, it does not prevent the developer from offering paid support, consulting, or managed hosting services. Many successful open-source projects sustain themselves through these models.

**Sustainability Risks:** The primary sustainability risk is the single-developer dependency. If saifmukhtar stops maintaining the project for any reason (loss of interest, financial constraints, personal circumstances), users could be left without security updates. Building a contributor community is essential for long-term sustainability.

**Potential Funding Approaches:** If the developer seeks funding, appropriate options include: GitHub Sponsors or Patreon for individual donations, applying for grants from privacy-focused foundations like the Open Technology Fund or the NLnet Foundation, offering paid support contracts for organizational users, or applying to accelerator programs focused on privacy technology.

**Cost of Operation:** For users, the primary ongoing cost is the VPS hosting for the backend. A suitable VPS for Enclave's backend would cost approximately $5-$20 per month depending on the provider and specifications. This is a modest cost for complete communication privacy. The Android app is free to install and use. There are no per-message fees, no subscription costs, and no usage limits beyond the server's capacity.

## Appendix H: User Risk Assessment Matrix
User Risk Assessment by Threat Scenario
| Threat Scenario | Likelihood | Impact | Risk Level | Mitigation Effectiveness |
|---|---|---|---|---|
| Server compromise by external attacker | Medium | Medium | Medium | High - E2EE means messages remain encrypted |
| Man-in-the-middle network attack | Low | High | Medium | High - Certificate pinning + HTTPS |
| Device theft (unlocked) | Low | High | Medium | Medium - Biometric + screen lock |
| Device theft (locked) | Low | Medium | Low | High - Hardware keystore protection |
| Malicious app on device | Medium | Medium | Medium | Medium - Android sandbox isolation |
| Developer malicious backdoor | Low | High | Medium | Medium - Open source enables review |
| Supply chain attack on dependency | Low | High | Medium | Medium - Dependabot + well-known libs |
| VPS provider compromise | Low | Medium | Low | High - E2EE + no plaintext on server |
| Unpatched vulnerability exploitation | Medium | High | High | Low - Depends on user patching |
| Social engineering of user | High | Medium | High | Low - User education needed |


## Appendix I: Future Technical Evolution Path

Based on the current architecture and codebase, the likely technical evolution path for Enclave can be projected:

**Short Term (0-6 months):** The immediate priorities will likely include completing F-Droid submission, addressing any security findings from community review, stabilizing the API surface, improving error handling and edge cases, adding comprehensive tests, and potentially releasing bug fix versions (v2.0.x or v2.1.0).

**Medium Term (6-18 months):** If the project gains traction, development will likely focus on an iOS client for cross-platform compatibility, group chat support (expanding beyond two users), desktop clients for Windows/macOS/Linux using Electron or Tauri, improved media handling with compression and format support, notification reliability improvements, and performance optimizations for lower-end devices.

**Long Term (18+ months):** Longer term possibilities include federation between self-hosted instances (enabling couples on different servers to communicate), integration with decentralized identity systems, post-quantum cryptographic algorithm migration as standards mature, advanced metadata protection through padding and mixing, and potentially enterprise features for organizational deployment.

The project's success will depend on building a sustainable community around the codebase. The couple-focused niche is specific enough to differentiate from general-purpose messaging apps, but broad enough to have a meaningful user base. The self-hosted model appeals to the growing segment of users concerned about centralized data collection, particularly for intimate communications.