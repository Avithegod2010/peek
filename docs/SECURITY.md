# Security model

Peek is a local-only viewer. It has no Peek account, server, analytics or crash
reporting. The only network destinations are Instagram, Instagram's CDN and
DuckDuckGo's HTML search endpoint.

## Protected at rest

- **Room metadata** — captions, profile text, media URLs, location, audio,
  comment text, search history and local file paths are encrypted with
  AES-256-GCM keys generated in Android Keystore.
- **Saved searches** — the DataStore value is encrypted with the same field key.
- **Session cookies** — encrypted with a separate Android Keystore key.
- **Private downloads** — sealed with an AES-256-GCM vault key. The key requires
  biometric or device-credential authentication, is unusable while the device
  is locked (Android 9+), and is invalidated when biometrics are re-enrolled.
- **Private vault backups** — passphrase-encrypted with PBKDF2-HMAC-SHA256 and
  AES-256-GCM. Private backup v2 includes metadata and sealed files.
- **Android backup** — disabled (`android:allowBackup="false"`).

Encryption failures are fail-closed for metadata writes. Plaintext rows from
older builds are read normally and re-encrypted on their next write. The
security center can rotate the field key; ciphertexts carry their key version so
a partial rotation stays readable.

## Protected in the UI

- Private feed, vault, security center, session audit, account, private post and
  private profile screens set `FLAG_SECURE` to block screenshots and recent-apps
  thumbnails.
- Private content is filtered through one visibility policy and re-locks on a
  configurable timer.
- Locking private content re-seals open vault files.
- Copied links, captions and usernames are marked sensitive and cleared after
  one minute.
- Optional shake-to-hide only hides content; it never deletes data.
- Network requests are restricted by host allowlist at both the application and
  network interceptor layers, including redirects. Cleartext HTTP is disabled.

## Known limits

- Android cannot stop a rooted attacker, a camera pointed at the screen, or an
  OS-level screen recorder that ignores `FLAG_SECURE`. Peek is best-effort.
- The vault uses a 30-minute Keystore authentication window rather than a
  per-operation `BiometricPrompt` `CryptoObject`. The app gate and auto-lock
  still hide content, but a future release should move to per-operation auth.
- Field-key rotation covers cached metadata, not files that were already copied
  out of the app (gallery exports, backups).
- Device verification is pending for biometric enrollment changes, auto-lock
  sealing, `FLAG_SECURE`, clipboard clearing, shake-to-hide and download queue
  restoration. See [TESTING.md](TESTING.md).
