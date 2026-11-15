# Building Peek for F-Droid

Peek is a single-module Android app with no closed-source dependencies and no
Google Play Services. F-Droid can build it from source with the standard Gradle
recipe.

## Current metadata

`fdroid/com.avi.peek.yml` targets:

- versionName **1.5.0**
- versionCode **8**
- tag **v1.5.0**
- Gradle release build

The upstream signed APK for v1.5.0 uses this certificate SHA-256:

```
fe94570c5a53a701cba408edc617c04687304624d9fc0a7a747ceda130d8a6f0
```

F-Droid signs its own builds with the F-Droid key unless the
`AllowedAPKSigningKeys` metadata option is deliberately configured. Users
cannot update between the GitHub-signed APK and an F-Droid-signed APK without
uninstalling one of them.

## Reproducible build

Requirements:

- JDK 17 (`JAVA_HOME` must point at any JDK 17 installation)
- Android SDK 36
- Gradle wrapper 8.14.3 (checked in)
- Network access for Gradle dependency resolution

F-Droid builds without signing environment variables, so the output is
unsigned:

```bash
./gradlew clean :app:assembleRelease --no-daemon
```

The APK is written to:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

To reproduce the upstream signed build locally, provide the release key
outside the repository and use the verification script:

```powershell
.\scripts\build-release.ps1 -Version 1.5.0
.\scripts\verify-release.ps1 -Apk .\release\peek-1.5.0-release.apk
```

## Checking a build

```bash
BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release-unsigned.apk
"$BUILD_TOOLS/aapt" dump badging app/build/outputs/apk/release/app-release-unsigned.apk | grep -E 'package:|sdkVersion|targetSdkVersion'
```

An unsigned F-Droid APK should fail `apksigner verify` until F-Droid signs it.
The important checks are the Gradle build succeeding, the package name
`com.avi.peek`, versionCode 8 and the absence of `application-debuggable`.

## What is intentionally absent

- No Google Play Services, Firebase, analytics or crash SDKs
- No login, posting, direct messages or comment posting
- No proprietary fonts or icon packs
- No runtime downloads of executable code
- The app only reads public, server-rendered Instagram pages and caches them
  locally; guest requests do not use an account
- No Peek server

The only network calls are public Instagram pages, Instagram's CDN and the
optional DuckDuckGo HTML name-search fallback. Both are user-triggered.
