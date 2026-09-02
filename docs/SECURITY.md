# Security

> TLS certificate pinning, pairing authentication, credential storage, and end-to-end encrypted push notifications.

## 1. TLS Certificate Pinning

The lpm desktop generates a **self-signed ECDSA P-256 certificate** on first launch:
- Subject: `CN=lpm`, SAN: `lpm`
- Validity: ~10 years (never rotated)
- Stored at `~/.lpm/remote-cert.pem` (mode 0600)

The Android app does **not use CA/PKI validation**. Instead, it pins the certificate by its SHA-256 fingerprint:

### Pinning Flow

1. **During QR pairing**: The QR code contains `f=<sha256_hex_fingerprint>`. On TLS handshake, the app computes `SHA-256(leaf_cert_DER)` and verifies it matches `f`. If mismatched, connection is aborted.

2. **During approval pairing**: Trust-On-First-Use (TOFU) — the app accepts the leaf cert on first successful pairing and saves its fingerprint.

3. **On subsequent connections**: The observed fingerprint MUST match the stored pin. If mismatched (e.g., cert was regenerated), connection is aborted with an `identity-changed` error requiring re-pairing.

### Implementation

```kotlin
class LpmTrustManager(private val pinnedFingerprint: String?) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain[0]
        val observed = leaf.encoded.sha256Hex()

        if (pinnedFingerprint != null && observed != pinnedFingerprint) {
            throw CertificateException("Certificate fingerprint mismatch")
        }
        // If no pin yet (TOFU), accept and caller saves observed fingerprint
    }
}
```

## 2. Pairing Authentication

### QR Code Pairing
- **One-time code**: 8-character alphanumeric (e.g., `AB12-CD34`), 10-minute expiry, single-use
- **Brute-force protection**: Server delays 500ms on wrong code before responding
- **Device replacement**: `replaces` field allows re-pairing to replace a previous device ID

### Approval Pairing
- **Match code**: 4-digit numeric displayed on both Mac and phone for visual confirmation
- **Approval window**: 30 seconds before timeout
- **Platform restriction**: Only supported on macOS (not Linux headless hosts)

### Token Exchange
On successful pairing, the server returns:
```json
{
  "deviceId": "<uuid>",       // Unique device identifier
  "token": "<base64_token>"   // 32-byte cryptographically random bearer token
}
```

The server stores only `sha256(token)` — the raw token exists only on the phone.

## 3. Credential Storage

All secrets are stored using Android Keystore with hardware-backed keys where available:

| Secret | Storage | Accessibility |
|--------|---------|---------------|
| Device credential (`deviceId` + `token`) | EncryptedSharedPreferences | After first unlock |
| TLS cert fingerprint | EncryptedSharedPreferences | After first unlock |
| AES-256 push key (32 bytes) | Android Keystore | After first unlock |

### Key Properties
- **Hardware-backed**: On devices with StrongBox or TEE, the AES push key is stored in secure hardware
- **No export**: Keys are non-extractable from the Keystore
- **Encrypted at rest**: EncryptedSharedPreferences uses AES-256-SIV for keys and AES-256-GCM for values

## 4. Connection Authentication

On every reconnect after initial pairing:

```
Client                          Server
  │                               │
  │──── WSS TLS Handshake ───────►│
  │     (verify pinned cert)      │
  │                               │
  │──── auth ────────────────────►│  {"t":"auth","deviceId":"...","token":"..."}
  │                               │
  │◄──── ready ──────────────────│  {"t":"ready","serverId":"..."}
  │                               │
```

- Auth must arrive within **20 seconds** of connection or the socket is dropped
- Server hashes the provided token with SHA-256 and matches against stored `token_hash`
- Active connections are re-verified every **5 seconds** — if the device is removed from desktop settings, the socket is dropped immediately

## 5. End-to-End Encrypted Push Notifications

### Key Generation
- The phone generates a single 32-byte AES-256 key: `SecureRandom().nextBytes(32)`
- This key is sent to the Mac during push token registration and stored in both:
  - Android Keystore (phone)
  - `~/.lpm/remote.json` device entry (Mac, as `push_key` base64)

### Encryption (Mac-side)
- Algorithm: **AES-256-GCM**
- Plaintext: JSON payload (project name, terminal, status, etc.)
- Output format: `nonce(12 bytes) || ciphertext || tag(16 bytes)`
- Encoded as standard Base64 → `blob` field

### Relay (Cloud)
- Desktop POSTs to `https://lpm.cx/api/push`:
  ```json
  {
    "token": "<fcm_token_hex>",
    "env": "production",
    "blob": "<base64_sealed_box>",
    "type": "alert" | "background",
    "collapseId": "<sha256_prefix_60_chars>"
  }
  ```
- The relay is **stateless** and **zero-knowledge** — it cannot read notification content
- It only holds FCM/APNs signing keys for delivery

### Decryption (Android)
```kotlin
fun decrypt(blob: String, key: ByteArray): String {
    val combined = Base64.decode(blob, Base64.DEFAULT)
    val nonce = combined.sliceArray(0 until 12)
    val ciphertextAndTag = combined.sliceArray(12 until combined.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val spec = GCMParameterSpec(128, nonce)  // 128-bit tag
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
    return String(cipher.doFinal(ciphertextAndTag), Charsets.UTF_8)
}
```

### Notification Types

**Alert notification** (visible):
```json
{
  "serverId": "<mac-uuid>",
  "project": "my-web-app",
  "target": "terminal",
  "terminal": "Claude Code",
  "terminalId": "my-web-app-1",
  "status": "Waiting",
  "ts": 1725234567890,
  "key": "agent_pane_1"
}
```
Rendered as: **my-web-app** — Claude Code — Agent is waiting for you

**Withdrawal notification** (silent):
```json
{
  "serverId": "<mac-uuid>",
  "clear": [{"project": "my-web-app", "key": "agent_pane_1"}]
}
```
Used to dismiss/remove previously delivered notifications when the agent moves on.

### Collapse Behavior
The `collapseId` field (SHA-256 prefix of `serverId|project|key`) ensures that repeated alerts for the same terminal pane **replace** each other rather than stacking up.

## 6. Network Security

| Threat | Mitigation |
|--------|-----------|
| Man-in-the-middle | TLS with certificate pinning (SHA-256 fingerprint) |
| Stolen token replay | Token is hashed server-side; revocable from desktop settings |
| Brute-force pairing | 500ms delay on wrong code; 10-minute code expiry |
| Push content exposure | AES-256-GCM E2EE; relay is zero-knowledge |
| Device theft | Credentials protected by Android Keystore (hardware-backed) |
| Cert rotation | Pinned fingerprint checked on every connect; mismatch → re-pair |
| Session hijacking | Active sessions re-verified every 5s against device registry |
