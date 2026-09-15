# FILISH V3.1 — Mass & Light

The art direction. **A spatial filesystem, where magnitude is visible and
light means something is happening.**

Status: **art direction complete and audited. Screens not built, on purpose.**

V3 ([filish-liquid-glass.md](filish-liquid-glass.md)) answered *what is this
made of* — Substrate, Lens, Ink, and the rule that information is never made of
glass. All of it stands. V3.1 answers a bigger question: *what is this place,
and what carries hierarchy inside it?*

> **A note on the references.** This round was briefed with visual references
> that did not reach the session — no images were attached to the conversation.
> The work below was developed from the written specification of the aesthetic
> territory instead (negative space, dark environments, luminous surfaces,
> isolated objects, typography as architecture, restrained chrome, depth
> through scale and position, cinematic composition). Where a rendered
> exploration was rejected, the reason is recorded, so re-running any of it
> against the actual references is cheap.

---

## 1. Four hypotheses

Genuinely different design hypotheses, not colour variations. Each was
**rendered**, using the same six objects — including a 12 KB text file whose
only job was to break the ones that assume everything is big, and a Cyrillic
filename to break the ones that assume Latin.

### H · Deep Field — *light carries hierarchy*

A void; objects revealed by proximity to a light source; distant things fade.

**Beautiful, and rejected.** It was the most atmospheric of the four and it
failed on the thing that matters most: a file two thirds down the list rendered
at 26% alpha and could not be read. Dimming the user's data according to its
position in a list is a file manager hiding the thing it exists to show, and
the ranking it implies is not real. It also wasted about 40% of a phone screen
to display six files.

**Kept:** the environment as a *place* rather than a background. Light as a
meaningful channel — but transient, never ambient.

### I · Core Sample — *mass carries hierarchy*

The directory as one continuous body drilled through; each object's vertical
extent is its size.

**The best idea in the round, in the wrong channel.** Size-as-extent was real
information — you could see which files mattered without reading a figure. It
was also unusable: six files filled a screen, and a 500-file directory would
have been a scrolling nightmare. Vertical space is the one resource a mobile
file manager cannot spend.

**Kept:** magnitude as a visual channel; the directory as a continuous body cut
by hairlines rather than a stack of cards; selection as mass inversion.

### J · Optical Bench — *resolution carries hierarchy*

A flat, evenly lit stage. A fixed instrument resolves whatever passes under it;
everything else is a compressed index.

**Rejected as a mechanism, mined for its best idea.** A fixed window that
content scrolls under fights every scrolling habit on the platform and
guarantees the thing you want is never where your finger is.

**Kept:** detail and actions appear *where the object already is*. This is what
lets FILISH have no per-file bottom sheet at all.

### K · Atmosphere — *content makes the environment*

The ground is a colour field derived from the folder's own media.

**Rejected — it is V3's Direction A returning in a new costume.** The effective
background of a file name became a function of the user's photographs, which is
the exact failure already proven fatal once. It also looked muddy, fought the
file-kind tints, and was the *least* distinctive of the four despite being the
most colourful. Sampling thumbnails for a dominant colour on every directory
entry is real work on a cold folder, for a result that hurts.

**Kept:** a folder should be identifiable before it is read — but through the
mark and the header, never the ground. A solid mark on a known ground is
auditable; a ground is not.

---

## 2. M · Mass & Light — the synthesis

### The one structural move

**Magnitude moved from row height into mark width.**

The leading mark on every object is as wide as the object is big, inside a
fixed 18dp channel. Rows stay uniform, so scanning and scrolling are untouched,
and the channel is carved out of a gutter that was empty. The left edge of a
directory becomes a **profile** of that directory — a size histogram read in one
glance, for free.

### Is it doing any work? — the control render

The same screen was rendered with the mark removed
(`v31-M-control-no-mass.png`). Without it, FILISH is a clean, legible,
completely anonymous dark file list — indistinguishable from a dozen other
apps. **The mark is the difference.** That is the blind-recognition test
answered with evidence rather than opinion.

### What the mark carries

| Channel | Carries |
|---|---|
| width | magnitude, relative to this listing |
| hue | kind (or, for a folder, its dominant content) |
| intensity | state — full when known, reduced while a size is still resolving |
| bloom | attention — the system is doing something to this object *now* |

Width, hue and intensity are separable, so they do not interfere; width and
intensity survive greyscale. A deliberate concentration: one element carries a
lot so the rest of the row can carry almost nothing, which is what makes the
list quiet.

### The two rules that keep it honest

> **MATERIAL SHOWS MAGNITUDE; INK STATES IT.**
> The mark is for the glance. The figure in the metadata line is for the
> answer, and it is always there. Anyone who cannot perceive the width
> difference loses a shortcut, never a fact.

