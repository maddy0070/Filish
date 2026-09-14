# Product principles

The reasoning FILISH is built on. Not a style guide — a record of the
arguments, so that a future change either carries the argument forward or
knowingly overturns it.

---

## The problem worth solving

A file manager's real job is not listing files. It is answering questions
about storage that the operating system refuses to answer:

- What is actually taking up my space?
- How big is this, really?
- What can I safely get rid of?
- Where did that file go?

Conventional file managers decline most of these because answering them is
slow. FILISH's central bet is that **slow is not the same as impossible**, and
that the correct response to "this takes three seconds" is to show the answer
as it arrives rather than to withhold it.

---

## The rules

### 1. Never withhold an answer because it is expensive

The flagship case: select four folders and two videos in any conventional file
manager and it reports "6 selected" — the one fact you already had. To learn
how much space they take, people genuinely create a temporary folder, move
everything in, read its properties, and move it all back.

FILISH measures it. Files contribute immediately; folders are walked in the
background and the figure climbs toward the truth with a marker beneath it
that resolves when the walk settles. A usable approximation in the first
hundred milliseconds, an exact answer a moment later, and never any ambiguity
about which one is on screen.

This principle generalises: **any result that streams should stream.**
Directory listings, search, storage analysis, duplicate detection and folder
sizes all publish partial results and say whether they are finished.

### 2. Never claim more than the platform allows

Every capability was checked against the actual APIs. Where Android does not
permit something, FILISH says so in the interface, in the words that apply,
rather than approximating and hoping. The deletion routing is the fullest
expression of this — see [platform-constraints.md](platform-constraints.md).

The corollary: **no feature that is mostly a costume.** No natural-language
search backed by three keyword matches. No office-document viewer that renders
contracts subtly wrong. No duplicate detection that reports files as identical
because they share a size.

### 3. A setting that does nothing is worse than a missing feature

A completion-notification preference was removed during development because it
had no implementation behind it. Permissions FILISH does not use were stripped
from the manifest. Three navigation destinations that rendered blank screens
were either built or deleted, and the shell's navigation switch now has no
fallback branch, so a destination without a screen fails to compile.

### 4. Analysis must lead to action

A storage screen that reports "Videos: 24 GB" has told the user something they
could have guessed. Photos and video are the point of the phone; their size is
not a finding.

"Nine videos over 1 GB, untouched in a year" is a finding, because it is a
decision. Every finding in FILISH opens a screen containing exactly the files
it described, wired to the same selection and deletion machinery as the
browser. Analysis and doing are the same surface.

### 5. Errors are part of the product

"Something went wrong" is banned from this codebase. Every failure states what
happened, why when that is knowable, and what the user can do next. A
playback failure distinguishes a missing decoder from a corrupt file because
those imply different next steps. A name that cannot be used says which
character is the problem.

### 6. The interface should not need a manual

The current sort order is written in words in the control strip, not hidden
behind an icon. Search shows every term it extracted as a removable chip, so a
misreading is visible and correctable rather than opaque. The delete
confirmation names the mechanism. An active filter always shows its count,
because a filtered list that looks unfiltered is how people conclude their
files have vanished.

### 7. Accessibility is a constraint, not a pass

Custom colour is where accessibility fails quietly: a recessive grey looks
elegant on a bright monitor and is unreadable in sunlight, and nothing in the
build catches it. Every ink-on-ground pair FILISH renders is asserted against
WCAG 2.1 AA in a test. Writing that test found three real failures in a
palette that looked fine.

Selection is a tonal inversion plus a leading marker rather than a colour, so
it survives greyscale and colour-blindness. Reduced motion removes positional
motion everywhere without removing information.

### 8. Question the convention, then decide on the merits

The brief asked for the interface to be derived from the problem rather than
copied. Where that produced something better, FILISH departs:

- **No bottom navigation.** It spends a tenth of the screen permanently to
  shorten the path to destinations used a fraction of the time, while browsing
  is ~90% of use.
- **No breadcrumb.** Breadcrumbs truncate the end of the path — the folder you
  are actually in. The rail pins the current folder and the root, and scrolls
  only the middle.
- **No donut chart.** Angle is among the hardest encodings to compare and a
  category donut answers the wrong question. One stratum bar answers "how
  full" and "of what" at once.
- **No progress bar for transfers.** The Conduit conserves material between a
  draining source and an accumulating destination, with segment speed tied to
  measured throughput — so a stalled transfer visibly stops, which a progress
  bar cannot express.
- **No ripple.** Surfaces take weight under a finger instead.
- **No cards, almost no dividers.** Grouping is by proximity, which is free,
  silent, and survives every theme.

And where convention was actually right, FILISH keeps it. Tap to open, long
press to select, pinch to zoom, swipe between images. Novelty in navigation
fundamentals costs the user and buys nothing.

### 9. Do not build a second copy of something the OS already has

FILISH keeps **no file index**. The obvious move is a local database of the
user's storage; it was rejected because Android already maintains one
(MediaStore) that is kept current by the OS and survives our process dying, a
private index is permanently at risk of being stale, and duplicating the
user's data costs them storage in an app whose purpose is saving them storage.

The one thing FILISH caches is measured folder sizes, which MediaStore
genuinely cannot provide.

---

## How decisions get overturned

Every non-obvious decision is documented at the point of code, in the words of
the problem it solves. A change that contradicts one should say why the
original reasoning no longer holds. Several decisions in this codebase were
already reversed during development — the ink ramp, the path rail's guarantee,
the Conduit's segments, the storage findings' dispatch — each because looking
at the result showed the first answer was wrong.
