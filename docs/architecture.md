# Architecture

## Shape

```
com.filish
├── FilishApp.kt          Application + the object graph
├── MainActivity.kt       The single browser window (edge to edge)
├── app/                  Shell: navigation, transitions, wiring
├── core/
│   ├── model/            FileNode, FileKind, ordering, number formatting
│   ├── fs/               Listing, volumes, recursive sizing, MediaStore bridge
│   │   └── ops/          Copy, move, delete routing
│   ├── search/           Query grammar + streaming search
│   ├── intel/            Storage analysis, findings, duplicates, hashing
│   ├── media/            Thumbnails, metadata
│   └── settings/         DataStore-backed preferences
├── design/               Palette, type, space, motion, depth, glyphs
│   └── component/        Every visible control, built from Compose Foundation
└── feature/              One package per surface
```

**No Material dependency anywhere.** FILISH uses Compose Foundation — layout,
gestures, drawing — and builds every visible component itself. Pulling in
Material would have meant inheriting its shapes, ripple, elevation model and
type ramp, then fighting all four.

**No DI framework.** About a dozen singletons with a fixed acyclic graph and
no scoping or test-substitution needs. `FilishApp.Graph` is a hand-written
service locator; a container would add an annotation processor and seconds to
every build to solve a problem this app does not have.

**No database.** See principle 9 in [product-principles.md](product-principles.md).

---

## The ideas that matter

### Everything expensive is a Flow

`SizeResolver.measure()`, `SearchEngine.search()`, `StorageAnalyzer.analyze()`
and `DuplicateFinder.scan()` all emit partial results as they work and mark
themselves complete at the end. The UI renders whatever has arrived. Nothing
blocks, and a partial answer is never mistaken for a final one — every
progress type carries an explicit `settled`/`complete` flag, and the interface
renders the two states differently.

Emissions are throttled (~100–200ms) and skip duplicate values, so a walk over
half a million files does not recompose a list half a million times.

### One walk answers every question

`StorageAnalyzer` accumulates category weights, age and size distributions,
largest files (via a bounded min-heap, so the top N costs O(log N) per file
with no final sort), heaviest folders and duplicate candidates in a **single**
breadth-first pass. The naive design runs one scan per panel — five
traversals, five times the I/O, five progress bars finishing at different
moments.

### Duplicate detection is designed around not hashing

Group by exact byte length (free — the size came from the walk) → fingerprint
the first and last 64 KB → full SHA-256 only for what survives both. For a
2 GB video the middle step reads 0.006% of the file. Ten large files with
distinct sizes hash zero bytes, which is asserted in a test.

A size collision is *never* reported as a duplicate; only a full digest match
is.

### Listing uses nio, not File

`Files.newDirectoryStream` with one `readAttributes` per entry, versus
`File.listFiles()` plus `isDirectory`/`length`/`lastModified` — one `stat`
instead of four. In a 50,000-entry camera folder that is the difference
between a listing that appears and one that hangs. Falls back to `java.io` on
filesystems where nio misbehaves.

### Bounded everything

Thumbnail cache is budgeted in **bytes**, not entries (one bitmap may be 40 KB
and another 4 MB). Decoding is capped at three concurrent permits. Search
results cap at 2,000 and say so. Size-measurement parallelism is bounded and
stops branching below a shallow depth, so a 500,000-file tree never creates
500,000 of anything. The size cache is an LRU with a TTL, not a map.

### Operations outlive the screen

Copy and move run in an application-scoped coroutine, so navigating away does
not cancel a transfer. The engine holds the job so `cancelActive()` can reach
inside the copy loop — stopping a 4 GB transfer must not mean waiting for that
file to finish. (It does not yet survive process death; see
[platform-constraints.md](platform-constraints.md) §7.)

### Navigation is an explicit stack

`NavStack` plus a `NavDirection`, rather than a navigation library, because
FILISH's transitions depend on knowing whether the user went deeper or came
back — the folder transition is directional and its inverse must be exact, and
a general-purpose navigator hides that. It is about three dozen lines.

The shell's destination switch has **no else branch**, so adding a destination
without a screen fails to compile.

---

## Testing

102 JVM tests, no device required.

| Area | What it pins down |
|---|---|
| `NaturalOrderTest` | `file10` sorts after `file9` |
| `QueryParserTest` | No typed token is ever silently dropped |
| `SearchEngineTest` | Real files; criteria intersect rather than union |
| `DuplicateFinderTest` | Real files; middle-byte and last-byte differences caught; zero bytes hashed without size collisions |
| `ContrastTest` | Every ink-on-ground pair meets WCAG 2.1 AA |
| `SelectionTest` | A folder selection is never "settled" before its walk finishes; a stale total cannot survive a selection change |
| `FormatTest` | Decimal units, constant significant digits, no 1970 timestamps |
| `OrderingTest` | Every sort combination states itself in words |
| `render/*` | Screenshots of every screen, both themes, small screen, high contrast |

### Screenshots without an emulator

The build environment has no KVM, so there is no hardware emulator.
Robolectric in native graphics mode runs the real Compose tree through the
real Skia pipeline; `ScreenshotTest` draws it via `View.draw` into a bitmap
(`captureToImage` goes through PixelCopy, which Robolectric cannot drive) and
writes PNGs to `app/build/screenshots/`.

This found six real defects that compiling could not: three contrast
failures, a path rail that depended on a side effect for its one guarantee, a
folder glyph that read as a card, Conduit segments that smeared into a blob,
a layout regression, and twenty-two strings shouting the app's own name.

**Caveat:** flows on `Dispatchers.IO` are not pumped by the Robolectric
looper, so screens whose content arrives asynchronously render empty in a
screenshot. Those paths are covered by direct tests against real files
instead; a blank screenshot of such a screen is a harness artifact, not
evidence of a bug.

```bash
gradle :app:testDebugUnitTest --tests "com.filish.render.*"
```

---

## Build

Kotlin 2.0.21 · AGP 8.7.3 · Compose BOM 2024.12.01 · minSdk 26 · targetSdk 35

Clash Display is **fetched at build time**, not committed. The ITF Free Font
License permits embedding the font in an application but forbids
redistributing the binary through a public repository; a build-time fetch
satisfies both. The fetch is best-effort — an offline build succeeds and the
app falls back to a tuned system stack, which Settings reports honestly.
