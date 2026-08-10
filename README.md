# Journalator

A single-entry-per-day journal for Android that works entirely offline. Writing today's page
never asks for a passcode. Reading anything you wrote before always does.

The app has **no internet permission at all**. Not "we choose not to use the network" — the
permission is absent from the manifest, so Android itself refuses every connection this
process could try to open. There is no account, no cloud, no analytics, no crash reporting
and no third-party SDK.

---

## How your entries are protected, in plain words

Think of it as a postbox.

**Anyone can post a letter.** The app carries a *public* key, which can lock things but
cannot unlock them. Every time a day is sealed, the app makes up a brand-new random key,
locks that day's writing with it, and then locks that key inside the postbox. All of this
works with the public key alone, which is why sealing a day never needs your passcode.

**Only you can empty the postbox.** Opening any sealed day needs the matching *private* key.
That private key is itself locked, with a key stretched from your passcode. No passcode, no
private key; no private key, no reading.

**Today is the exception, deliberately.** You have to be able to keep adding to today's page
all day without unlocking anything, so the unfinished draft is encrypted with a key held by
your phone's own security hardware. It never leaves the device and it never leaves this app.
If someone copies the database file off your phone, that draft is still noise to them.

**When a day ends, it seals itself.** Not on a timer or a background job — the next time you
open the app, any draft belonging to a day that is no longer today gets sealed into the
archive and the draft is shredded. You can also seal today early with the SEAL ENTRY button.

**Sealed entries cannot be edited.** There is no edit button anywhere, on purpose. A journal
you can quietly rewrite stops being a record of anything.

### There is no recovery

If you forget your passcode, your entries are gone. Permanently. Nobody — not you, not the
author of this app, not a court order — can recover them, because no copy of the key exists
anywhere outside your phone. The setup screen makes you tick a box saying you understand
this, and that box is not a formality.

Your one safety net is the encrypted backup file (below). Make one.

---

## What it does

| Screen | What happens there |
|---|---|
| **Setup** | Once, on first launch. Choose a passcode, confirm it, keys are generated. |
| **Write** | Home. Today's page, autosaved as you type. No passcode needed. |
| **Unlock** | The only door to the archive. Keypad or passphrase, biometrics if you enable them. |
| **Archive** | Past entries, reverse-chronological, plus a year calendar. Read-only. |
| **Settings** | Passcode, auto-lock, biometrics, text size, motion, backups, licences. |

Other behaviour worth knowing:

- **Screenshots and screen recording are blocked** app-wide, and the app's thumbnail in the
  recents switcher is blanked.
- **Auto-lock** when you leave the app. Immediate by default; 30 s and 2 min are available.
  Locking drops the private key and every decrypted entry from memory.
- **Rate limiting** after wrong passcodes: four free attempts, then 5 s, 15 s, 60 s, 5 min,
  30 min. The counter is on disk, so force-stopping the app does not reset it.
- **Optional wipe-after-failures**, off by default, behind a type-the-word-ERASE dialog.
- **Search** happens in memory while the archive is unlocked. Nothing is indexed to disk.

---

## Backups

The only file this app ever writes to shared storage is an encrypted archive, chosen through
the system file picker. It never writes to a hardcoded path and never asks for storage
permissions.

- **`.jrnlx`** — every entry, encrypted with AES-256-GCM under a passphrase you choose at
  export time. This is your backup. Keep one somewhere you will still have in five years.
- **Plain text** — a separate, clearly-labelled action behind a one-time warning that says
  exactly what it means: the exported file is readable by anything on the phone.
- **Import** reverses `.jrnlx`, tells you how many days collide with days you already have,
  and asks what to do about them. Nothing is ever merged silently.

---

## Building it

```bash
./gradlew build test lint
```

You need JDK 17 and an Android SDK with platform 35. A fresh clone builds without any
signing configuration — the release variant simply comes out unsigned. CI injects a keystore
from repository secrets when they are present.

```bash
./gradlew assembleRelease
```

The unsigned APK lands in `app/build/outputs/apk/release/`. It is about **1.9 MB**.

To install a signed release, download the APK from the GitHub release for the tag you want,
check its SHA-256 against the checksum published beside it, and `adb install` it or open it
on the phone.

---

## Deliberate non-goals

These are choices, not gaps. Knowing why they were made matters more than the choices
themselves, because it tells a future maintainer what not to "fix".

