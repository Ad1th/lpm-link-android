# WebSocket Protocol Reference

> Complete reference for the lpm mobile WebSocket protocol. Every message type, JSON schema, and behavior documented.

## Overview

- **Transport**: `wss://` (WebSocket over TLS)
- **Port**: `8765` (prod), `8767` (dev = port+2)
- **Frame format**: JSON text frames with discriminator field `"t"`
- **Max frame size**: 16 MiB
- **No HTTP/REST endpoints** — everything goes over the single WebSocket

## Connection Handshake

### Pairing (first-time)

```json
// Client → Server: QR code pairing
{"t":"pair", "code":"AB12-CD34", "name":"Pixel 9", "replaces":"<old_device_uuid>?"}

// Client → Server: Approval pairing (mDNS discovery)
{"t":"pairRequest", "name":"Pixel 9", "replaces":"<old_device_uuid>?"}

// Server → Client: Approval pending
{"t":"pairPending", "matchCode":"5821"}

// Server → Client: Approval denied
{"t":"pairDenied", "reason":"declined"|"timeout"|"busy", "message":"..."}

// Server → Client: Pairing success (both flows)
{"t":"paired", "deviceId":"<uuid>", "token":"<base64>", "serverId":"<uuid>", "serverName":"MacBook Pro", "platform":"macos", "hosts":["192.168.1.50","100.64.0.1"]}

// Server → Client: Pairing error
{"t":"error", "error":"pairing rejected"|"pairing unavailable"}
```

### Authentication (reconnection)

```json
// Client → Server
{"t":"auth", "deviceId":"<uuid>", "token":"<base64>"}

// Server → Client: Success
{"t":"ready", "serverId":"<uuid>", "serverName":"MacBook Pro", "platform":"macos", "hosts":["192.168.1.50"]}

// Server → Client: Failure
{"t":"error", "error":"unauthorized"}
```

### Keepalive

```json
// Client → Server
{"t":"ping"}

// Server → Client
{"t":"pong"}
```

---

## Projects & Services

### List Projects
```json
// Request
{"t":"projects"}

// Response
{"t":"projects", "projects":[
  {
    "name": "my-app",
    "label": "My App",
    "root": "/Users/dev/my-app",
    "running": true,
    "parent": null,
    "worktree": false,
    "services": {"web": {"port": 3000, "running": true}, "api": {"port": 8080, "running": false}},
    "profiles": {"default": ["web","api"], "frontend": ["web"]},
    "actions": [{"key":"deploy","label":"Deploy","emoji":"🚀","confirm":true}],
    "ssh": null
  }
]}
```

### Start / Stop Project
```json
{"t":"start", "name":"my-app", "profile":"default"}
// → {"t":"start", "ok":true, "error":"..."}

{"t":"stop", "name":"my-app"}
// → {"t":"stop", "ok":true, "error":"..."}
```

### Toggle Individual Service
```json
{"t":"toggleService", "name":"my-app", "service":"web"}
// → {"t":"toggleService", "ok":true}
```

### Run Action
```json
// Terminal action (opens in desktop)
{"t":"runAction", "project":"my-app", "action":"deploy", "inputValues":{"env":"production"}, "confirmed":true}
// → {"t":"runAction", "ok":true}

// Background action (headless in Rust thread)
{"t":"runActionBackground", "project":"my-app", "action":"deploy", "inputValues":{}, "runId":"<uuid>"}
// → {"t":"runActionBackground", "ok":true}

// Poll background output
{"t":"actionBgOutput", "project":"my-app", "runId":"<uuid>"}
// → {"t":"actionBgOutput", "ok":true, "output":"...", "running":true, "exitCode":null}

// Cancel background action
{"t":"cancelActionBackground", "runId":"<uuid>"}

// List recent background runs
{"t":"backgroundRuns", "project":"my-app"}
```

### Project Management
```json
// Create from existing folder
{"t":"createProject", "name":"my-app", "root":"/Users/dev/my-app"}

// Create SSH project
{"t":"createSshProject", "name":"remote-app", "ssh":{"host":"server.com","user":"dev","port":22,"key":"~/.ssh/id_ed25519","dir":"/home/dev/app"}}

// Clone from git
{"t":"cloneProject", "name":"new-app", "url":"https://github.com/user/repo.git", "branch":"main", "destParent":"/Users/dev"}

// Rename
{"t":"renameProject", "project":"my-app", "name":"My Cool App"}

// Remove (deletes folder for duplicates)
{"t":"remove", "name":"my-app-copy-1"}
```