> **LIGHT IS NEVER LOAD-BEARING — and it lives in the gutter.**
> Illumination is carried entirely by the mark, where no text sits and no text
> can ever sit. Nothing is ever dimmed to make something else stand out.

The second rule was *discovered*, not designed. The first version lit the
object's body too, at a strength chosen to survive the audit. It did not
survive — day metadata on a lit body measured 4.22:1, and every value strong
enough to actually see failed in both themes, because the resting body already
spends nearly all the headroom AA allows. Tuning it down would have produced a
highlight nobody could see. Deleting it produced a better rule: light cannot
reduce the contrast of a file name *by construction*. `WorldContrastTest`
asserts there is no second object ground for it to come back on.

### Relative, not absolute

Marks scale against the largest item in the current listing. An absolute
1 KB–64 GB scale went flat in the middle of every real directory — 418 MB and
51 MB a pixel apart. Relative scaling spends the whole channel on the spread
actually present, and matches the question people ask, which is never "how big
is this in the abstract" but "what is taking up the room *in here*".

**The cost, stated plainly:** the same file draws a different mark in different
folders. It is acceptable only because the figure is always in text, and it is
the single thing in this direction most worth putting in front of a real user.

---

## 3. Objects are not lenses

The most important structural change, and it follows from V3's own density
finding taken to its conclusion.

| | |
|---|---|
| **LENS** | a transient surface that is *not* content — action bar, sheet, confirmation, ledger. Rim, radius, shadow. It will go away. `Modifier.lens()` |
| **OBJECT** | the user's own files. No rim, no radius, no shadow. Cuts in a continuous body, distinguished by a mark and a hairline. `Modifier.worldObject()` |

V3 discovered that at list spacing a column of lenses stops reading as cards
and starts reading as one body. The conclusion V3 did not draw: at that spacing
the rim, the radius and the corner geometry are doing *nothing* — while costing
draw calls on the most frequent element in the application.

**Content is a material you cut. Chrome is an object you place.**

---

## 4. What was rejected, and why it matters

### The substrate cannot carry information

A storage horizon in the environment — a soft density change at the device's
fill level — was prototyped and rejected for a better reason than V3 found.

V3 rejected a hard waterline because rows sliced it into fragments. The
softened version has no line to fragment, and it *still* failed: in a working
directory the environment is almost entirely covered by content. There is
nowhere for an ambient signal to live.

**This kills a whole family of ideas rather than one** — ambient storage
colour, mood grounds, content-derived atmosphere, environmental "weather". They
all fail the same way. The substrate's only job is to establish that this is a
place; it gets the screen edges, empty states, the opening and the storage
screen, and it never gets a fact.

### Night is a place. Day is a page on a desk.

Light mode is not the dark theme inverted, and it is not the dark theme's
metaphor forced onto paper.

In night, FILISH is an environment you are inside. In day that reading
collapses — a near-white ground is a surface you look at, not a space you
occupy — and forcing atmosphere onto it produces exactly the grey haze that
makes light glassmorphism look like cling film.

So day is a different medium with identical physics: **the list is a sheet of
paper, and the environment is the desk under it.** The environment was dropped
several steps from V3's near-white specifically so the paper has somewhere to
be — objects had measured 1.05:1 against it, which is a list of things
indistinguishable from the surface beneath them.

Consequence, and it is V3's recessed-ground rule applying again rather than a
new exception: the environment is darker than the paper, so text drawn directly
on it takes `ink1`, never `ink2`.

---

## 5. The remove-30% pass

The full material was rendered beside a version with the environment gradient,
the object body gradient and the cut hairline removed. The result was **split**,
which is the honest outcome and more useful than a verdict:

**Removed — the body gradient.** A two-stop fade over a 58dp row at these
alphas is invisible. What it *did* do was band the list, fighting the one thing
the composition wanted: a continuous body.

**Kept, and proven load-bearing — the cut.** Without it, three consecutive RAW
files of near-identical size merged into one indistinguishable orange block.
Adjacent files sharing a kind and a size is not an edge case — it is burst
photography, screenshots and exports, which is most of a real camera folder.

The strip also exposed a **bug**: the cut stopped at the mark, leaving the marks
of adjacent same-kind files fused into a continuous bar that said "one object"
about three. The cut now crosses the channel.

**Kept with a caveat — the environment gradient.** Nearly free, and nearly
invisible under a full directory. It earns its place only in sparse states, and
this document would rather say so than oversell it.

---

## 6. Storage, without a chart

*What does 42 GB of photographs look like?* The system already owns the answer:
it looks like an object whose mark is that wide.

