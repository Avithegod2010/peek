# Testing Peek

## Runs anywhere (JVM)

```bash
./gradlew testDebugUnitTest
```

Covers:

- backup encryption round-trip, wrong passphrase, truncation and tampering
- network host allowlist and lookalike/cleartext rejection
- private-content visibility policy
- SSR profile, post, comments and malformed-payload parsing
- media JSON helpers

## Compiles without a device

```bash
./gradlew compileDebugAndroidTestKotlin
```

This is part of CI. It catches instrumentation-test compile errors even when no
device is attached.

## Requires a phone or emulator

```bash
./gradlew connectedDebugAndroidTest
```

These tests are written and compile, but have **not been run on hardware**:

- `SecurityInstrumentedTest` — session cookie encryption, vault seal/open, metadata cipher, private access
- `SecureDaoInstrumentedTest` — media/profile/comment/search ciphertext at rest, search dedupe, lock callback
- `MigrationInstrumentedTest` — Room 1→5 migrations, private-state propagation, comment table and indices
- `RepositoryVisibilityInstrumentedTest` — private feed and media filtering through the real repository
- `ComponentUiTest` — Compose empty state, top-bar back action, image capture, RTL rendering and `FLAG_SECURE`

## What still needs hardware verification

- Biometric-bound vault key creation and the 30-minute auth window
- Auto-lock sealing and shake-to-hide
- Screenshot blocking on private screens
- Clipboard auto-clear after one minute
- Download queue restoration after process death
- Story pager, comment threads and video playback