### Duplicate / Worktree
```json
{"t":"duplicate", "name":"my-app", "count":3, "labels":["auth","payments","ui"],
 "groupName":"sprint-5", "excludeUncommitted":false, "reinstallDeps":true,
 "pullLatest":false, "worktree":true,
 "runMode":"action", "action":"dev-server", "prompt":"Fix the auth bug"}

// Progress (streamed)
{"t":"duplicateProgress", "done":1, "total":3, "name":"my-app-auth"}
{"t":"duplicateProgress", "done":2, "total":3, "name":"my-app-payments"}

// Completion
{"t":"duplicate", "ok":true}

// Get saved defaults for duplicate modal
{"t":"duplicateDefaults"}
```

---

## Sidebar

```json
{"t":"sidebar"}
// → {"t":"sidebar", "order":["my-app","other-app"], "groups":[{"name":"Work","projects":["my-app"]}]}

{"t":"sidebarCreateFolder", "name":"Work"}
{"t":"sidebarRenameFolder", "name":"Work", "newName":"Personal"}
{"t":"sidebarDeleteFolder", "name":"Work"}
{"t":"sidebarMoveProject", "project":"my-app", "folder":"Work"}  // null to unfile
```

---

## Terminals & PTY Streaming

### List Terminals
```json
{"t":"terminals", "project":"my-app"}
// → {"t":"terminals", "project":"my-app", "terminals":[
//     {"id":"my-app-1", "label":"Claude Code", "pinned":true, "emoji":"🤖", "cli":"claude"}
//   ]}
```

### Subscribe to Terminal Stream
```json
// Subscribe (with optional resume offset)
{"t":"sub", "id":"my-app-1", "from":123456}

// Server seed (initial or resumed scrollback)
{"t":"seed", "id":"my-app-1", "cols":120, "rows":40, "data":"<scrollback>",
 "off":123800, "reset":false,
 "owner":{"kind":"mobile","id":"<deviceId>","label":"Pixel 9"},
 "draft":{"text":"partially typed prompt", "rev":3}}

// Live PTY output (continuous stream)
{"t":"o", "id":"my-app-1", "d":"<chunk>", "off":123900}

// Process exit
{"t":"exit", "id":"my-app-1", "code":0}

// Unsubscribe
{"t":"unsub", "id":"my-app-1"}
```

### Terminal Input
```json
// UTF-8 text input (keystrokes)
{"t":"in", "id":"my-app-1", "d":"ls -la\r"}

// Raw bytes (non-UTF-8): prefix with \0HEX: followed by hex
{"t":"in", "id":"my-app-1", "d":"\u0000HEX:1b5b41"}  // ← ESC [ A (arrow up)
```

### Terminal Control
```json
// Take control ownership
{"t":"claim", "id":"my-app-1"}

// Resize PTY (owner only — ignored if not owner)
{"t":"resize", "id":"my-app-1", "cols":80, "rows":24}

// Control ownership changed (broadcast)
{"t":"control", "id":"my-app-1", "owner":{"kind":"window"|"mobile", "id":"...", "label":"..."}}
// owner: null means no one owns it
```

### Terminal Management
```json
{"t":"newTerminal", "project":"my-app"}
{"t":"closeTerminal", "project":"my-app", "id":"my-app-1"}
{"t":"renameTerminal", "project":"my-app", "id":"my-app-1", "label":"New Name"}
{"t":"pinTerminal", "project":"my-app", "id":"my-app-1"}
{"t":"reorderTerminals", "project":"my-app", "order":["my-app-2","my-app-1"]}
```

### Slash Commands & Mentions
```json
{"t":"slash", "id":"my-app-1", "project":"my-app"}
// → {"t":"slash", "id":"my-app-1", "commands":[{"name":"/help","description":"Show help"}]}

{"t":"mentions", "project":"my-app"}
// → {"t":"mentions", "project":"my-app", "entries":[
//     {"path":"src/main.rs","dir":false,"changed":true},
//     {"path":"src/lib/","dir":true,"changed":false}
//   ]}
```