So the storage screen is the browse screen one level up. Categories are
objects, the mark is the magnitude, the grammar is identical. A donut teaches
nothing transferable and cannot be acted on; this is the same list the user
already knows how to read, and every row is a place to go.

The insight rows use the same scale, and their mark is **the bytes they can
give back** — so how much a cleanup is worth is legible without reading a
number. "1,284 duplicates · 3.2 GB reclaimable" is an object with a 3.2 GB
mark, lit while it is still scanning.

One visual language, not a browsing one and a charting one.

---

## 7. Operations in the world

V2's Conduit rule survives intact — source drains, destination accumulates,
segments travel, completion is arrival — and now has a native expression,
because both ends of a transfer are objects with a mark.

- **A move** narrows the source's mark and widens the destination's. The mass
  travels down the same gutter the marks live in.
- **A copy** leaves the source at full width. *The difference between move and
  copy is visible without a label* — which is the distinction users most often
  get wrong.
- **Departing to trash** shrinks the object toward a place it still exists in.
- **Destroyed** has no destination; the mark goes first.
- **A stalled transfer holds its position rather than pulsing.** Motion means
  work. A thing that has stopped working must stop moving, or the interface is
  lying.

---

## 8. Resolution in place

Tapping an object expands it **where it is**. Detail, the figure at display
size, and the actions, all inside the row.

FILISH therefore has **no per-file bottom sheet** — no context menu, no
overflow, no dialog for the common case. The object never loses its place in
the list, so closing it costs no re-orientation.

Navigation is the same move at screen scale: entering a folder is not screen A
replacing screen B, it is this directory receding and the child's contents
arriving in the space it leaves. Each level you came through stays as one
compressed line whose mark is still its size — so the route in is also a record
of where the space went.

---

## 9. Media

Images are the one thing a file manager has that other applications do not, so
they get to be the object rather than a 40dp square beside it.

A media field uses the **same grammar as the list, turned ninety degrees**:
extent is magnitude, neighbours are separated by a hairline cut. A 51 MB RAW
visibly occupies more of the strip than a 4 MB HEIC.

> **NO TEXT IS EVER DRAWN ON A THUMBNAIL.**

That single constraint is why V3's Direction A and V3.1's Direction K were both
rejected, and it is what makes media integration safe here. Captions live in
the object body underneath, on a known ground, at an audited contrast. Nothing
in the composition depends on what the picture happens to be.

---

## 10. Typography

Unchanged from V3, and the §15 question was already settled in round one:
FILISH is set in **two faces, for a product reason rather than an aesthetic
one**.

**Clash Display** carries FILISH's voice — wordmark, titles, and every large
number. Licensing was verified: ITF Free Font License permits embedding but
forbids repo redistribution, so it is fetched at build time and degrades to a
tuned system stack if absent.

**The system sans** carries the user's data — file names, paths, metadata. This
is the correct call, not a compromise: file names are arbitrary strings in
unknown scripts, and setting them in a Latin display face means silent,
inconsistent fallback on exactly the content the user cares most about.
**Chrome is ours; content is the user's.**

What V3.1 adds is *typography as architecture*: the contextual title is the
largest thing on screen and among the quietest, and a resolved object leads
with its figure at display size rather than with a label. Numbers are objects.

---

## 11. Colour

No new palette. V3's ink ramp, semantic colours and category tints are
unchanged, with **one** edit: the day signal was darkened one step
(`#2E7D6E` → `#2A7365`) because dropping the day environment put it at 4.43:1
on an object body. A darker verdigris raises every pairing it appears in.

Colour remains **energy entering the environment**, not decoration:

- **kind** — low-chroma category tints, on the mark only
- **signal (verdigris)** — focus, primary action, an insight in progress
- **danger / warn / ok** — semantic events
- **selection** — not a colour at all; the ground inverts

No acid, no lime, no electric blue. They were considered and not adopted: the
mark already spends the colour budget on kind, and a high-energy accent would
compete with the one channel doing real work.

---

## 12. Accessibility

Audited in `WorldContrastTest` and `MassTest`, not asserted.

- The **unlit** state is what the audit checks. Illumination is only ever
  added, so if the resting state passes, every state passes.
- Full ink ramp clears AA on an object body, at both ends of the environment
  gradient, in both themes.
- The environment is a recessed ground: `ink0`/`ink1` only.
- Every category mark clears 3:1 on the resting body **and** on the inverted
  ground of a selection — which is exactly when the user is deciding what to
  delete.
- Objects are distinguishable from the environment (≥1.12:1) and the cut from
  the body (≥1.10:1). Those two thresholds are perceptual floors taken from the
  renders, not WCAG figures; there is no WCAG number for "these are two
  different materials".
- **A bigger file can never draw a narrower mark.** Pinned. A visual channel
  carrying a quantity is a claim about the user's data.
