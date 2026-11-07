# Peek 1.2.0

A big quiet update: better discovery, more control over media, and stronger privacy.

## Search & discovery
- Search filters: category, verified accounts, follower range and country
- Resolve a post, reel or TV link to its author
- Saved searches and aliases ("CR7" → `@cristiano`)
- Offline search across cached bios, usernames and captions
- Related creators on every public profile
- Themed packs: Museums, Formula 1, Food, Architecture, Photography
- Hidden-gems pack, pack import from text/JSON and shareable `peek://pack` links
- Local category suggestions and recently viewed profiles

## Media
- Pinch-to-zoom photos
- Video scrubbing, speed control and a caption overlay
- Picture-in-picture reels
- Audio pages for cached reels
- Swipe-down dismissal with spring-back
- Shared-element avatar transitions from Home into profiles
- Read-only comments sheet with the public comment count

## Library & privacy
- Local liked feed, unread navigation badge and a weekly recap
- Download queue with pause, resume, retry and carousel downloads
- Passphrase-encrypted backups (AES-256-GCM + PBKDF2) with legacy JSON restore
- HTML and JSON archive export
- Biometric / device-lock app lock
- One-handed Home search action

## Platform
- Home-screen widget, Quick Settings search tile and app shortcuts
- Instagram share target
- Adaptive navigation rail for tablets and foldables
- RTL-ready layouts, content descriptions and scalable type
- F-Droid metadata and reproducible-build notes

## Notes
- Package: `com.avi.peek.debug` (debug-signed, same as 1.1.0)
- Minimum Android 8.0 (API 26)
- No login, no posting, no tracking
- Instagram does not expose comment text or video subtitle tracks to guests; Peek shows the public count and falls back to the post caption rather than inventing content

Full source and build instructions: https://github.com/Avithegod2010/peek
