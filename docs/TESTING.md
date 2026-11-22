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
- screenshot baselines for the shared composables (see below)

## Screenshot baselines (JVM golden images)

`ScreenshotBaselineTest` renders the shared composables on the JVM — Roborazzi on
top of the existing Robolectric runner — and compares each render pixel-for-pixel
with the PNGs committed in `app/src/test/screenshots`.

**The committed PNGs are recorded on Linux, and CI is their only authority.**
Roborazzi rasterises through real Skia under `@GraphicsMode(NATIVE)`, and Windows
and Linux turn the same composable into slightly different pixels — font metrics
and rasterisation differ. A golden recorded on a Windows laptop therefore fails on
the Linux runner, and vice versa. So:

- the tests skip themselves unless `CI` is set in the environment. A local Windows
  run reports them as **skipped**, never failed and never passed — a local Windows
  run cannot validate these baselines;
- CI (`ubuntu-latest`) is where they are verified;
- to record or re-record them, run the `screenshot-record.yml` workflow
  (`workflow_dispatch` on GitHub, Actions → *Record screenshot baselines* → Run
  workflow) and download the `screenshot-baselines` artifact. Commit those PNGs.

```bash
# What CI runs - verifies the Linux-recorded goldens
./gradlew :app:testDebugUnitTest
```

Verification is the default: `app/build.gradle.kts` sets `roborazzi.test.verify`
for every unit-test run and only flips to record when
`-Proborazzi.test.record=true` is passed, so a plain test run can never rewrite a
baseline. A mismatch fails the test and writes the diff to
`app/build/outputs/roborazzi/*_compare.png`; commit the updated PNGs alongside
the code change so reviewers can see the visual delta.

Do not record the PNGs on Windows. The `-Proborazzi.test.record=true` flag is
honoured only where the assumption passes, so a local Windows record run leaves
the committed files untouched.

Covered today (light and/or dark): `SimpleTopBar` with a back button and subtitle,
`EmptyState` with an action button, and the `SectionHeader` + `AddPeopleHint`
settings block, which is what carries the theme's surface containers and type scale.

The window is pinned to 360x640dp at xhdpi on API 34 with dynamic colour off, and
nothing depends on a device, the wall clock or an animation frame.

Honest limitation: these render on the JVM at that one fixed config, using
Robolectric's rendering stack and font set. They catch layout, size, colour and
typography regressions — not how the UI behaves on real hardware (touch targets as
felt by a thumb, insets, haptics, OEM fonts, gesture handling). Keep the
instrumented tests and a device pass for anything that needs a real screen.

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
- `LargeProfileStressInstrumentedTest` — 10k-post profile query and index plan
- `ComponentUiTest` — Compose empty state, top-bar back action, image capture, RTL rendering and `FLAG_SECURE`

## What still needs hardware verification

- Biometric-bound vault key creation and the 30-minute auth window
- Auto-lock sealing and shake-to-hide
- Screenshot blocking on private screens
- Clipboard auto-clear after one minute
- Download queue restoration after process death
- Story pager, comment threads and video playback
- Calm mode, settings search, saved-post notes, vault search/sort and the request timeline
- Feed prefetch hit rate and player pause/resume across background/foreground
