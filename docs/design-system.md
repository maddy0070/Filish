# The FILISH design language

Every rule here exists to solve a stated problem. Where a rule departs from
convention, the argument is recorded — including the ones that were overturned
after looking at the result.

Source of truth: `app/src/main/java/com/filish/design/`.

> **V3 — Substrate & Lens.** The material language has been rebuilt. The
> principles below still hold, and the ink ramp, semantic colours and category
> tints are unchanged — but surfaces, shapes, interaction states and depth are
> now governed by [`filish-liquid-glass.md`](filish-liquid-glass.md), which
> supersedes this document wherever the two disagree. Two revisions are
> explicit and stated there: rounding, and dark-mode translucency.
>
> Tokens: `app/src/main/java/com/filish/design/glass/`.

---

## Ground metaphor: strata, not cards

A filesystem is not a set of cards floating in space. It is material laid down
in layers, at rest, with depth — the user is inspecting a cross-section. This
yields a coherent language that is neither "cards" nor "Material":

- Content sits **on** a ground, not inside floating containers.
- Separation is by **tone and rhythm**, not by shadow and rounded rectangles.
- Shadow is reserved for things that genuinely leave the ground.

The motif recurs: three strata form the app icon, the storage glyph, and the
opening sequence, where they stretch into the first rows of the list.

---

## Colour

Two rules.

**State is saturated; category is muted.** Saturated colour is reserved for
things that are *happening* — an operation running, an error, focus. File
categories are identified by their glyph first and a low-chroma tint second,
so a folder of mixed content never becomes a fruit salad.

**Selection is not a colour.** Conventional file managers tint selected rows
blue. FILISH inverts the row's ground and adds a leading-edge marker. Selection
therefore survives greyscale, colour-blindness and glare, and cannot be
confused with the "something is happening" colours.

The signal hue is **verdigris**, not system blue — chosen for hue distance
from red (danger) and amber (warning) so the three can never be mistaken for
one another, and because it is cool against a warm paper ground.

### Contrast is measured, not eyeballed

`ContrastTest` asserts every ink-on-ground pair FILISH actually renders against
WCAG 2.1 AA (4.5:1 for text, 3:1 for meaningful non-text).

Writing it found three real failures. The first fix darkened the secondary ink
until it collapsed into the primary and the hierarchy vanished — so the whole
ramp was rebalanced instead. All three steps now clear 4.5:1 *and* stay
visibly distinct:

| | light | dark |
|---|---|---|
| `ink0` | 17.2 : 1 | 16.9 : 1 |
| `ink1` | 10.5 : 1 | 7.8 : 1 |
| `ink2` | 5.4 : 1 | 5.6 : 1 |

If a colour is changed for aesthetic reasons and that test fails, the change
was wrong.

**Dark is designed, not inverted.** Near-black with a slight blue cast rather
than `#000` — pure black removes the tonal headroom separation depends on and
smears on OLED during scroll. Selection inverts *upward*: the selected row
becomes the brightest thing in the list.

---

## Space

**There is no 4dp grid, deliberately.** A uniform scale answers "what number
goes here?" but never "what is the relationship between these two things?" —
and spacing is the only tool that communicates relationship without drawing
anything. When every gap is 16dp, no gap means anything, and the interface
then needs dividers, boxes and cards to re-introduce the grouping uniform
spacing destroyed. Most card-heavy interfaces are card-heavy for this reason.

Space is named by relationship:

| Token | Size | Meaning |
|---|---|---|
| `bond` | 3dp | one thing — a number and its unit |
| `near` | 7dp | directly dependent — a name and its metadata |
| `group` | 14dp | peers — row to row, chip to chip |
| `apart` | 26dp | separate concerns — section to section |
| `zone` | 44dp | major regions |
| `gutter` | 20dp | screen edge to content |

Steps are ~1.9× apart — the smallest ratio at which two gaps read as
*different kinds of gap* rather than as a mistake — and offset off the common
8dp rhythm so the page has its own cadence.

**Consequence:** FILISH needs almost no dividers and almost no cards.

---

## Typography

Two faces, divided by ownership.

**Clash Display** carries FILISH's voice: wordmark, titles, section headings,
and every large number. Storage figures are the most consequential text in a
file manager — they are what the user is there to understand.

**The system sans** carries the user's data: file names, paths, metadata.
This is the correct call, not a compromise. File names are arbitrary strings
in unknown scripts — Cyrillic, CJK, Arabic, emoji — and Clash Display is a
Latin face. Setting names in it would mean silent, inconsistent fallback on
exactly the content the user cares most about, with one name rendering in a
visibly different face from the one above it.

