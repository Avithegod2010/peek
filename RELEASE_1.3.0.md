# Peek 1.3.0

The biggest batch so far: the full public grid is now a workable local library.

## Profiles
- **See every public post** with automatic pagination, a Load more fallback and a live loaded count
- **Search this profile** by caption, place, audio or date
- **Filters** for photos, videos, reels, carousels, date range and newest/oldest/most-liked
- **New since your last visit** count, one-tap Show filter and Mark seen
- **Long-press selection** with Offline, Gallery, Collect and Archive actions
- **Profile storage** screen with cached/downloaded sizes, clear cache, delete downloads and trim to newest 60/120
- **Two-pane layout** on tablets and wide screens: profile grid on the left, post detail on the right
- **Read-only highlights** with the public cover/title and any media the guest page exposes

## Library
- **Collections** — private folders with a cover and per-collection grid
- **Private circles** — local groups, close-friends labels and a combined cached timeline
- **New since last visit** marks and archive timestamps stay on device
- **Per-profile archive export** in HTML or JSON, with an incremental “only new since last export” option

## Downloads & media
- **Download only new posts** after a quick first-page refresh
- **Download scheduler** settings: Wi-Fi only, charge-only and a maximum newest-post count per profile
- **Gallery albums** per profile under `Pictures/Peek/<username>` and `Movies/Peek/<username>`
- Optional **caption/date JSON sidecars** written with gallery saves
- Existing gallery files are skipped on repeat saves
- Selection archives export only the chosen posts

## Reliability & privacy
- **Rate-limit dashboard** listing recent profile/page/post/download requests and rate-limit hits
- Pause all downloads or turn off background sync when Instagram pushes back
- All new lists, visit marks, circles and archive timestamps are local DataStore data
- No login, no analytics, no server

## Known limits
- Instagram can still cut off profile pagination or rate-limit guests at any time.
- A large full-profile download can take minutes and several GB.
- Highlight media is shown only when the logged-out page actually exposes it; otherwise Peek shows the public cover, title and a clear hand-off.
- Private accounts remain unreadable by design.
- Media belongs to its creators; keep downloads private and do not redistribute.

## Build
- Version: 1.3.0 (versionCode 6)
- Package: `com.avi.peek.debug`
- Minimum Android 8.0 (API 26)
- F-Droid metadata and reproducible-build notes are in the repository

Full source: https://github.com/Avithegod2010/peek
