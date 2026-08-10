# Journalator — manual QA checklist

Run this once on a real device after installing a build. CI covers the crypto, the data layer
and an automated accessibility sweep; this covers the things a machine cannot judge — whether
TalkBack's reading order makes sense, whether the seal animation lands, whether the app is
still usable at 200 % font scale.

Tick every box before calling a build good. Where a step has an expected result, the check is
that exact result, not "something reasonable happened".

---

## 0. Before you start

- [ ] Install onto a device with **no previous Journalator data** (`adb uninstall
      dev.retr0alfred.journalator` first if in doubt).
- [ ] Confirm the APK's permissions:
      `aapt dump permissions <apk>` lists only `USE_BIOMETRIC` and `USE_FINGERPRINT`
      (the second is contributed by AndroidX Biometric). **No `INTERNET`.**
- [ ] Note the APK size. It should be roughly 1.9 MB and must be under 10 MB.

---

## 1. Setup

- [ ] First launch opens on **Setup**, step 1 of 3.
- [ ] Continue is disabled until a passcode of at least 6 digits is entered **and** the
      acknowledgement is ticked.
- [ ] Typing `111111` shows the "repeating or sequential pattern" warning.
- [ ] Typing `123456` shows the same warning.
- [ ] A reasonable PIN shows a cracking-time estimate, not a coloured bar.
- [ ] Switching to Passphrase requires at least 8 characters.
- [ ] Step 2 rejects a mismatch with a message and does not advance.
- [ ] Step 3 shows the assembling mark and finishes within a few seconds.
- [ ] "Start writing" lands on the Write screen.

---

## 2. Writing

- [ ] The app opens directly on today's page with the **keyboard down**.
- [ ] One tap on the page raises the keyboard.
- [ ] The header shows today's date in the device's locale format, a DAY counter and a live
      word count.
- [ ] Type a sentence, wait a second: the status flips from "Saving…" to "Saved".
- [ ] Type, then immediately background the app (Home). Return: the text is intact.
- [ ] Force-stop the app (`adb shell am force-stop dev.retr0alfred.journalator`) mid-sentence,
      relaunch: the text up to the last keystroke or the last pause is intact.
- [ ] Pick a mood, background and return: the mood is still selected.
- [ ] Rotate the device: text, cursor position and mood all survive.
- [ ] Split-screen: layout adapts, nothing is clipped.

---

## 3. Sealing

- [ ] SEAL ENTRY is disabled while the page is empty.
- [ ] Tapping it shows a confirmation that explains what sealing means.
- [ ] Cancelling leaves the page editable.
- [ ] Confirming plays the stamp animation once (about 0.4 s) and leaves a SEALED state.
- [ ] Reopening the app the same day shows the SEALED stamp, **not** an error and not an
      empty editable page.
- [ ] Feel for a haptic tick on seal.

### Lazy sealing across a day boundary

- [ ] Write something without sealing it.
- [ ] Set the device clock forward one day (Settings → Date & time, disable automatic).
- [ ] Reopen Journalator. The write screen is empty and ready for the new day.
- [ ] Unlock the archive: yesterday's entry is there, with yesterday's date.
- [ ] Set the clock **back**. The app must not create a duplicate or lose the sealed entry.

---

## 4. Unlock

- [ ] ARCHIVE opens the unlock screen, never the archive itself.
- [ ] A wrong passcode gives: the words "Wrong passcode", a haptic thud, and the glitch.
- [ ] Attempts 1–4 are free; the countdown starts on the 5th.
- [ ] During a lockout the keypad is disabled and the remaining time counts down.
- [ ] Force-stop the app during a lockout and relaunch: **the lockout is still in force**.
- [ ] The correct passcode converges the mark and opens the archive.
- [ ] Switching to passphrase entry and back works.

---

## 5. Archive

- [ ] Entries are listed newest first, with date, first-line preview and word count.
- [ ] Tapping an entry opens it full screen, read-only. There is no edit control anywhere.
- [ ] Search finds a word from an entry; a nonsense query says so by name.
- [ ] The Calendar tab shows the year with written days marked.
- [ ] Year navigation works in both directions.
- [ ] Back from an open entry returns to the list.
- [ ] Back from the list **locks** the archive and returns to today — press ARCHIVE again and
      the passcode is required.