Chrome is ours; content is the user's.

The face is fetched at build time and its absence is handled: FILISH degrades
to a tuned system stack and says so in Settings rather than pretending.

---

## Depth

Three planes, and a component must earn its way off the ground.

| Plane | Who | Treatment |
|---|---|---|
| **Ground** | lists, headers, content | never casts a shadow |
| **Raised** | sheets, overlays, the ledger | wide soft shadow — light from far away |
| **Carried** | something being dragged | tighter, darker — close to the finger |

On dark, shadow is nearly invisible, so the same three planes are expressed
tonally (`ground1` → `ground2` plus a hairline). The hierarchy is identical;
only the medium changes — which is why depth is a named plane in the codebase
rather than a dp value passed around.

---

## Motion

Every animation answers *why does this move?* If the honest answer is "it
looked nice", it was deleted. What survives does one of four jobs:

**Continuity** (the thing you're looking at is the thing you tapped) ·
**Causation** (this changed because you did that) · **Progress** ·
**Consequence**.

Durations differ by job, not uniformly. Selection is a state change on
something already on screen and is near-instant (120ms); navigation is a
change of place and needs long enough to follow (220ms). Nothing bounces — a
bounce implies elasticity, and files are not elastic.

**Reduced motion** replaces all positional and scaling motion with a 90ms
cross-fade and turns continuous animations into discrete state. No information
is lost; only the movement goes.

---

## Components worth arguing about

### The Conduit — replaces the progress bar

A progress bar answers one question and answers it indistinguishably from a
bar that has frozen. A spinner answers nothing.

The Conduit is a band with two ends: material **drains** from a source mass on
the left and **accumulates** in a destination mass on the right, conserved
between them — the two masses *are* the reading. Segments travel between them
at a speed tied to **measured throughput**.

That last decision is what makes it honest: **a stalled transfer visibly
stops.** A copy hung on a locked file stops dead and the user knows within a
second without reading a number.

Seven segments, one Canvas, no layers or blur, and the phase advances only
while bytes are moving — an idle conduit costs nothing. Segment count follows
available space, because a fixed count smears into one blob as the gap closes
near the end of a transfer, which is exactly when the user is watching.

### The stratum bar — replaces the donut

Angle is among the hardest visual encodings to compare; length along a common
axis is among the easiest — and a storage breakdown is entirely comparisons.
Worse, a category donut answers the wrong question: the user arrived asking
"how full am I", which needs a separate number in the hole.

One band spanning the volume answers both at once. Space FILISH cannot read
into is drawn **hatched**, never folded silently into "Other" — a breakdown
whose parts don't sum to the total is one users are right to distrust.

### The path rail — replaces breadcrumbs and the title bar

A title bar says where you are but not how you got there. A breadcrumb fixes
that and truncates the *end* — the folder you are actually in.

The rail pins **both ends**: the root at the leading edge and the current
folder at the trailing edge, with only intermediate ancestors scrolling. The
current folder is pinned *structurally* (reversed layout), not by a scroll
effect — the first version used a `LaunchedEffect` to scroll to the last item,
which made the component's one guarantee depend on a side effect. An overflow
gradient marks clipping, and appears only when something is genuinely clipped.

### The selection ledger

Leads with **total size**, not a count. See product principle 1.

### Press feedback — replaces the ripple

A ripple is a metaphor for a drop landing on water: decorative, and it says
nothing about what was pressed. FILISH surfaces **take weight** — darkening
slightly and contracting by a fraction of a percent, immediate on press-down
(60ms) and unhurried on release (180ms). The scale is almost imperceptible on
purpose; anything more is nauseating during fast scrolling.

### Toggles

Not a sliding thumb. Its two states differ mainly by *position*, the hardest
difference for a glance to read and one that disappears in greyscale. FILISH
uses a filled/unfilled field with a resolving check: on is dense and marked,
off is hollow.

### Glyphs

Drawn, not imported, from one vocabulary: 24-unit grid, single stroke weight
(24 : 1.9), round caps and joins. **Every file kind shares one silhouette with
a different interior**, so a listing reads as files that differ rather than a
scatter of unrelated shapes. Nothing is skeuomorphic — no folded page corner,
no floppy disk.

The folder is the single exception to the shared silhouette, because a folder
is a different kind of thing: a place you enter, not an object you act on.

---

## Empty and error states

No illustrations. An illustration occupies the space where the explanation
should be and then says only "empty", which the absence of rows established.

"This folder is empty" and "nothing matches your filter" look identical on
screen and need completely different responses, so they never render the same
words. Errors state what happened, why when knowable, and what to do next.