- Nothing is dimmed, ever.

### Still owed before screens

- **Rows must be content-height with a 48dp floor.** The prototypes use a fixed
  58dp, which breaks under large font settings.
- The mark is decorative to a screen reader — the size is in the text, so
  nothing is lost, but it must be explicitly excluded from semantics rather
  than left to chance.
- Under reduced motion, a growing mark jumps to its settled width.

---

## 13. Performance

The whole material is flat rectangles and, when lit, one horizontal gradient.
No blur, no `saveLayer`, no offscreen buffers, no `RenderEffect`. Identical on
API 26 and API 35.

**The one real trap, and it is a correctness requirement rather than an
optimisation:** every mark scales against the largest item in the listing, and
folder sizes stream in. Each change to that reference rescales every mark on
screen — the left edge jitters while the user is reading it, and every row's
cached draw state invalidates on every partial result.

`Mass.quantiseLargest()` snaps the reference to whole doublings, capping
rescales at the doublings crossed rather than the number of updates.
`MassTest` pins it with a 400-partial streaming walk. **Call it once per
listing, never per row.**

Everything else: brushes built in `drawWithCache`; at most one continuously
animating element on screen, and only while work is in flight.

---

## 14. What changed from V3, and what must not

### Changed

- Content rows are **objects**, not lenses — no rim, no radius, no shadow.
- The band became the **mark**, with variable width carrying magnitude.
- The environment is a **world** (`World`), with day reframed as page-on-desk.
- Light is a defined, bounded channel (`Light`) that lives in the gutter.
- Day environment dropped several steps; day signal darkened one step.

### Must not change

- **Information is never made of glass.** (V3)
- **Light is never load-bearing.** (V3.1)
- **No text is ever drawn on a thumbnail.**
- **Material shows magnitude; ink states it.**
- **A bigger file never draws a narrower mark.**
- Blur remains banned outside one modal.
- The Conduit's grammar: source drains, destination accumulates, a stalled
  thing stops moving.
- Selection inverts the ground rather than tinting it.
- Nothing overshoots; nothing bounces.

---

## 15. Honest weaknesses

Written down because they are the things most likely to be wrong.

1. **Relative scaling is the biggest open risk.** A user who learns "wide =
   big" will be surprised when a 2 MB PDF has a wide mark in a folder of text
   files. Mitigated by the figure always being present; needs a real user, not
   more reasoning.
2. **Four channels on one element is a lot.** It degrades gracefully — you can
   ignore the mark entirely and the list still works — but a first-time user
   will not decode it unaided.
3. **The mid-range compresses.** 9.4 GB and 1.8 GB differ by about 2dp. Honest,
   and the text carries the precision, but it is a real limit of log scaling.
4. **The environment gradient is nearly invisible** under a full directory. It
   is kept because it is nearly free and because sparse states are where first
   impressions are formed, not because it is doing daily work.
5. **In-place expansion pushes rows down.** The tapped row must stay anchored or
   it will be disorienting after the fiftieth time.

---

## 16. Where the code is

```
app/src/main/java/com/filish/design/glass/
  Mass.kt          the magnitude channel, and quantiseLargest
  World.kt         the environment, and Light
  WorldObject.kt   Modifier.worldObject() — content
  Surface.kt       Modifier.lens() — chrome (V3, unchanged)
  Material.kt Skin.kt Facet.kt Liquid.kt Response.kt   (V3, unchanged)

app/src/test/java/com/filish/
  MassTest.kt            the channel cannot lie about size
  WorldContrastTest.kt   the world, audited
  lab/world/Worlds.kt    the four hypotheses
  lab/world/Synthesis.kt the synthesis, its control and its strip test
  lab/world/Prototypes.kt the ten experiments, from production tokens
```

Renders in `app/build/screenshots/`: `v31-H/I/J/K-*`, `v31-M-*`,
`v31-A/B/C/D-*`.

---

## 17. Build this first, next session

In order, because each de-risks the next.

1. **The browse list at list density, with the mark.** It is the highest-value
   and highest-risk surface, and everything else is a variation of it. Ship it
   behind the existing screens if need be, but render it with a real 5,000-file
   directory before anything else is touched.
2. **Content-height rows with a 48dp floor**, verified at the largest font
   setting. This is a hard blocker for everything above.
3. **Wire `Mass.quantiseLargest` into the streaming size path** and watch the
   left edge while a large folder measures. If it jitters, nothing else matters.
4. **Resolution in place**, which deletes the per-file sheet.
5. **The storage screen**, which is now the same component with different rows.

Not yet: media fields, navigation depth, the Conduit rework. They are designed
and they can wait for the list to prove itself.
