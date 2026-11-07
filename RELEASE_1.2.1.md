# Peek 1.2.1

Save what you want and take a whole profile offline in one action.

## New
- **Save to gallery** from a post or from the Offline library
  - Photos go to `Pictures/Peek/<username>`
  - Videos go to `Movies/Peek/<username>`
  - Android 10+ uses scoped MediaStore with no storage permission; Android 8–9 asks once for the legacy write permission
  - Existing gallery copies are skipped on repeat saves
- **Download profile** button on every public profile
  - Saves all currently visible public posts for offline use
  - Optional “Also copy to gallery” for every photo and video
  - Live progress, per-profile completion card and failure count
  - Respects the existing Wi-Fi-only setting
- **Public/private detection fix**
  - The parser now picks the profile node that actually matches the requested handle
  - A profile with public posts is never labelled private just because of a stale flag
  - The profile page only shows the private state when there are truly no public posts; refreshing re-checks it

## Why a profile may not be “complete”
Instagram only exposes the latest public grid to logged-out guests. Peek can save everything it can currently see (usually the latest 12 posts), but not an account’s full historical archive. The confirmation sheet says this before downloading.

## Risks to know
- Mass downloading can trigger temporary Instagram rate limits or blocks. Peek keeps concurrency at two and does not retry aggressively.
- A large profile can use significant storage and mobile data.
- Posts and videos remain the property of their creators. Keep downloads private and do not redistribute them.
- Private accounts cannot be downloaded; Peek never logs in.

## Notes
- Package: `com.avi.peek.debug` (debug-signed, same as 1.1.0 and 1.2.0)
- Version: 1.2.1 (versionCode 4)
- Minimum Android 8.0 (API 26)
- No login, no posting, no tracking

Full source: https://github.com/Avithegod2010/peek
