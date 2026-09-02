# Architecture

> System architecture for lpm Link Android — how it connects to the lpm desktop app, the protocol it speaks, and how the internal modules are organized.

## 1. System Overview

lpm Link for Android is a **companion client** for the lpm macOS desktop app. It connects to the desktop over a single encrypted WebSocket connection and provides a mobile interface for all desktop features.

```
                    ┌─────────────────────────────┐
                    │     lpm Desktop (Mac)        │
                    │                              │
                    │  ┌────────────────────────┐  │
                    │  │ React/TS Frontend      │  │
                    │  │ (Tauri WebView)        │  │
                    │  └────────┬───────────────┘  │
                    │           │ Tauri IPC         │
                    │  ┌────────▼───────────────┐  │
                    │  │ Rust Backend            │  │
                    │  │                         │  │
                    │  │  ┌───────────────────┐  │  │
                    │  │  │ remote.rs          │  │  │
                    │  │  │ TLS WebSocket Srv  │  │  │
                    │  │  │ Port 8765          │──┼──┼──── wss:// ────┐
                    │  │  └───────────────────┘  │  │                │
                    │  │  ┌───────────────────┐  │  │                │
                    │  │  │ socketsrv.rs       │  │  │                │
                    │  │  │ Unix Socket Srv    │  │  │                │
                    │  │  │ ~/.lpm/lpm.sock    │  │  │                │
                    │  │  └───────────────────┘  │  │                │
                    │  └─────────────────────────┘  │                │
                    │                              │                │
                    │  ┌─────────────────────────┐  │                │
                    │  │ mDNS Advertiser        │  │                │
                    │  │ _lpm._tcp.local.        │  │                │
                    │  └─────────────────────────┘  │                │
                    └──────────────────────────────┘                │
                                                                    │
                    ┌───────────────────────────────────────────────▼┐
                    │         lpm Link (Android)                     │
                    │                                                │
                    │  ┌──────────────┐  ┌──────────────────────┐   │
                    │  │ LpmClient    │  │ UI Layer             │   │
                    │  │ (OkHttp WS)  │  │ Jetpack Compose      │   │
                    │  │              │──▶│                      │   │
                    │  │ TLS Pinning  │  │ Projects │ Terminal  │   │
                    │  │ Reconnection │  │ Activity │ Git      │   │
                    │  │ Msg Router   │  │ Jobs     │ History  │   │
                    │  └──────────────┘  └──────────────────────┘   │
                    │                                                │
                    │  ┌──────────────┐  ┌──────────────────────┐   │
                    │  │ NsdManager   │  │ FCM Service          │   │
                    │  │ (mDNS)      │  │ AES-256-GCM decrypt  │   │
                    │  └──────────────┘  └──────────────────────┘   │
                    └────────────────────────────────────────────────┘
```

## 2. Connection Lifecycle

### 2.1 Discovery & Pairing

There are two ways to initially pair with a Mac:

**QR Code Pairing** (primary):
1. Mac displays QR code containing `lpm://pair?p=<port>&c=<code>&h=<host>&h=<host>&f=<fingerprint>`
2. Android scans QR, extracts candidate hosts and cert fingerprint
3. Android races all hosts concurrently (6s timeout per probe)
4. Connects via WSS to the fastest responding host
5. Verifies TLS cert fingerprint matches `f=` from QR
6. Sends `{"t":"pair","code":"AB12-CD34","name":"Pixel 9"}`
7. Receives `{"t":"paired","deviceId":"...","token":"...","serverId":"..."}`
8. Stores credentials in Android Keystore

**mDNS Approval Pairing** (LAN-only):
1. Android browses `_lpm._tcp.local.` using NsdManager
2. Lists nearby Macs with their names
3. Connects and sends `{"t":"pairRequest","name":"Pixel 9"}`
4. Mac shows approval dialog with 4-digit match code
5. Both devices display code for user verification
6. User clicks Allow on Mac → `{"t":"paired",...}`

### 2.2 Authentication (Reconnection)

On all subsequent connections:
1. Android connects via WSS using stored host + port
2. Sends `{"t":"auth","deviceId":"<uuid>","token":"<b64>"}` within 20 seconds
3. Receives `{"t":"ready","serverId":"...","serverName":"...","hosts":[...]}`
4. Flushes any queued offline messages

