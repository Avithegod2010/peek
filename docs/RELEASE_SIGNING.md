# Signing a Peek release

The repository never contains a signing key or its passwords.

## One-time key creation

Run the release script once. It creates a PKCS#12 keystore and a secrets file **outside the repository**:

- `%USERPROFILE%\.peek\peek-release.jks`
- `%USERPROFILE%\.peek\peek-release.properties`

```powershell
.\scripts\build-release.ps1 -Version 1.5.0
```

Back up both files somewhere safe. If you lose the key, existing installs cannot be updated in place by a new key.

## Environment variables

The Gradle release build reads:

- `PEEK_KEYSTORE`
- `PEEK_KEYSTORE_PASSWORD`
- `PEEK_KEY_ALIAS` (default `peek-release`)
- `PEEK_KEY_PASSWORD`

The script sets these from the local secrets file.

## Output

- `release/peek-<version>-release.apk`
- `release/SHA256SUMS.txt`

The release build is non-debuggable and R8-minified. Do not publish `app-debug.apk` as a production artifact.

Verify an APK before uploading it:

```powershell
.\scripts\verify-release.ps1 -Apk .\release\peek-1.5.0-release.apk
```

The script checks the signature, reads the package metadata and refuses a debuggable APK.

## Upgrading from a debug build

Debug APKs up to v1.4.0 used the standard Android debug key, so Android will not install a release-signed APK over them. Uninstall the debug build first; local data is removed with it. Release-signed builds from v1.5.0 onward update each other in place.

## GitHub Actions secrets

The tag workflow `.github/workflows/release.yml` needs four repository secrets:

| Secret | Value |
| --- | --- |
| `PEEK_KEYSTORE_BASE64` | Base64 of `%USERPROFILE%\.peek\peek-release.jks` |
| `PEEK_KEYSTORE_PASSWORD` | keystore password |
| `PEEK_KEY_ALIAS` | `peek-release` |
| `PEEK_KEY_PASSWORD` | key password |

Create the base64 value locally without writing it into the repository:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$env:USERPROFILE\.peek\peek-release.jks")
) | Set-Clipboard
```

Add the secrets under **Settings → Secrets and variables → Actions → New repository secret**. The workflow decodes the keystore into the runner workspace, builds `assembleRelease`, verifies the signature and non-debuggable badge, writes `SHA256SUMS.txt`, uploads the artifact and creates the GitHub release. The temporary keystore is deleted with an `always()` step.

Push a matching tag to start it:

```powershell
git tag -a v1.6.0 -m "Peek 1.6.0"
git push origin v1.6.0
```

The tag must equal `versionName` in `app/build.gradle.kts`. If `docs/releases/v1.6.0.md` exists, the release is published with those notes; otherwise the workflow generates notes from commits and creates a **draft** for review.

## GitHub releases

Upload only the signed release APK and its `SHA256SUMS.txt`. Never attach the keystore, the properties file, or an unsigned/debug APK to a public release.
