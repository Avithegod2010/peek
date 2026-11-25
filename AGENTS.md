# Peek — working brief

Private Instagram client for Android ("offline guest client"). Kotlin + Compose, Room, Media3, Coil.
**Repo root = this folder.** Branch `main`. Public repo `AviTheGod2010/peek`.

## Current state (verified 2026-11-16)

- Version `1.6.0` / versionCode 9 (published release, tag `v1.6.0`). Everything up to `2767ba4` is in it; `v1.5.0` was the first properly signed release.
- Working tree clean and pushed. This release is non-debuggable, R8-minified, v2-signed, with `SHA256SUMS.txt` attached.
- Instrumented suite green on the phone: **18/18, run twice back-to-back on the same install**, SM-S942B / API 37 (2026-11-16). Evidence in `verification/2026-11-16/harness-fix/`.
- CI: `.github/workflows/android.yml` (unit tests + debug build + lint), `release.yml` (tag-triggered, verifies signature, publishes), `security-metadata.yml` (dependency verification).

## Architecture / security model

- Field-level AES-GCM for captions, profile text, URLs, location, audio, local paths, comments and search history. Ciphertexts are versioned (`enc:vN:`); key rotation is exposed in the Security Center.
- Vault key lives in the Android Keystore bound to user authentication, with a legacy-file fallback. The auth window governs how long that key can be reused for background sealing — it does **not** gate whether the prompt appears, so a second unlock prompts again. Auto-lock seals the vault; optional shake-to-hide panic gesture.
- Networking goes through an OkHttp host allowlist (Instagram/CDN/DuckDuckGo), redirects included.
- `FLAG_SECURE` on private surfaces; sensitive clipboard text auto-clears after 60s.
- Private visibility is centralised in `VisibilityPolicy` and covered by tests.
- Encryption is **field-level, not SQLCipher** — that distinction matters when discussing guarantees.

## Build / test

```powershell
$env:JAVA_HOME = "E:\Dev\jdk\jdk-17.0.20+8"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat connectedDebugAndroidTest   # needs the phone
```

`gradle.properties` uses `-Xmx1536m` (metaspace 512m). That is the solo-build setting — do not leave it lower, and do not run two Android builds at once.

## Device

- Galaxy S26, model `SM-S942B`, Android 17. adb: `E:\Dev\android-sdk\platform-tools\adb.exe`.
- Wireless adb, serial `adb-RZGL3039AAA-K6JTF6._adb-tls-connect._tcp`.
- The phone is often on Samsung DeX — **ignore DeX**, drive only the device. Ask before device work; the phone is shared with the Aura Camera project.

## Preserved artifacts

Device-verification output (APKs, logs, screenshots, UI dumps from 2026-11-16) is **git-ignored** and backed up at:
`E:\Opencode CLI\backups\peek-verification-2026-11-16` (89 files, ~36 MB).

## Outstanding

- Manual device pass (2026-11-18, SM-S942B): **verified** — biometric vault unlock (real `BiometricPrompt`, `onAuthenticationSucceeded`), `FLAG_SECURE` (A/B: secure flag set and screenshot blank on Session audit, capturable on Settings), 60-second clipboard clear (paste at 49s worked, at 97s empty). Evidence: `verification/2026-11-18/manual/RESULTS.md` (git-ignored).
- **Not verified, and deliberately left unverified**: everything that needs a signed-in Instagram session or a non-empty vault — sealing/re-opening real vault files, auto-lock re-hiding, panic mode, shake-to-hide, enrollment invalidation, queue restore after a process kill. The Instagram login on the test device was declined (it would put a session cookie on the phone). Do not sign in on that device without asking.
- Per-operation `BiometricPrompt` + `CryptoObject` for the vault key (not done).
- Golden-image screenshot baselines, MockWebServer pagination suite.
- Localisation: ~193 hardcoded `Text("…")` calls remain outside the translated surfaces.
- Release procedure: `v1.6.0` was cut by bumping `versionCode`/`versionName`, pushing `main`, then pushing the tag — the workflow builds, signs and publishes. The four `PEEK_*` secrets are set on the repo from `C:\Users\Anish\.peek\peek-release.properties`.

## Session history and operating lessons

Built 2026-11-15/16 across several long OpenCode CLI sessions, driven by a Hermes agent that relayed prompts and independently verified results. Now worked natively in Hermes (one Telegram topic per project); OpenCode is no longer in the loop.

Lessons that cost real time — apply them:

- **Never run long agent jobs as children of the Hermes gateway.** A `hermes gateway restart` on 2026-11-16 01:55 killed every background child, including this project's device-verification run mid-pass. Launch detached, or use `C:\Users\Anish\bin\oc-notify.ps1`.
- **One Gradle build at a time on this 8 GB machine.** Concurrent daemons thrash and get OOM-killed.
- **Verify claims with commands.** `git log`, `git status`, `gh run list --repo AviTheGod2010/peek`. One earlier commit (`0993482`) failed Android checks and the failure was not reported — the missing settings-search icon import was only fixed by `100e1e7`.
- **The run that "died silently" had actually asked a question**: it was waiting on a biometric vault unlock on the phone. If a device run stalls, screenshot the phone before assuming a hang.
- **The phone is shared with the Aura Camera project.** One agent on it at a time, and ask before touching it.
- **`gradle.properties` heap is currently 900m** from that shared-machine run — raise to 1536m when building alone.

