# Peek 1.5.0

Session-aware privacy controls, request budgeting and the next layer of private-account tooling.

## Session health and audit
- Detects expired Instagram sessions on private profile, page, post, highlight and comment requests.
- Shows **Session expired** in the account screen and **Reconnect** on locked private profiles.
- **Session audit** screen: cookie names only, saved/verified/success timestamps, expiry state and recent request outcomes.
- **Manual session fallback**: paste your own `sessionid` or cookie header when the in-app web login is blocked. Values stay encrypted on device.

## Private-safe downloads and vault
- Private downloads are **off by default**.
- When enabled, private media must be unlocked with biometrics and is stored in an **AES-GCM vault** encrypted with an Android Keystore key.
- Private media is never copied to the public gallery.
- The vault is sealed when the app goes to the background and can be opened or sealed manually.
- **Signing out deletes private downloads and private cached posts**.
- Panic mode hides private profiles, posts and downloads until biometric unlock.

## Private feed and panic mode
- **Private feed** screen with a separate local timeline for private follows.
- **Panic mode** switch in Settings and a **Quick Settings tile** that hides private content immediately.
- Public feeds, saved/liked grids, downloads, collections, circles, audio and search results filter private media while hidden.

## Highlights and comments
- **Story-style highlight viewer** with segmented progress bars, previous/next navigation, local seen marks and automatic advance.
- Authenticated highlight API is tried first; the old public-page parser remains as a fallback.
- **Read-only comments** with a signed-in session: author, avatar, text, date and likes. No reply box and no posting.

## Request budget
- Hourly request cap with local usage count and reset time.
- Exponential backoff after rate limits: 5 minutes, 15 minutes, 1 hour, 6 hours.
- Quiet hours for background sync.
- Resume/reset controls on the rate-limit dashboard.
- Background sync respects quiet hours and Wi-Fi-only.

## Themes and privacy controls
- Custom accent hue, text scale and high-contrast mode on top of the 13 palettes.
- Backup and archive exports exclude private content by default, with explicit opt-in switches.
- Private profile diagnostics explain the exact lock reason instead of showing a generic empty grid.

## Build
- Version: 1.5.0 (versionCode 8)
- Package: `com.avi.peek.debug`
- Minimum Android 8.0 (API 26)

## Known limits
- Private content still depends on Instagram accepting the web session. Instagram may challenge or block login, expire cookies or rate-limit authenticated pages.
- The private vault encrypts local downloads; it does not encrypt the Room metadata database.
- Highlight auto-advance uses a fixed 5-second estimate for images; video timing is approximate.
- Panic mode filters cached private media from Peek's own lists, but exported files or screenshots made before hiding are outside its control.
- Media belongs to its creators. Keep private downloads private and do not redistribute them.
