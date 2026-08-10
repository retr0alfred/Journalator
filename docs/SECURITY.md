# Journalator — security model

This document is the reference for how the app protects entries, what it deliberately does
not protect against, and why each choice was made. The plain-language version lives in the
[README](../README.md); this one assumes you read code.

---

## 1. The requirement that shapes everything

> Writing today's entry must **not** require the passcode.
> Reading any past entry **must**.

A single symmetric key cannot express that. Any key capable of encrypting today is capable of
decrypting yesterday, so a symmetric design would have to keep the key resident all day, and
"resident all day" is the same thing as "no protection at all".

The answer is a hybrid envelope: sealing uses public material, opening uses private material,
and the private material is locked behind the passcode.

---

## 2. Key hierarchy

```
                            passcode (CharArray, never a String)
                                   │
                    PBKDF2-HMAC-SHA256, 32-byte salt,
                    iterations calibrated to 500-800 ms
                    (floor 210 000, ceiling 600 000)
                                   │
                                   ▼
                          KEK  (256-bit, transient)
                                   │
                       AES-256-GCM, random 12-byte IV
                                   │
                                   ▼
   vault.cfg  ┌──────────────────────────────────────────────────────┐
              │  publicKeyDer      (plaintext — it is not a secret)  │
              │  kdfSalt           (plaintext)                        │
              │  kdfIterations     (plaintext)                        │
              │  wrappedPrivateKey = AES-GCM(KEK, PKCS#8 private key) │
              │  biometricBlob?    (optional second wrapping)         │
              └──────────────────────────────────────────────────────┘
                                   │
                       unlock ⇒ RSA private key (in memory only)
                                   │
   ┌───────────────────────────────┴───────────────────────────────┐
   │                                                               │
SEALING (public key only)                          OPENING (private key)
   │                                                               │
   ▼                                                               ▼
content key ← random 256-bit, per entry                  RSA-OAEP unwrap
   │                                                               │
AES-256-GCM(content key, entry)                        AES-256-GCM open
   │                                                               │
   ▼                                                               ▼
entries row: { wrappedKey, iv, ciphertext }              plaintext entry


   TODAY'S DRAFT — the one exception
   ─────────────────────────────────
   AndroidKeyStore AES-256-GCM key
     setUserAuthenticationRequired(false)   ← so writing needs no passcode
     setRandomizedEncryptionRequired(true)  ← platform picks the IV; no reuse
     StrongBox when the device has it, TEE otherwise
   Hardware-bound: readable by this app, on this device, and nowhere else.
```

### Algorithm choices, and why not the obvious alternative

| Choice | Alternative rejected | Reason |
|---|---|---|
| RSA-3072 (2048 fallback) | X25519 / ECIES | JCA's XDH needs API 31. This app supports API 24. |
| Software RSA keypair | AndroidKeyStore keypair | A keystore key is protected by the OS and the device. This key must be protected by something the *user* knows, so a stolen unlocked phone still cannot yield the archive. |
| PBKDF2-HMAC-SHA256, hand-rolled on `Mac` | `SecretKeyFactory("PBKDF2WithHmacSHA256")` | That algorithm arrived on Android at API 26. Verified against published vectors in `Pbkdf2Test`. |
| PBKDF2 | Argon2id / scrypt | Both need a native library. No `.so` in this app: smaller APK, no 16 KB page-size migration, no build rot. PBKDF2 with a calibrated iteration count is the strongest thing available without one. |
| `RSA/ECB/OAEPPadding` + explicit `OAEPParameterSpec` | `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` | Providers disagree about the shorthand: several read it as SHA-256 for the label with MGF1 still on SHA-1. Naming both digests makes the wire format provider-independent, which a ten-year file format requires. |
| GCM tag appended to ciphertext | Separate `tag` column | That is how the JCE returns it. Splitting it only creates a way to reassemble it wrongly. |

### Iteration calibration

A fixed iteration count is wrong on a ten-year horizon: punishing on a 2018 budget phone,
insultingly cheap on a 2030 one. `Pbkdf2.calibrateIterations` times a small sample at setup
and scales to a 650 ms target, clamped to [210 000, 600 000]. The chosen count is stored in
the header — it has to be, or the file could not be opened again — and it is authenticated
in the backup format so it cannot be quietly reduced.

---

## 3. Verification, lockout and wiping

**There is no stored password hash and no verifier string.** Verification *is* the GCM tag
check on the wrapped private key: `AEADBadTagException` means the passcode was wrong. There
is nothing on disk for an attacker to attack offline except the wrapped key itself, and no
partial plaintext is ever produced — `doFinal` returns the whole key or throws.

**Backoff is persisted** in `failures.bin`, separate from `vault.cfg` so a write during a
failed unlock can never damage the wrapped key:

| Consecutive failures | Wait |
|---|---|
| 1–4 | none |
| 5 | 5 s |
| 6 | 15 s |
| 7 | 60 s |
| 8 | 5 min |
| 9 and beyond | 30 min |

Force-stopping the app does not reset this, which is the entire point; `BackoffTest` covers
the simulated-process-death case explicitly.

**Wipe-after-failures** is off by default, threshold 10, and turning it on requires typing
the word ERASE into a dialog that states the consequence without hedging.

---

## 4. Memory hygiene

- Key material is held in `ByteArray`/`CharArray` and zero-filled in `finally` blocks
  (`SecureMemory`). The KEK, the PKCS#8 bytes and every per-entry content key are wiped
  immediately after use.