### File Upload
```json
{"t":"upload", "id":"my-app-1", "data":"<base64>", "mime":"image/png", "name":"screenshot.png", "reqId":"abc"}
// → {"t":"upload", "id":"my-app-1", "ok":true, "path":"/tmp/lpm-upload/screenshot.png", "reqId":"abc"}
```

---

## Composer

### Draft Sync
```json
// Send draft to Mac (and other connected devices)
{"t":"composerDraft", "id":"my-app-1", "text":"fix the auth bug"}

// Receive draft from Mac or another phone
{"t":"composerDraft", "id":"my-app-1", "text":"fix the auth bug", "rev":5, "origin":"mac"|"<deviceId>"}
```

### AI Prompt Rewrite
```json
// Get available rewrite actions
{"t":"composerActions"}
// → {"t":"composerActions", "actions":[{"id":"shorten","label":"Make shorter","instruction":"..."}]}

// Request transform
{"t":"transform", "reqId":"abc", "project":"my-app", "instruction":"Make more concise", "text":"...", "variants":3}

// Streamed results
{"t":"transform", "reqId":"abc", "idx":0, "ok":true, "text":"..."}
{"t":"transform", "reqId":"abc", "idx":1, "ok":true, "text":"..."}
{"t":"transformDone", "reqId":"abc", "ok":true}
```

---

## Agent Status

```json
// Query status badges
{"t":"status", "project":"my-app"}
// → {"t":"status", "project":"my-app", "status":[
//     {"key":"claude_pane_1", "value":"Waiting", "icon":"⏳", "color":"amber",
//      "priority":1, "timestamp":1725234567890, "agentPID":12345, "paneID":"my-app-1"}
//   ]}

// Clear a dismissed status
{"t":"clearStatus", "project":"my-app", "paneId":"my-app-1", "value":"Done"}
```

---

## Git Review & Ship

```json
// Status scan
{"t":"git", "project":"my-app"}
// → {"t":"git", "project":"my-app", "ok":true, "isRepo":true,
//    "branch":"feature/auth", "detached":false, "hasUpstream":true,
//    "ahead":3, "behind":0, "defaultBranch":"main", "ghCli":true,
//    "files":[{"path":"src/auth.rs","status":"modified","staged":false,"stamp":"abc123"}]}

// Single file diff
{"t":"gitDiff", "project":"my-app", "path":"src/auth.rs"}
// → {"t":"gitDiff", "project":"my-app", "path":"src/auth.rs", "ok":true,
//    "diff":"--- a/src/auth.rs\n+++ b/src/auth.rs\n...", "binary":false, "truncated":false}

// Batch diffs
{"t":"gitDiffs", "project":"my-app", "paths":["src/auth.rs","src/main.rs"]}

// Commit
{"t":"gitCommit", "project":"my-app", "message":"Fix auth flow", "files":["src/auth.rs"]}

// Push / Pull / Fetch
{"t":"gitPush", "project":"my-app"}
{"t":"gitPull", "project":"my-app"}
{"t":"gitFetch", "project":"my-app"}

// Branches
{"t":"gitBranches", "project":"my-app"}
{"t":"gitCheckout", "project":"my-app", "branch":"main", "remote":"origin"}
{"t":"gitCreateBranch", "project":"my-app", "name":"feature/payments"}

// Discard all changes
{"t":"gitDiscardAll", "project":"my-app"}

// Watch for file changes (subscribes to git-changed broadcasts)
{"t":"gitWatch", "project":"my-app"}
{"t":"gitUnwatch", "project":"my-app"}

// AI-generated commit message
{"t":"gitGenMessage", "project":"my-app", "files":["src/auth.rs"]}
// → {"t":"gitGenMessage", "ok":true, "message":"Fix authentication token validation"}

// AI-generated PR
{"t":"gitGenPr", "project":"my-app"}
// → {"t":"gitGenPr", "ok":true, "title":"Fix auth flow", "body":"## Changes\n..."}

// Create PR via gh CLI
{"t":"gitCreatePr", "project":"my-app", "title":"Fix auth flow", "body":"..."}
```

---

## Automations / Jobs

