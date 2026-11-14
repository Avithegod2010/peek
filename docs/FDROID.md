# Building Peek for F-Droid

Peek is a single-module Android app with no closed-source dependencies and no
Google Play Services. F-Droid can build it from source with the standard Gradle
recipe.

## Reproducible build

Requirements:

- JDK 17 (`JAVA_HOME` must point at any JDK 17 installation)
- Android SDK 36
- Gradle wrapper 8.14.3 (checked in)
- Network access only for Gradle dependency resolution; the app itself reads
  public Instagram pages at runtime and never requires an account

Deterministic release build:

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Java\jdk-17"
.\gradlew.bat clean :app:assembleRelease --no-daemon --offline
```

The APK is written to `app/build/outputs/apk/release/app-release-unsigned.apk`.
F-Droid signs it with its own key.

## F-Droid metadata

The `fdroid/com.avi.peek.yml` file in this repository is a copy of the
metadata entry that belongs in `fdroiddata`. It points at the release tag and
uses the default `assembleRelease` build.

To reproduce the F-Droid build locally:

```bash
# from a clean checkout at the tag in fdroid/com.avi.peek.yml
gradle clean assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release-unsigned.apk
```

## What is intentionally absent

- No Google Play Services, Firebase, analytics or crash SDKs
- No login, posting, direct messages or comment posting
- No proprietary fonts or icon packs
- No runtime downloads of executable code
- The app only reads public, server-rendered Instagram pages and caches them
  locally; guest requests do not use an account

The only network calls are the public Instagram HTML pages and the optional
DuckDuckGo HTML name-search fallback. Both are user-triggered.
