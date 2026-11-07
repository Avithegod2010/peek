# Peek 1.2.2

The full public grid, not just the first page.

## New
- **All public posts** now load in the profile grid
  - Peek uses Instagram's own logged-out “Show more posts” timeline query
  - New pages load automatically as you approach the bottom, with a “Load more posts” button as a fallback
  - No login, cookies are only the anonymous guest session
- **Download profile now downloads the whole grid**
  - Peek walks every public page it can reach first, then queues all found posts
  - The progress card shows “Loading public pages · N posts found”, then normal download progress
  - Optional “Also copy to gallery” still applies to every downloaded post
- **Post count and pagination state are cached**
  - A database migration preserves existing follows, bookmarks and downloads
  - Refreshing no longer drops pages you already loaded
- **Public/private fix carried forward**
  - The parser matches the requested handle and never labels a profile private when public media was returned

## What “all” means
Peek follows Instagram's public page pagination until Instagram stops returning pages. Some accounts expose their full grid; others may be cut short by Instagram rate limits, a login wall, or an account-level limit. The UI shows how many posts are loaded and keeps a Load more button when another page exists.

## Good to know
- Large accounts can take a long time and several GB to download.
- Instagram may rate-limit guests. Peek makes one page request at a time and keeps downloads at two concurrent files.
- Private accounts still cannot be read or downloaded.
- Posts and videos remain the property of their creators; keep downloads private.

## Build
- Version: 1.2.2 (versionCode 5)
- Package: `com.avi.peek.debug`
- Minimum Android 8.0 (API 26)
- No login, no posting, no tracking

Full source: https://github.com/Avithegod2010/peek
