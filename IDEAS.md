# Peek ideas — a living roadmap

Peek is intentionally a calm, local, read-only viewer for public Instagram profiles. The ideas below are ordered by how well they fit that promise. Anything that needs a login, posting, or a private API is out of scope.

## Already in 1.1

- Global **name search** (public web discovery → real public profile)
- Curated **Explore** directory with real avatars, bios and follower counts
- Real starter pack loaded live
- Material You category colours, animated selection and creator cards
- Search by name, `@username`, or profile link
- Local follow from search/explore without a second network request

## Search & discovery

1. **Search filters** — people, verified, category, follower range, country/language.
2. **Search by post or reel link** — resolve the shortcode to its author automatically.
3. **Saved searches and search aliases** — remember “CR7” → `@cristiano`.
4. **Transliteration and nickname matching** — “Cristiano” / “Cristiano Ronaldo” / “CR7”.
5. **“Related people” on a profile** — use public related-profile data when Instagram exposes it.
6. **Followed-by-similarity (local)** — suggest creators in categories you already follow.
7. **Trending searches** — aggregate only local searches on the device, never upload them.
8. **Surprise me** — open a random public creator from a chosen category.
9. **Hidden gems** — curated small/medium public accounts, not just mega creators.
10. **Shared starter packs** — export a pack of usernames as a small link/file; import someone else’s.
11. **Preview sheet before following** — quick peek at recent posts inside search.
12. **Recent views** — a private history of viewed profiles, not just searches.
13. **Creator notes** — “why I followed this account”, private and searchable.
14. **Country/culture packs** — local creators from a chosen region.

## Explore

15. **Daily collections** — five hand-curated accounts per day for Nature, Space, Design, etc.
16. **Themed packs** — Museums, Formula 1, Food, Film, Architecture, Photography.
17. **Trending reels** — a local carousel built from cached reels in your circle.
18. **New-post rail** — profiles with fresh posts since your last visit.
19. **Explore by mood** — Calm, Curious, Creative, Funny, Outdoors.
20. **Follow a whole pack** — one tap, sequential follows with progress and rate-limit backoff.
21. **Editorial cards** — short “why follow” copy for every recommendation.
22. **Diversify my circle** — suggest categories you follow zero accounts in.
23. **“A quiet corner”** — long-form photography/art accounts with no reels.
24. **Hidden public accounts by hashtag** — only if a public, login-free page exists.

## Home feed

25. **Calm mode** — chronological only, no counts, no “suggested”, no autoplay.
26. **Catch-up digest** — “while you were away” grouped by account.
27. **Per-account digest** — one card per account instead of every post.
28. **Unread markers** — local dots for profiles with posts newer than your last view.
29. **Feed filters** — photos only, reels only, videos, carousels, text.
30. **Mute a format** — hide reels from Home while keeping them in the Reels tab.
31. **Pinned posts** — pin a post to the top locally.
32. **Notes on posts** — private captions/reasons saved with a bookmark.
33. **Reading timer** — a gentle “you’ve been here 20 minutes” nudge.
34. **Open at the last position** — resume the feed where you left it.
35. **Star accounts** — a small “favourites” row above the feed.
36. **“Quiet hours”** — no feed badges/notifications after a chosen time.
37. **Local liked feed** — everything you liked, in one place.
38. **Saved collections** — group bookmarks into named collections.

## Offline & downloads

39. **Auto-archive latest N posts** per follow on Wi-Fi (configurable).
40. **Download queue** with pause/resume, retry, and progress notifications.
41. **Storage budget** — cap downloads and auto-evict the oldest unstarred media.
42. **Quality presets** — data saver / balanced / best.
43. **Bulk export** — save an offline profile archive with captions and metadata.
44. **Download carousel children** individually, not just the cover.
45. **Resume broken downloads** after process death.
46. **Offline search** across cached bios and captions.
47. **Export to HTML/JSON** for a personal, readable archive.
48. **Import a previous archive** after reinstall.

## Notifications

49. **Per-account notifications** — only the accounts that matter.
50. **Digest only** — one notification per hour/day.
51. **Quiet hours** — no sound or vibration in a chosen window.
52. **Format filters** — notify me for photos but not reels.
53. **New-post badge** on the profile avatar instead of a notification.
54. **Follow-back detection** — not possible with guest access; skip.
55. **Follower-count milestones** — store snapshots locally and celebrate only with the user’s opt-in.

## Interface & motion

