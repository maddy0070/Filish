# FILISH

An offline-first Android file manager, built from the problem rather than
from the pattern.

```
gradle :app:assembleDebug          # build
gradle :app:testDebugUnitTest      # 102 tests, no device needed
```

Requires an Android SDK (`local.properties` → `sdk.dir`). Clash Display is
fetched at build time; an offline build still succeeds with a system-font
fallback.

---

## What it does differently

**It tells you how big a folder is.** Select four folders and two videos and
FILISH leads with the total size, not "6 selected". Files count immediately;
folders are walked in the background and the figure climbs toward the truth
with a marker that resolves when it settles. The alternative — what people
actually do today — is to create a temporary folder, move everything in, read
its properties, and move it all back.

**It is honest about deleting.** Android has no universal recycle bin that any
app may write into. FILISH uses MediaStore's trash where the platform allows,
extends it by indexing files that have no MediaStore row so they become
recoverable rather than destroyed, verifies the result, and reads the real
expiry date back instead of promising "30 days". Where none of that is
possible it says "Permanent" and explains why, before you confirm.

**Storage analysis leads somewhere.** Not "Videos: 24 GB" — you knew that —
but "nine videos over 1 GB, untouched in a year", opening a screen containing
exactly those files, wired to the same selection and deletion machinery as the
browser.

**It cannot talk to the network.** No `INTERNET` permission is declared, so
the process cannot open a socket even if a dependency tried to. Verifiable in
the built APK and in Android's own app info screen.

**It opens your files itself.** Images, video, audio, PDF and text, all
in-app. A file manager that hands every photograph to another application is
a launcher with a directory listing.

---

## Design

No Material components, no bottom navigation, no breadcrumbs, no donut chart,
no progress bar, no ripple, no cards. Each of those is a decision with an
argument behind it, recorded in [docs/design-system.md](docs/design-system.md).

The pieces most worth looking at: the **Conduit**, where material drains from
a source and accumulates in a destination with segment speed tied to real
throughput, so a stalled transfer visibly stops; and the **path rail**, which
pins both the root and the current folder and scrolls only what is in between.

---

## Documentation

| | |
|---|---|
| [product-principles.md](docs/product-principles.md) | The reasoning, and how to overturn it |
| [design-system.md](docs/design-system.md) | Colour, type, space, motion, and the arguments |
| [architecture.md](docs/architecture.md) | Structure, the ideas that matter, testing |
| [platform-constraints.md](docs/platform-constraints.md) | What Android permits — and what FILISH does not claim |
| [roadmap.md](docs/roadmap.md) | Honest status, including what is missing |

---

## Status

Phase 1 is complete and builds to a working APK. Known gaps are listed in
[platform-constraints.md](docs/platform-constraints.md) §7 rather than hidden:
operations do not survive process death, audio does not play in the
background, and archives are not implemented.

The app icon is explicitly provisional.

---

## Licence and fonts

Clash Display © Indian Type Foundry, used under the ITF Free Font License,
which permits embedding in an application but forbids redistributing the
binary through a public repository — hence the build-time fetch rather than a
committed file.