```json
// List all jobs
{"t":"jobs"}
// → {"t":"jobs", "ok":true, "jobs":[...]}

// Job run history
{"t":"jobHistory", "project":"my-app", "jobId":"daily-test"}

// Live output (poll)
{"t":"jobLiveOutput", "project":"my-app", "jobId":"daily-test"}

// Run / Stop
{"t":"runJob", "project":"my-app", "jobId":"daily-test"}
{"t":"stopJob", "project":"my-app", "jobId":"daily-test"}

// Enable / Disable
{"t":"setJobEnabled", "project":"my-app", "jobId":"daily-test", "enabled":false}

// Mark as read
{"t":"markJobSeen", "project":"my-app", "jobId":"daily-test", "at":1725234567890}
{"t":"markAllJobsSeen"}

// Continue conversation
{"t":"sendJobFollowup", "project":"my-app", "jobId":"daily-test",
 "at":1725234567890, "message":"Try a different approach",
 "agent":"claude", "model":"opus", "effort":"high"}

// Read/save/delete job config
{"t":"jobConfig", "project":"my-app", "jobId":"daily-test", "source":"project"}
{"t":"saveJob", "id":"daily-test", "source":"project", "project":"my-app", "job":{...}}
{"t":"deleteJob", "id":"daily-test", "source":"project", "project":"my-app", "deleteCopies":false}
```

---

## Message History

```json
// Simple search
{"t":"history", "project":"my-app", "q":"auth"}

// Record a sent prompt
{"t":"historyAdd", "project":"my-app", "id":"<uuid>", "label":"Claude Code", "text":"fix the auth bug"}

// Paginated query (keyset pagination)
{"t":"historyQuery", "project":"my-app", "search":"auth", "favoritesOnly":false,
 "folder":"work", "before":{"at":1725234567890,"seq":42}}
// → {"t":"historyQuery", "items":[...], "hasMore":true}

// Save draft
{"t":"historySaveDraft", "message":"partially typed...", "project":"my-app"}

// Favorites & folders
{"t":"historyToggleFavorite", "id":"<uuid>"}
{"t":"historySetFolder", "id":"<uuid>", "folder":"work"}
{"t":"historyDelete", "id":"<uuid>"}
{"t":"historyFolders"}
{"t":"historyCreateFolder", "name":"work"}
{"t":"historyDeleteFolder", "id":"<uuid>"}
```

---

## Session Memory

```json
// List sessions
{"t":"memory", "project":"my-app"}
// → {"t":"memory", "project":"my-app", "sessions":[
//     {"name":"auth-refactor","preview":"# Goal\nRefactor...","modifiedAt":1725234567890}
//   ]}

// Read full session
{"t":"memorySession", "project":"my-app", "name":"auth-refactor"}
// → {"t":"memorySession", "project":"my-app", "name":"auth-refactor", "ok":true, "content":"# Goal\n..."}

// Save (Compare-And-Swap to prevent concurrent overwrites)
{"t":"memorySave", "project":"my-app", "name":"auth-refactor", "content":"...", "baseline":"<prev_content_hash>"}
// → {"t":"memorySave", "ok":true}  or  {"t":"memorySave", "ok":false, "error":"conflict"}

// Delete
{"t":"memoryDelete", "project":"my-app", "name":"auth-refactor"}
```

---

## Encrypted Notes

```json
// List chats
{"t":"notesChats", "project":"my-app"}

// Create / rename / delete chat
{"t":"notesCreateChat", "project":"my-app", "title":"Design Notes"}
{"t":"notesRenameChat", "project":"my-app", "chatId":"<uuid>", "title":"New Title"}
{"t":"notesDeleteChat", "project":"my-app", "chatId":"<uuid>"}

// Messages (paginated, newest first)
{"t":"notesMessages", "project":"my-app", "chatId":"<uuid>", "limit":50, "beforeId":"<uuid>"}

// Add message (max 8MiB per attachment)
{"t":"notesAddMessage", "project":"my-app", "chatId":"<uuid>", "text":"Here's the design",
 "attachments":[{"name":"design.png","mimeType":"image/png","data":"<base64>"}]}

// Edit / delete message
{"t":"notesEditMessage", "project":"my-app", "id":"<uuid>", "text":"Updated text"}
{"t":"notesDeleteMessage", "project":"my-app", "id":"<uuid>"}

// Search across notes
{"t":"notesSearch", "project":"my-app", "query":"authentication", "limit":20}

// Download attachment
{"t":"notesAttachment", "project":"my-app", "hash":"<sha256_hex>"}
// → {"t":"notesAttachment", "ok":true, "data":"<base64>", "mimeType":"image/png"}
```

