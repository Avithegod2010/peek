# Performance notes

## Query path

- Room schema v6 adds indices for recent views, liked posts, downloaded posts,
  saved posts, private-feed ordering, audio titles, media types and search
  history. The `LargeProfileStressInstrumentedTest` writes 10k media rows and
  checks that the profile query uses an index.
- Private feed, bookmarks and downloads still filter through one visibility
  policy, so hiding private content never falls back to a full decrypt of
  unrelated tables.

## Decryption cost

Android Keystore operations are IPC-heavy. `FieldCipher` now:

- caches key handles per key version;
- keeps an LRU cache of decrypted field values keyed by ciphertext;
- clears decrypted values when private content locks.

The metadata cache is intentionally bounded at 2,048 values. A future
optimisation is to store sensitive metadata as one encrypted JSON blob per row,
which would reduce a post from many Keystore operations to one.

## Images and video

- The home feed prefetches the first 12 covers through the same Coil disk-cache
  keys used by `PeekImage`.
- Coil uses a bounded memory cache (20% of the app heap) and the existing
  configurable disk-cache limit.
- ExoPlayer instances pause on `ON_STOP` and resume only when their page is
  still active. Players are released when the composable leaves composition.

## Still to measure on hardware

- cold start and first-feed paint (`androidx.benchmark` / Macrobenchmark)
- 10k-row migration and scroll timing on a low-memory device
- field-cipher hit rate during a long feed session
- video memory after swiping through many reels