56. **Shared-element profile transitions** — avatar flows from feed to profile.
57. **Swipe down to dismiss** the post viewer (with spring-back).
58. **Predictive back** animations on Android 14+.
59. **Tablet and foldable two-pane layout** — feed + detail, profile + post.
60. **Landscape reels** with proper insets.
61. **Home-screen widgets** — recent saved posts, new from your circle, quick search.
62. **Android share target** — share an Instagram link into Peek to preview/follow the author.
63. **App shortcuts** — “Search”, “Saved”, “Downloads”.
64. **Quick Settings tile** — “Open Peek search”.
65. **One-handed mode** — move the search field and primary actions to the bottom.
66. **Custom accent per profile** — extract a safe dominant colour from the avatar.
67. **Monochrome mode** — a quiet black/white theme beyond AMOLED.
68. **Font scaling and high-contrast modes** — test at 200% text size and TalkBack.
69. **Localisation** — start with Hindi, Spanish, Portuguese, Arabic and Japanese; ensure RTL.
70. **Haptic intensity** — light / standard / strong, plus “no haptics”.
71. **Reduce motion** — already present; add a “still frames only” mode for reels.
72. **Landscape post viewer** — image left, metadata right.

## Media

73. **Pinch-to-zoom** in the post viewer.
74. **Video scrubbing** with thumbnails, speed control and captions.
75. **Picture-in-picture** and background audio for reels.
76. **Save the cover** of a reel separately from the video.
77. **Carousel grid preview** on the feed with per-slide dots.
78. **Audio page** — every cached post using the same original audio.
79. **Dominant-colour backgrounds** behind transparent/portrait media.
80. **Live photo / GIF support** where public data exposes it.
81. **Screenshot mode** — hide UI chrome and save a clean image.

## Privacy & control

82. **Biometric app lock** — fingerprint/face before opening Peek.
83. **Quick exit** — a panic gesture that clears the recent screen.
84. **Encrypted backups** — password-protected JSON archive.
85. **Screenshot blocking** for sensitive profiles (best-effort, Android limitation).
86. **Data retention controls** — keep 1/3/12 months of viewed history.
87. **Export and delete everything** in one transparent flow.
88. **No analytics by default** — optional anonymous crash reports only.
89. **Network log viewer** — see exactly which hosts Peek contacted.

## Power users

90. **Multiple libraries** — e.g. “Focus”, “Inspiration”, “Local news”, each with its own follows.
91. **Per-profile sync schedule** — daily for art, weekly for news.
92. **Aliases and tags** — private naming and grouping.
93. **CSV/JSON import** — bring a follow list from another tool.
94. **Bulk unfollow / bulk move** between libraries.
95. **Saved-post search** across captions, usernames and your notes.
96. **Custom starter packs** — build and share a JSON pack.
97. **Watchlist** — profiles whose new posts deserve a stronger badge.
98. **Local “digest” screen** — weekly recap of what you saved and who posted.

## Technical quality

99. **Pagination** — support more than the first public page of posts when Instagram exposes a public cursor.
100. **Public highlights viewer** — read public highlight trays and their media where available.
101. **Read-only comments** — public comments from embed/SSR pages, clearly labelled read-only.
102. **Parser versioning** — fixture tests for SSR payload changes and a friendly “Instagram changed its page” state.
103. **Rate-limit governor** — shared token bucket, exponential backoff, per-host limits, queue priority (user tap > background sync).
104. **Conditional requests / ETag** where the public page supports it.
105. **Batched directory loads** with cache-first and shimmer states — already in progress.
106. **Baseline profiles and Macrobenchmark** for cold start, feed scroll and search latency.
107. **Screenshot tests** for every screen at 360dp, 411dp and tablet widths.
108. **F-Droid / reproducible build** and a signed release pipeline.
109. **CI** — compile, unit tests, parser fixtures, lint, APK artifact.
110. **Localisation QA** and RTL screenshot tests.

## Deliberately not planned

- Logging into Instagram or storing passwords
- Posting, liking, commenting or following on Instagram
- Private-account content
- Infinite algorithmic feeds, autoplay-by-default reels, ads or tracking
- Uploading follows, searches or view history to a server

## Suggested next three releases

### 1.2 — Search & Explore polish
Search filters, related creators, saved searches, share target, creator notes, download queue.

### 1.3 — A calmer library
Collections, offline auto-archive, per-account notifications, tablet layout, home-screen widget.

### 1.4 — Accessibility & trust
Biometric lock, encrypted backups, localisation, screenshot tests, parser fixtures, rate-limit governor.

If an idea needs an authenticated Instagram session, it does not belong in Peek. The constraint is the product.
