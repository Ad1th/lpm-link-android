# lpm Link for Android

**The Android companion app for [lpm](https://lpm.cx)** — control your Mac dev projects, AI coding agents, and terminals from your Android phone.

> lpm Link connects to your Mac's running lpm desktop app over a secure WebSocket connection and lets you monitor, control, and interact with everything — projects, terminals, AI agents, git, automations — from your phone.

---

## What is lpm?

[lpm](https://github.com/gug007/lpm) is a native macOS desktop app for managing dev projects. It lets you start/stop services, run AI coding agents (Claude Code, Codex, Gemini CLI), duplicate projects into parallel worktrees, and manage everything from a single workspace.

**lpm Link** is the companion mobile app. The iOS version already exists — this is the **Android** counterpart.

## How it works

```
┌─────────────────────────────────────┐
│          Mac (lpm desktop)          │
│  Tauri 2 app (React/TS + Rust)     │
│                                     │
│  Embedded TLS WebSocket Server      │
│  Port 8765 (wss://)                │
│  Self-signed ECDSA P-256 cert      │
└──────────────┬──────────────────────┘
               │ Single full-duplex
               │ WebSocket connection
               │ (LAN or Tailscale VPN)
┌──────────────▼──────────────────────┐
│      Android Phone (lpm Link)       │
│  Kotlin / Jetpack Compose           │
│  Pure WebSocket client              │
│  xterm.js terminal in WebView       │
└─────────────────────────────────────┘
```

**Key insight**: There are zero HTTP/REST calls. The entire protocol runs over a **single `wss://` WebSocket connection** carrying JSON messages. The Android app is a pure client — it doesn't run any projects itself.

## Features

- **QR Code Pairing** — Scan a QR code on your Mac to connect instantly
- **mDNS Discovery** — Find nearby Macs on your local network automatically
- **Project Dashboard** — View all projects, start/stop with one tap
- **Live Terminal** — Full xterm.js terminal with keyboard input and ANSI rendering
- **AI Agent Activity** — See Running/Waiting/Done/Error status across all agents
- **Interactive Input** — Type responses to agent prompts directly from your phone
- **Git Review & Ship** — View diffs, commit, push, create PRs, AI commit messages
- **Automations** — Monitor scheduled jobs, view output, send followups
- **Encrypted Push Notifications** — End-to-end encrypted alerts via FCM when agents need attention
- **Prompt History** — Search, organize, and reuse prompts across sessions
- **Session Memory** — View and edit work session logs shared between agents
- **Encrypted Notes** — Private notebook with chat threads and file attachments
- **Agent Usage Stats** — Token consumption, rate limit meters, cost estimates
- **Multi-Mac Support** — Pair with multiple Macs, switch between them
- **Tailscale Support** — Connect remotely over Tailscale VPN

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Build System | Gradle (Kotlin DSL) |
| WebSocket | OkHttp |
| QR Scanning | CameraX + ML Kit Barcode |
| mDNS Discovery | Android NsdManager |
| Terminal | xterm.js in Android WebView |
| Secure Storage | Android Keystore + EncryptedSharedPreferences |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Cryptography | javax.crypto (AES-256-GCM) |
| Serialization | kotlinx.serialization |
| DI | Hilt |
| Navigation | Compose Navigation |

## Project Structure

```
app/src/main/java/cx/lpm/link/
├── di/                     # Hilt dependency injection modules
├── network/                # WebSocket client, TLS pinning, host probing
├── pairing/                # QR scanner, mDNS browser, approval flow
├── projects/               # Project list, detail, services, profiles
├── terminal/               # xterm.js WebView, composer, keyboard
├── activity/               # Agent status feed, activity badges
├── git/                    # Diff viewer, branch management, PR creation
├── automations/            # Scheduled jobs, run history, followups
├── history/                # Prompt history with folders and search
├── memory/                 # Session memory viewer/editor
├── notes/                  # Encrypted notebook chats
├── usage/                  # Token stats, rate limit meters
├── config/                 # Service/action/profile/YAML editors
├── push/                   # FCM service, sealed push decryption
├── security/               # Keystore, cert pinning, AES decryptor
├── model/                  # Shared data models and protocol types
└── ui/                     # Shared UI components, themes, navigation
```

## Architecture Docs

- **[ARCHITECTURE.md](./docs/ARCHITECTURE.md)** — System architecture, protocol overview, security model
- **[PROTOCOL.md](./docs/PROTOCOL.md)** — Complete WebSocket message reference (80+ message types)
- **[SECURITY.md](./docs/SECURITY.md)** — TLS pinning, pairing flows, push encryption

## Building

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- JDK 17+
- Android SDK 35

### Setup
```bash
git clone https://github.com/Ad1th/lpm-link-android.git
cd lpm-link-android
```

Open in Android Studio and sync Gradle.

### Run
1. Ensure lpm desktop is running on your Mac
2. Build and run on your Android device/emulator
3. Scan the QR code from lpm's mobile pairing screen

## Relationship to the iOS App

This is a **standalone Android client** that implements the same WebSocket protocol as the iOS [lpm Link](https://github.com/Ad1th/lpm/tree/main/mobile) app. It shares zero code with the iOS version — it's a ground-up Kotlin/Compose implementation that speaks the same JSON-over-WebSocket protocol.

The desktop app requires **no modifications** — it already runs the WebSocket server that both iOS and Android clients connect to.

## License

MIT — see [LICENSE](./LICENSE)