- `Vault.unlock` wipes the derived KEK and the decrypted PKCS#8 whether it succeeds or throws.
- The view model holds the decrypted private key in one nullable field. `lock()` clears it
  along with every decrypted entry, and `lock()` runs on `ON_STOP`.
- Passcode comparison during setup is constant-time (`SecureMemory.contentEquals`).

### The one honest limitation

**Passphrase entry cannot avoid `String`.** Android's IME hands text to an application as
immutable `String` instances, so the characters typed into a passphrase field remain on the
heap until garbage collection, unreachable and un-erasable by the app. This is a platform
boundary, not an oversight.

Mitigations: PIN entry is `CharArray` end to end and never touches a `String`; everything
below the UI layer is `String`-free; and the exposure is bounded by the app's process
lifetime, with `FLAG_SECURE` preventing the screen itself from being captured.

---

## 5. Storage

| What | Where | Protection |
|---|---|---|
| Entries, draft | `getDatabasePath()` — app-internal | App sandbox + envelope / hardware key |
| `vault.cfg`, `failures.bin` | `filesDir` — app-internal | App sandbox + passcode-wrapped key |
| UI preferences | `SharedPreferences` — app-internal | App sandbox. Nothing sensitive is stored here; the failure counter that gates access lives in its own file precisely so it is not sitting next to the theme setting. |
| `.jrnlx` backup | Wherever the user picks, via SAF | AES-256-GCM under a passphrase, header authenticated |
| Plain-text export | Wherever the user picks, via SAF | **None**, by definition, behind an explicit warning |

No hardcoded paths, no `MANAGE_EXTERNAL_STORAGE`, no storage permissions of any kind.

**Backup is disabled** three ways: `android:allowBackup="false"`, an empty
`fullBackupContent`, and an empty `dataExtractionRules`. No `adb backup`, no Google
auto-backup, no device-transfer copy.

**`FLAG_SECURE`** is set on the window in `onCreate` and never cleared: no screenshots, no
screen recording, and a blank thumbnail in the recents switcher.

### The `.jrnlx` container

```
"JRNLX1"      6 bytes  magic
version       1 byte
iterations    4 bytes  PBKDF2 rounds
salt          4-byte length + bytes
iv            4-byte length + 12 bytes
ciphertext    4-byte length + AES-256-GCM(JSON array of entries)
```

Everything before the ciphertext is passed to GCM as associated data. Rewriting the iteration
count to 1 — the obvious way to make an offline attack on the backup passphrase cheap — makes
the tag check fail. `JrnlxArchiveTest` asserts exactly that.

---

## 6. Threat model

### In scope, and defended

| Threat | Defence |
|---|---|
| Phone lost or stolen, screen locked | Archive is encrypted; private key exists only under the passcode. |
| Phone taken while unlocked, app in background | Auto-lock on `ON_STOP` (immediate by default) drops keys and plaintext. |
| Another app on the device | App sandbox; no exported components; no readable files outside the sandbox. |
| A file manager or a USB browse | Database is in app-internal storage and is ciphertext regardless. |
| Disk image of the unencrypted partition | Entries are envelope-encrypted; the draft is encrypted to a key that never leaves the security hardware. |
| Cloud/ADB backup exfiltration | Backup disabled three ways. |
| Screenshot or screen-recording malware | `FLAG_SECURE`. |
| Offline brute force of the passcode | PBKDF2 at a calibrated cost; strength meter states the estimated offline cracking time. |
| Online brute force at the keypad | Persisted exponential backoff; optional wipe. |
| Somebody adding their fingerprint to your unlocked phone | The biometric key sets `setInvalidatedByBiometricEnrollment(true)`, so a new enrolment destroys it. |
| Tampered backup file | GCM tag over ciphertext and header. |
| Network exfiltration by this app | There is no `INTERNET` permission; the OS refuses. A CI check fails the build if one ever appears. |

### Out of scope, stated plainly

- **Root, or a compromised OS.** Root can read another process's memory and the app sandbox.
  Nothing an unprivileged app does changes that.
- **The device seized while the archive is unlocked.** The plaintext is in RAM by definition.
- **Coercion.** There is no duress passcode and no hidden volume. Both are easy to implement
  badly and give a false sense of safety; neither survives an adversary who knows the app.
- **Keyloggers and malicious IMEs.** A keyboard that logs is a keyboard that sees the
  passcode. Use a keyboard you trust.
- **Traffic analysis, side channels, fault injection.** Not modelled.
- **Forgotten passcodes.** Not a threat — a design consequence. There is no recovery path,
  by construction, and the setup flow requires the user to acknowledge it.

---

## 7. What must never be "improved"

A short list for whoever touches this next.

1. **Do not add `android.permission.INTERNET`**, for any reason. There is a Gradle check that
   fails the build; do not delete it either.
2. **Do not swap RSA-OAEP for ECIES/X25519.** It breaks API 24 and it breaks every existing
   `.jrnlx` file.
3. **Do not add a search index.** An index is a plaintext keyword list of every entry.
4. **Do not re-encrypt the corpus on a passcode change.** Entries are wrapped to the public
   key, which never changes; a passcode change is O(1) and cannot lose data halfway through.
5. **Do not make biometrics the only key holder.** The passcode path is the recovery route.
6. **Do not move sealing to `WorkManager`.** Doze will defer it, and it will fail silently.
7. **Do not store word counts or moods in the clear** unless the user has opted into the
   heat-map. How much you wrote on which day is itself information.
