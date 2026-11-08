# Peek 1.4.0

Optional Instagram sign-in, clear private-account states and a much larger palette set.

## Optional Instagram account

Settings → **Instagram account** opens Instagram's own web login inside Peek.

- **Optional** — Peek still works for public profiles without any account.
- **No password storage** — Peek never reads or stores the password fields.
- **Keystore encryption** — only the session cookies Instagram returns are saved, encrypted with an Android Keystore AES-GCM key in a separate local DataStore.
- **Sign out** deletes the saved cookies and clears the WebView session.
- The session is restored into Peek's HTTP client on later launches.
- Private profiles your own session follows can now be requested through the same reader.
- If Instagram challenges or blocks web sign-in, Peek reports the failure; it does not claim the account was connected.

## Private accounts are labelled honestly

- A locked private profile now shows **“This account is private”**, not an empty grid or a generic error.
- Signed out, the state offers **Sign in to Instagram**.
- Signed in but not following, it explains that Instagram only reveals private posts to followers of that account.
- Private profiles that are visible through the session carry a **“Private account · visible to your session”** badge.
- Private profiles in the following list and search results show a lock icon.
- Public/private state is still re-checked on refresh and is never inferred from an empty grid.

## Thirteen palettes

Five palettes became thirteen:

Lavender · Ocean · Forest · Sunset · Rose · Mint · Coffee · Sakura · Citrus · Indigo · Slate · Sand · Aurora

The picker is now a wrapping FlowRow with colour swatches and names, and every palette has tuned light and dark containers.

## Build

- Version: 1.4.0 (versionCode 7)
- Package: `com.avi.peek.debug`
- Minimum Android 8.0 (API 26)
- F-Droid metadata and reproducible-build notes are in the repository

## Verified on device

- Settings → Instagram account screen with signed-out state and warnings
- Instagram's web login page loads inside the in-app WebView
- A real private account (`@private_test`) shows the lock badge, disabled download button and the “This account is private” empty state
- Thirteen palette pills, Ocean and Aurora in dark mode, Aurora in light mode
- Final APK builds and installs

## Known limits

- Actual credential sign-in and private-content reading were **not** tested because no test Instagram account was available. Instagram may challenge or block WebView sign-in, and it may change or rate-limit authenticated pages.
- Signing in does not unlock private accounts you do not follow. Instagram controls that access.
- Automated access can conflict with Instagram's terms. Use your own account, keep activity light, and treat private posts as private.
- Media belongs to its creators. Keep downloads private and do not redistribute them.