**No search index on disk.** Search decrypts and scans in memory while unlocked. An index is
a plaintext keyword list — it would hand an attacker a summary of every entry you ever wrote.
A linear scan is fine well past several thousand entries.

**No protection against a rooted or physically compromised device.** If someone has root, or
has your phone while the archive is unlocked, the game is already over. Pretending otherwise
would be theatre.

**No cloud sync, ever.** Your backup strategy is the `.jrnlx` file. Sync would mean a server,
a server means an operator, and an operator means somebody other than you can be compelled.

**No dependency injection framework, no navigation library.** One module, one graph, five
screens. A DI framework and a navigation DSL would add annotation processors and compiler
plugins that all have to keep working for a decade, to save perhaps eighty lines.

**No native libraries.** No SQLCipher, no Argon2. Everything uses the JCA that ships with
Android, which is why the APK is under 2 MB, why the 16 KB page-size migration is irrelevant
here, and why this will still build in five years.

**No background work.** No `WorkManager`, no alarms. Sealing happens when the app runs. A
background job would cost battery and could fail invisibly inside Doze.

---

## Decisions made where the spec left room

- **PBKDF2-HMAC-SHA256 is implemented in-app** rather than via `SecretKeyFactory`. Android
  only gained `PBKDF2WithHmacSHA256` at API 26 and this app supports API 24. The
  implementation is checked against the published test vectors in `Pbkdf2Test`.
- **The OAEP transformation is named explicitly** as `RSA/ECB/OAEPPadding` plus an
  `OAEPParameterSpec` naming SHA-256 for both the label digest and MGF1, rather than the
  `OAEPWithSHA-256AndMGF1Padding` shorthand. Several JCA providers read the shorthand as
  SHA-256 with MGF1-SHA-1, which would make the format provider-dependent.
- **RSA key size is chosen at runtime.** A 2048-bit generation is timed first; 3072 is used
  when the extrapolated cost fits inside an 8 second budget, and the already-generated 2048
  key is kept otherwise. No work is wasted either way.
- **The GCM tag is stored as the tail of the ciphertext**, not as a fourth column, because
  that is how the JCE returns it and splitting it up only creates a way to reassemble it
  wrongly.
- **The calendar grid is not tappable.** Seven columns cannot give every cell a 48 dp touch
  target on a narrow phone. Rather than ship sub-minimum targets, the calendar is a picture
  of the year and the list beside it is how you open a day.
- **No Macrobenchmark module.** The spec asks for one module. The baseline profile in
  `app/src/main/baseline-prof.txt` is hand-authored over the cold-start path instead; a
  benchmark module would have meant a second Gradle module and an emulator run in CI to
  generate a file that is only advisory.
- **Passphrase entry cannot avoid `String`.** Android's IME hands text to an app as immutable
  `String`s, so the last characters typed live in the heap until GC no matter what the app
  does. PIN entry avoids this entirely (`CharArray` throughout); everything below the UI
  layer is `String`-free. See `docs/SECURITY.md`.
- **`AppCompatActivity` rather than `ComponentActivity`**, for exactly one reason: per-app
  language selection needs AppCompat's delegate below API 33.

---

## Design

The visual language is street art, not a terminal. Spray-paint stencils, torn wheat-paste
poster blocks, halftone dots, tape, saturated magenta and cyan over warm-dark asphalt. The
mark is an original stencil "J" built from four cut plates with a deliberate registration
error; it borrows nothing from any existing artwork.

Type is Chakra Petch Bold for display and Space Mono for everything you read or write, both
SIL Open Font License — licence texts are committed under `licenses/`.

Every foreground/background pair in the palette was computed against WCAG rather than
eyeballed; the measured ratios are in the comments in `ui/theme/Color.kt`. Two extra "ink"
variants exist precisely because raw magenta and raw cyan fail on paper.

Motion is budgeted: nothing exceeds 420 ms, and the only thing that reaches it is the seal —
the one moment the rest of the app stays quiet for, and the only place the acid-green accent
appears. Reduce Motion, and a system animator scale of zero, collapse every animation to an
instant change.

---

## Documentation

- [`docs/SECURITY.md`](docs/SECURITY.md) — the key hierarchy and the threat model.
- [`docs/QA.md`](docs/QA.md) — the manual checklist to run once on a device after install.

## Licence

MIT. See [`LICENSE`](LICENSE).
