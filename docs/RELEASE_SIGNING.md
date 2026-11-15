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

## GitHub releases

Upload only the signed release APK and its `SHA256SUMS.txt`. Never attach the keystore, the properties file, or an unsigned/debug APK to a public release.