### 2.3 Connection Maintenance

| Behavior | Value |
|----------|-------|
| Heartbeat interval | 20s (WebSocket ping) |
| Pong deadline | 10s |
| Foreground probe on app resume | 4s deadline |
| Backoff formula | `min(1.5 × 2^(attempt-1), 20) × random(0.85..1.15)` |
| Offline send queue | Up to 32 messages |
| Server device check | Every 5s (drops revoked devices) |

## 3. Module Architecture

### 3.1 Network Layer (`network/`)

```
network/
├── LpmClient.kt           # WebSocket lifecycle, reconnection, heartbeat
├── MessageRouter.kt        # Routes inbound JSON frames by "t" discriminator
├── TlsPinningFactory.kt    # Custom SSLSocketFactory with SHA-256 cert pinning
├── HostProbe.kt            # Race multiple host candidates concurrently
└── OfflineQueue.kt         # Buffer non-live messages while disconnected
```

**LpmClient** is the core networking class. It:
- Manages a single OkHttp WebSocket connection
- Handles TLS with custom `X509TrustManager` (pin-based, no CA validation)
- Implements exponential backoff reconnection
- Sends heartbeat pings every 20s
- Detects app foreground/background transitions and probes connection health
- Queues up to 32 messages while offline, flushes on reconnect
- Tracks per-terminal stream offsets for seamless PTY resumption

**MessageRouter** is a dispatcher that:
- Parses every inbound JSON frame
- Extracts the `"t"` discriminator field
- Routes to registered handlers (one per feature module)
- Handles `projects-changed`, `status-changed`, and other broadcast events

### 3.2 Security Layer (`security/`)

```
security/
├── KeystoreManager.kt      # Android Keystore wrapper for all secrets
├── CertPinStore.kt          # Store/verify TLS cert fingerprints
├── CredentialStore.kt       # Encrypted device credentials (deviceId + token)
├── SealedDecryptor.kt       # AES-256-GCM push payload decryption
└── PushKeyManager.kt        # Generate/store 32-byte AES push key
```

All secrets are stored in Android Keystore with `AFTER_FIRST_UNLOCK` accessibility:
- **Device credential**: `deviceId` + `token` (JSON blob, encrypted)
- **Cert pin**: SHA-256 hex fingerprint of Mac's TLS leaf certificate
- **Push key**: 32-byte AES-256 symmetric key for push notification decryption

### 3.3 UI Layer (Compose)

Each feature area follows the same pattern:
- **Screen** composable (top-level, receives nav events)
- **Store/ViewModel** (holds state, sends WebSocket messages, processes replies)
- **Sheet** composables (modal editors, forms)

```
projects/
├── ProjectsScreen.kt        # Sidebar-style project list with folders
├── ProjectDetailScreen.kt   # Services, terminals, actions for one project
├── ProjectsStore.kt         # State management, WebSocket request/response
└── DuplicateSheet.kt        # Project duplication form

terminal/
├── TerminalScreen.kt        # Compose wrapper hosting WebView
├── TerminalWebView.kt       # xterm.js bridge: feed output, capture input
├── TerminalComposer.kt      # Multi-prompt input bar with attachments
├── ControlHandoff.kt        # "Take control" banner for shared terminals
├── TerminalStore.kt         # Stream offsets, sub/unsub, control ownership
└── assets/terminal.html     # xterm.js bundle (ported from iOS)

activity/
├── ActivityScreen.kt        # Aggregated agent status feed
├── ActivityStore.kt         # Status tracking, badge management
└── UsageScreen.kt           # Rate limit meters + token usage charts
```

### 3.4 Terminal Architecture

The terminal is the most complex component. It uses xterm.js running in an Android WebView:

```
┌─────────────────────────────────────────┐
│           TerminalScreen.kt             │
│  ┌───────────────────────────────────┐  │
│  │       Android WebView             │  │
│  │  ┌─────────────────────────────┐  │  │
│  │  │     xterm.js (terminal.html) │  │  │
│  │  │                             │  │  │
│  │  │  term.write(bytes)  ◄───────┼──┼──┼── "o" frames (PTY output)
│  │  │  term.onData(text)  ────────┼──┼──┼──► "in" frames (keystrokes)
│  │  │  fitAddon.fit()     ────────┼──┼──┼──► "resize" frames
│  │  │                             │  │  │
│  │  └─────────────────────────────┘  │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │     TerminalComposer.kt           │  │
│  │  Multi-line input | Attachments   │  │
│  │  Slash commands | @-mentions      │  │
│  │  Keyboard row: Esc Ctrl Tab ↑↓←→  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

**Data flow**:
1. Subscribe: Client sends `{"t":"sub","id":"<termId>","from":<lastOffset>}`
2. Server replies with `seed` (initial scrollback or resume slice)
3. Server streams live `o` frames with `off` byte offsets
4. Client feeds chunks to xterm.js via JS bridge: `window.lpmFeed(base64)`
5. xterm.js `onData` callback sends keystrokes back via `{"t":"in","id":"<termId>","d":"..."}`

**Offset tracking**: The client persists the last-seen `off` value per terminal. On reconnect/foreground, it sends `sub` with `from=lastOffset`. If the server's 512KB ring buffer still contains the data, it sends a `seed` with `reset:false` (append-only resume). Otherwise `reset:true` (full re-render).

**Control ownership**: Only one surface (Mac desktop or one phone) can "own" a terminal at a time. The owner controls resize and receives keyboard focus. Other surfaces see the stream read-only with a "Take control" banner.

### 3.5 Push Notification Architecture

```
┌──────────┐     ┌──────────────┐     ┌──────┐     ┌─────────────┐
│ Mac      │────►│ Push Relay   │────►│ FCM  │────►│ Android     │
│ Desktop  │     │ lpm.cx/api/  │     │      │     │ LpmFcm-     │
│          │     │ push         │     │      │     │ Service.kt  │
│ AES-256  │     │ (stateless,  │     │      │     │             │
│ encrypt  │     │  can't read  │     │      │     │ AES-256     │
│ payload  │     │  content)    │     │      │     │ decrypt     │
└──────────┘     └──────────────┘     └──────┘     └─────────────┘
```

The push relay is **stateless** and **zero-knowledge** — it only routes opaque encrypted blobs. The AES-256 key never leaves the phone and Mac.

**Registration**: After every successful `auth`, the app sends:
```json
{
  "t": "apnsToken",
  "token": "<fcm_registration_token>",
  "env": "production",
  "key": "<base64_32_byte_aes_key>",
  "notify": { "waiting": true, "done": true, "error": true, ... }
}
```

**Decryption** (in `LpmMessagingService`):
1. Extract `blob` from FCM data payload
2. Base64 decode → `nonce(12) || ciphertext || tag(16)`
3. Decrypt with AES-256-GCM using key from Android Keystore
4. Parse JSON → build notification with project name, terminal name, status

## 4. Data Flow Patterns

### 4.1 Request-Response
Most messages follow request-response. Client sends `{"t":"projects"}`, server replies with `{"t":"projects","projects":[...]}`. The `"t"` field matches.

### 4.2 Server-Push Events
The server sends unsolicited broadcast events:
- `projects-changed` → client re-fetches `projects`
- `status-changed` → client re-fetches `status` for that project
- `jobs-changed` → client re-fetches `jobs`
- `git-changed` → client re-fetches `git` for that project
- `memory-changed` → client re-fetches `memory`
- `limits-changed` → contains full payload (no re-fetch needed)

### 4.3 Streaming
Terminal PTY output is continuously streamed via `o` frames. Composer drafts are bidirectionally synced via `composerDraft` frames. AI transform results stream as multiple `transform` frames followed by `transformDone`.

## 5. Offline Behavior

When the WebSocket is disconnected:
1. Non-live messages (project queries, config reads, etc.) are queued (max 32)
2. Live messages (keystrokes, resizes) are dropped (fire-and-forget)
3. On reconnect, queued messages are flushed after receiving `ready`
4. Terminal streams resume via offset-based `sub` with `from` parameter
5. All state is re-fetched (projects, status, jobs) to catch up

## 6. Threading Model

| Component | Thread |
|-----------|--------|
| OkHttp WebSocket | Background thread pool (OkHttp-managed) |
| Message routing | Background → Main via `Dispatchers.Main` |
| UI rendering | Main thread (Compose) |
| xterm.js | WebView thread (auto-managed by Android) |
| FCM service | Background (system-managed) |
| mDNS browsing | Background (NsdManager callbacks) |
| Host probing | Coroutine scope with parallel `async` per host |