---

## Usage Stats & Rate Limits

```json
// Token usage stats
{"t":"stats", "days":7}  // 0 = all time
// → {"t":"stats", "ok":true, "stats":{...}}

// Rate limit meters
{"t":"limits"}
// → {"t":"limits", "ok":true, "limits":{...}, "claudeEnabled":true, "now":1725234567890}

// Text-to-speech
{"t":"ttsSpeak", "reqId":"abc", "text":"Hello world"}
// → {"t":"ttsSpeak", "reqId":"abc", "ok":true, "format":"aac", "audio":"<base64_aac>"}
```

---

## Services & Config

```json
// Service info
{"t":"services", "project":"my-app"}
// → {"t":"services", "project":"my-app", "ok":true, "running":true, "services":[...]}

// Service logs (tmux pane capture)
{"t":"serviceLogs", "project":"my-app", "paneIndex":0, "lines":100}
// → {"t":"serviceLogs", "project":"my-app", "paneIndex":0, "ok":true, "text":"..."}

// Read file (<1MB UTF-8)
{"t":"readFile", "project":"my-app", "path":"src/main.rs"}

// Config layers (project / repo / global)
{"t":"readConfig", "project":"my-app", "layer":"project"}
{"t":"saveConfig", "project":"my-app", "layer":"project", "content":"services:\n  web:\n    cmd: npm start"}

// CRUD for services, actions, profiles
{"t":"serviceBody", "project":"my-app", "key":"web"}
{"t":"saveService", "project":"my-app", "key":"web", "payload":{...}, "previousKey":"old-web"}
{"t":"deleteService", "project":"my-app", "key":"web"}

{"t":"actionBody", "project":"my-app", "key":"deploy"}
{"t":"saveAction", "project":"my-app", "key":"deploy", "payload":{...}, "section":"actions"}
{"t":"deleteAction", "project":"my-app", "key":"deploy"}

{"t":"saveProfile", "project":"my-app", "name":"frontend", "services":["web"], "previousName":"old-name"}
{"t":"deleteProfile", "project":"my-app", "name":"frontend"}

// Mac filesystem browsing
{"t":"listDirs", "path":"/Users/dev"}
{"t":"listSshHosts"}
```

---

## Push Token Registration

```json
{"t":"apnsToken",
 "token":"<fcm_registration_token_hex>",
 "env":"production",
 "key":"<base64_32_byte_aes_key>",
 "notify":{
   "waiting": true,
   "done": true,
   "error": true,
   "automationStarted": false,
   "automationDone": true,
   "automationError": true
 }}
// → {"t":"apnsToken", "ok":true}
```

---

## Server → Client Broadcast Events

These are unsolicited pushes from the server. The client should react by re-fetching the relevant data.

| Event `t` | Payload | Action |
|-----------|---------|--------|
| `projects-changed` | `{}` | Re-fetch `projects` |
| `status-changed` | `{project}` | Re-fetch `status` for that project |
| `jobs-changed` | `{}` | Re-fetch `jobs` |
| `git-changed` | `{project}` | Re-fetch `git` for that project |
| `memory-changed` | `{project}` | Re-fetch `memory` |
| `limits-changed` | `{limits, claudeEnabled, now}` | **Full snapshot included** — no re-fetch needed |

---

## mDNS Service Discovery

The desktop advertises via Bonjour/mDNS:

| Property | Value |
|----------|-------|
| Service type | `_lpm._tcp.local.` |
| Instance name | `<serverName> [<serverId_first_6>]` (max 63 bytes) |
| TXT `id` | Full `serverId` UUID |
| TXT `name` | User-visible machine name |
| TXT `v` | Protocol version (`"1"`) |
| TXT `rp` | `"1"` if approval pairing supported (Mac), `"0"` (Linux headless) |
| TXT `dev` | `"1"` on dev builds |

Android discovers via `NsdManager.discoverServices("_lpm._tcp.", NsdManager.PROTOCOL_DNS_SD, ...)`.