- [ ] Background the app from the archive and return: the archive is locked (with auto-lock
      on Immediate).

---

## 6. Settings

- [ ] Change passcode: the wrong current passcode is rejected; the right one succeeds and
      every existing entry still opens with the **new** passcode.
- [ ] Auto-lock 30 s: background for 10 s and return — still unlocked. Background for 40 s —
      locked.
- [ ] Reduce Motion on: the seal is instant, screen changes are instant, the header no longer
      types itself in.
- [ ] Text size Large: everything scales and nothing overlaps.
- [ ] Heat-map toggle explains, in words, that it stores word counts unencrypted.
- [ ] Wipe-on-failure cannot be turned on without typing ERASE.

### Biometrics (skip on a device with none enrolled)

- [ ] The toggle is disabled and explains why when no biometrics are enrolled.
- [ ] Enabling asks for the passcode, then for a fingerprint.
- [ ] The unlock screen then offers "Use biometrics", and it works.
- [ ] The **passcode still works** afterwards. This is not optional.
- [ ] Enrol a new fingerprint in system settings: biometric unlock stops working and the
      passcode still opens the archive.
- [ ] Changing the passcode turns biometrics off.

---

## 7. Backup and restore

- [ ] Export encrypted: pick a passphrase, choose a location, confirm the file appears.
- [ ] Open the `.jrnlx` in a text editor: it is binary, and no entry text is visible.
- [ ] Import it with the **wrong** passphrase: refused with a message about the passphrase.
- [ ] Import with the right passphrase: it reports how many entries and how many collisions,
      and asks before replacing anything.
- [ ] "Keep what is on this device" leaves existing days untouched.
- [ ] "Take the backup's version" replaces them.
- [ ] Import a random non-backup file: refused cleanly, nothing changes.
- [ ] Plain-text export shows the warning first, and the resulting file is readable text.

---

## 8. Privacy behaviours

- [ ] Try to take a screenshot: the system refuses (**FLAG_SECURE**).
- [ ] Open the recents switcher: Journalator's thumbnail is blank.
- [ ] Turn on aeroplane mode and use the whole app: nothing degrades, because nothing was
      ever using the network.
- [ ] With a file manager, browse internal storage: Journalator's data is not visible.

---

## 9. Accessibility

- [ ] **TalkBack**: swipe through every screen. Every control is announced with a meaningful
      label and a role. Reading order is top-to-bottom, left-to-right.
- [ ] Keypad keys announce as "Digit 5, button", not "5".
- [ ] The seal button announces what sealing will do, not just "SEAL ENTRY".
- [ ] Calendar days announce "date: written" or "date: nothing written".
- [ ] The lockout state is announced as text, not conveyed by colour alone.
- [ ] **Font scale 200 %** (Settings → Display → Font size, largest): every screen is usable,
      nothing is clipped, nothing overlaps. Scroll where needed.
- [ ] **Display size Largest**: same.
- [ ] **Keyboard/D-pad**: connect a keyboard. Tab through every screen — each focused control
      shows a visible cyan outline, and the whole app is operable without touch.
- [ ] **Force RTL** (Developer options → Force RTL layout direction): layouts mirror, text
      alignment is correct, nothing is hard-coded to the left.
- [ ] Write an entry in Arabic or Hebrew, seal it, reopen it: the text is intact and reads
      right-to-left.

---

## 10. Themes and performance

- [ ] System light theme: the app inverts to a paper-dominant variant and remains legible.
      Nothing is invisible.
- [ ] System dark theme: the intended design.
- [ ] Cold start on the oldest device you have: under 800 ms to the write screen.
- [ ] Type a long paragraph quickly: no dropped frames, no stutter on the write screen.
- [ ] Scroll an archive of many entries: smooth.

---

## 11. Sign-off

- [ ] `./gradlew build test lint` is green locally.
- [ ] CI is green, including instrumented tests on API 24 and API 34.
- [ ] The release APK installs on a clean device and completes setup.
- [ ] The SHA-256 of the downloaded APK matches the checksum published with the release.
