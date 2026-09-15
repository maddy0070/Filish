# FILISH Visual Language — Mass & Light / The Spine

**Status: art direction complete. The browse list is built — see [V4](#v4--p--spine-in-production).**

The lineage, kept whole because each step's reasoning still constrains the
next:

| | Question answered |
|---|---|
| **V3** — Substrate & Lens ([doc](filish-liquid-glass.md)) | *what is this made of* |
| **V3.1** — Mass & Light | *what carries hierarchy* |
| **V3.2** — The Spine | *what does this place look like* |
| **V4** — production *(current)* | *does it survive real files* |

> ### The reference images did not reach this environment
>
> Both V3.1 and V3.2 were briefed with visual references. Neither time did any
> image arrive — `/mnt/attach` is empty and nothing is in the conversation.
> The work was developed from the written specification of the territory
> instead (negative space, dark environments, luminous surfaces, isolated
> objects, typography as architecture, restrained chrome, depth through scale
> and position, selective colour, cinematic composition).
>
> Every rejection below is recorded with its reason and its render, so
> re-testing any of it against the real references is cheap. The place
> references would most change the answer is §2 — the three variants.

---

## 1. The problem this round was given

V3.1 was correct and under-directed. Its own summary is the indictment: a black
ground, uniform filled rows, a coloured tab at the leading edge, good
typography, one gradient. Technically elegant, visually anonymous — a design
system rendered onto a file manager rather than a designed thing.

The specific trap named in the brief, and it was the right diagnosis:

> *"Light cannot touch text"* does not mean *"the interface should be flat."*
> *"Blur is expensive"* does not mean *"FILISH cannot have depth."*
> *"Cards are generic"* does not mean *"everything should be flat rows."*

Depth without expensive effects comes from **scale, position, density,
negative space, occlusion, mass, opacity, media and geometry.** V3.1 used
almost none of them.

---

## 2. Three art directions for Mass & Light

Same twenty-one objects each: two folders, a media run, a Cyrillic name, and a
3 KB config file whose only job is to break anything that assumes files are
big.

### P · THE SPINE — *the marks leave the rows and become a landscape* ✅

Three moves, each aimed at one V3.1 failure:

1. **The row body is deleted.** At rest a file is a mark and two lines of text
   on open ground.
2. **The marks detach and gain gaps.** Substrate shows between mark and text,
   and between one mark and the next.
3. **Media enters the spine.** An image's mark is a vertical sliver of the
   actual photograph.

**Won.** See §3.

### Q · THE PLATE — *depth by layering and margin* ❌

The listing as a single inset plate, with the contextual title in the margin
behind it.

**Rejected as a composition.** The rows are still filled rectangles, so the
generic quality is untouched — Q frames generic content. Worse, a plate with a
margin on all sides is *one enormous card*, which is the opposite of where §10
points. The marks also fused into continuous bars again.

**Taken:** occlusion. The title being cut off by the plate reads as real depth
for zero cost, and V3.1 never used occlusion once. It now lives in the root
composition (§6).

### R · THE ATLAS — *magnitude becomes area* ❌

Objects above a threshold get a full-width plate with a media band and
display-size typography; everything smaller compresses into dense two-column
runs.

**Visually the most dramatic of the three, and rejected for browsing:**

- **Text is drawn on media.** "1.98 GB" over a thumbnail — contrast becomes a
  function of the user's photographs. This is precisely the failure that killed
  V3 Direction A and V3.1 Direction K. On a bright photo the figure vanishes.
- **Five files consume a whole screen.** Unusable at 5,000.
- **Sorting breaks it.** The big/small split is a size sort imposed on the
  view; sort by name and the layout reshuffles into a different shape.
- Two-column runs make the eye zigzag.

**Taken:** R is a very good *storage* view, where the content genuinely is
"what is big here" and the list is short. That is now where it lives (§10).

---

## 3. S · The Spine — the converged direction

### THE MARK SHOWS THE MOST SPECIFIC THING AVAILABLE

| | The mark is |
|---|---|
| a photograph | a vertical sliver of that photograph |
| a video | a sliver of its poster frame |
| a folder | a composite of what is inside it |
| anything else | its kind tint |

**The no-media control proved this is load-bearing, not decorative.** With flat
category tints a run of seven photographs is seven identical orange bars, and
the spine says only "these are images" — which the extension already said. With
slivers the spine is a record of the actual pictures, and a shot is findable by
tone before its filename is read. A folder composite fixes the dead grey that
made folders the weakest objects on screen.

It is **contrast-safe by construction**: the spine is a gutter. No text sits on
it and none ever can. Unlike every other attempt to bring media into the
composition, the user's photographs cannot affect the legibility of one glyph.

*The cost, stated:* hue stops carrying **kind** for media files. Kind is in the
metadata line, in words, always. The mark is for the glance; the ink is for the
answer.

### THE BODY IS A STATE

At rest a file has **no surface** — no rectangle, no container, no card, no
rim, no radius, no shadow. A body appears under the finger and on selection.

So the material that used to be every row's default now means exactly one
thing: *this object is under your control.* Press and selection become one
gesture at two strengths rather than two unrelated effects.

And a run of selected files reads as **one solid body with the unselected ones
cut out of it** — which is what "these are now a collection" should look like.
No checkboxes. No blue.

### Removing the body was an accessibility win, not a cost

This is the most useful thing the round produced.

In V3.1 every file name sat on a translucent body over a gradient environment,
so the audit reasoned about composites, worst-case ends, lit variants and a
recessed-ground exception — and day mode *still* failed, which forced the
environment darker, which broke metadata on the environment, which pushed the
signal colour. A chain of compromises all descending from a rectangle that was
not earning its place.

With no body, text sits on a flat opaque ground. The composite problem
disappeared, the day environment went back to true paper, and
`WorldContrastTest` got shorter.

---

## 4. The compositional grammar

Six terms, kept because each earned its place in a render. Concepts from the
brief that did *not* earn one — void, cluster, focal point, transition zone —
were dropped rather than formalised.

| | |
|---|---|
| **SPINE** | the leading gutter where marks live. The only structure on screen at rest, and the thing nobody else has. 18dp from the frame, 26dp wide. |
| **COLUMN** | names and metadata, aligned at a fixed 60dp inset so a ragged spine never produces a ragged text column. |
| **THRESHOLD** | the top of a listing. The **only** place the browse composition breathes. Negative space is concentrated where the user arrives, not distributed evenly. |
| **BREATH** | a gap and an eyebrow where the sort key changes bucket. The only vertical variation permitted inside a listing. |
| **FAR PLANE** | display-scale type behind the content, occluded by it. Root only — see below. |
| **CHROME** | transient surfaces. The one place glass survives. |

### Why the browse list has no far plane

Occlusion is the cheapest strong depth cue there is, and it was tested in the
browse list first. It fails there for a concrete reason: the browse list is
**dense text**, and text over text is not depth, it is a collision. Either the
far-plane type collides with filenames or it is pushed somewhere it does not
read.

The root has a far plane because it has room for one. That is a rule, not an
inconsistency: **occlusion needs emptiness, and only the root has any.**

### Structural scale vs informational scale

- **Structural scale may be dramatic.** The threshold figure is 62sp Light; the
  root's is 74sp with a 190sp figure behind it. Set in the lightest weight
  available, which is what stops "big" from becoming "loud".
- **Informational scale is stable.** Every row is the same height. Scanning
  5,000 files requires it, and R proved what happens when it is abandoned.

Scale changes happen at **boundaries between zones**, never inside a list.

---

## 5. The remove-30% pass

The gaps, the media slivers, the environment gradient and the breath were
removed and re-rendered.

**Removing the gaps is fatal.** Four consecutive RAW files of near-identical
size fuse into a single continuous orange bar; so do the two folders and the
three HEICs. Runs of same-kind same-size files are not an edge case — they are
burst photography, screenshots and exports, which is most of a camera folder.
**Object separation in the spine is load-bearing**, and this is the same
finding V3.1 made about the cut hairline, arriving at the same conclusion from
a different direction.

**Removing media is a serious loss** (see §3).

**Removing the breath** makes the list noticeably monotonous. Kept.

**Removing the environment gradient** changes almost nothing under a full
directory. Kept only because it is nearly free and because the threshold, the
empty states and the root are where first impressions are formed.

The unusual outcome: **almost nothing survived removal.** The direction is
already close to minimal, which is the right place to stop. Untested removal
candidates, named honestly: the two-tone metadata split, and the threshold's
third line.

---

## 6. Material grammar

| | Content (the user's files) | Chrome (the interface) |
|---|---|---|
| surface | **none at rest** | translucent, optical |
| edge | none | a lit rim |
| radius | none | `Facet` geometry |
| shadow | none | real, on the three lifted tiers |
| blur | never | one modal only |

> **Content is a material you cut. Chrome is an object you place.**

This is what gives permission to make the interface expressive without turning
the user's files into decoration.

---

## 7. Typography

Two faces, settled in round one for a product reason. **Clash Display** carries
FILISH's voice — wordmark, titles, every large number; licensing verified (ITF
Free Font License permits embedding, forbids repo redistribution, so it is
fetched at build time and degrades to a tuned system stack). **The system sans**
carries the user's data, because filenames are arbitrary strings in unknown
scripts and a Latin display face would fall back silently on exactly the
content the user cares most about. *Chrome is ours; content is the user's.*

V3.2 adds three moves:

1. **The figure is the largest thing on screen and among the quietest** — 62sp
   Light, with its unit at 17sp beside it. Weight contrast does the work that
   size alone would make shouty.
2. **The size is promoted above the kind** in the metadata line — bold size,
   quiet kind — because the size is what the mark is a picture of and the two
   must agree.
3. **The threshold figure bleeds past the content gutter to the frame**, so it
   reads as architecture rather than as a heading with padding.

No second text face was added. None is needed.

---

## 8. Colour

Unchanged palette; the energy hierarchy is what V3.2 formalises.

- **identity** — the mark, which is now mostly *the user's own media*. Most of
  the colour on a FILISH screen belongs to the user, not the designer.
- **kind** — low-chroma category tints, for files with no visual content.
- **signal (verdigris)** — focus, primary action, an insight in progress.
- **danger / warn / ok** — semantic events.
- **selection** — not a colour at all; the object materialises.

Acid, lime and electric blue were considered and **not adopted.** The reasoning
is specific rather than conservative: the spine already spends the colour
budget on the user's photographs, and a high-energy accent would compete with
the one channel doing real work. In an interface whose colour comes from the
content, a designer's accent is noise.

---

## 9. Media

> **NO TEXT IS EVER DRAWN ON A THUMBNAIL.**

Three explorations have now died on this rule (V3 Direction A, V3.1 Direction
K, V3.2 Direction R). It is the hardest constraint in the system.

The spine is how media gets in without breaking it — media lives in a gutter,
where text cannot follow. A media *field* (thumbnails at browsing size) uses
the same grammar rotated: extent is magnitude, neighbours separated by a gap,
captions underneath on a known ground.

---

## 10. Storage

The browse grammar, one level up: categories are objects, the mark is the
magnitude. An insight's mark is **the bytes it can give back**, on the same
scale — so how much a cleanup is worth is legible without reading a number.

Direction R's dramatic media plates belong here, where the list is short and
genuinely sorted by size. The figure moves off the media and onto adjacent
solid ground.

**The number is always authoritative. The visualisation is supplementary.**

---

## 11. Motion implications

The grammar is unchanged; V3.2 gives it new things to be about.

| Event | What moves |
|---|---|
| a folder resolving | its mark **grows**, weaker until settled — the growth *is* the measurement |
| press | the body materialises under the finger |
| select | the body completes; adjacent selections merge into one |
| deselect | the body releases; the object returns to open ground |
| move | source mark narrows, destination widens, segments cross the gutter |
| copy | the source does not change — that is the information |
| to trash | the object shrinks toward somewhere it still exists |
| destroyed | the mark goes first, then the space closes |
| entering a folder | this spine recedes; the child's arrives |
| **stalled** | **nothing.** Motion means work |

---

## 12. Accessibility

Audited in `WorldContrastTest` and `MassTest`.

- The **full ink ramp** clears AA directly on the environment, both stops, both
  themes. This is now the background of every file name in the application.
- Every category mark clears 3:1 against the environment and against the
  inverted ground of a selection.
- A **held** object is legible and keeps its magnitude and identity.
- A **touched** object is visible (≥1.10:1), ranks below selection, and still
  carries `ink2` at AA. Day press is tight at 1.116:1 — noted, not hidden.
- Light cannot touch a text background: there is no lit body to audit, and a
  test fails if `World.lit` or `World.body` reappears.
- **A bigger file never draws a narrower mark.** Pinned.

### Owed before screens

- **Rows must be content-height with a 48dp floor.** Prototypes use a fixed
  54dp; large font settings will break it.
- The mark is decorative to a screen reader — the size is in the text — so it
  must be explicitly excluded from semantics.
- Reduced motion: a growing mark jumps to settled.
- The spine costs 60dp of horizontal inset. At the largest font setting on a
  small screen, verify filenames still get usable width; the fallback is
  narrowing the channel, not the text.

---

## 13. Performance

Flat rectangles, one gradient per media mark, one for the environment. No blur,
no `saveLayer`, no offscreen buffers. Identical on API 26 and 35.

**The one real trap is a correctness requirement, not an optimisation.** Marks
scale against the largest item in the listing, and folder sizes stream in.
Every change to that reference rescales every mark on screen — the spine
jitters while the user reads it, and every row's cached draw state invalidates.
`Mass.quantiseLargest()` snaps the reference to whole doublings;
`MassTest` pins it with a 400-partial walk. **Call it once per listing.**

**Media slivers** are new cost and must be bounded: one already-decoded
thumbnail, cropped to a 26dp-wide strip, drawn as a shader brush. No per-frame
decoding, no full-size bitmaps held for a 26dp mark. Thumbnails must be
requested at sliver scale, not at full size and then shrunk.

---

## 14. Honest weaknesses

1. **Relative scaling remains the biggest open risk.** The same file draws
   differently in different folders. The figure is always in text, but this
   needs a real user.
2. **The spine costs 60dp of a 411dp screen** — 15%. It buys the identity, but
   it is the first thing to reconsider if filenames feel cramped.
3. **Media slivers weaken the kind signal.** Mitigated by the metadata line;
   still a real trade.
4. **The mid-range compresses.** 9.4 GB and 1.8 GB differ by about 2dp.
5. **A long uniform run is still monotonous.** The breath helps between groups;
   inside a 200-file run there is nothing, by design, because scanning needs it.
6. **The environment gradient is nearly invisible** under a full directory.

---

## 15. Blind recognition

> *Could this be mistaken for Google Files, Samsung My Files, Apple Files,
> Solid Explorer, a generic Material app, a generic glassmorphism app, or a
> generic AI app?*

No, and the evidence is the control render (`v32-P-spine-no-media.png` and
`v31-M-control-no-mass.png`): the same screens with the spine's information
removed are clean, legible and completely anonymous. The spine is the
difference, and it is not a style — it is the user's own files arranged by how
much room they take.

> *Beside 20 beautifully designed apps with names hidden, would FILISH be
> identifiable?*

Yes — by one thing: **the ragged column of light down the left edge, made of
the user's photographs, whose width is how big they are.** That is the
signature. It is not borrowable, because it requires a product that knows both
the magnitude and the content of what it lists.

---

## 16. Where the code is

```
app/src/main/java/com/filish/design/glass/
  Mass.kt          the magnitude channel, the spine geometry, quantiseLargest
  World.kt         the environment, and Light
  WorldObject.kt   Modifier.worldObject() — content, no surface at rest
  Surface.kt       Modifier.lens() — chrome
  Material.kt Skin.kt Facet.kt Liquid.kt Response.kt   (V3, unchanged)

app/src/test/java/com/filish/
  MassTest.kt            the channel cannot lie about size
  WorldContrastTest.kt   the world, audited
  lab/art/Variants.kt    P, Q, R
  lab/art/Converged.kt   S, its strip test, selection, the root
  lab/world/*            the V3.1 explorations and prototype boards
```

Renders: `v32-P/Q/R-*`, `v32-S-*`, plus the V3.1 set.

---

## 17. Build this first: **the browse list**

Not the root, even though the root is the better picture.

**Why the browse list:**

1. **It is where every risk lives.** The spine, the missing body, media
   slivers, the quantiser, press and selection, density at 5,000 files — all of
   it is the browse list. The root exercises none of them.
2. **Everything else is a variation of it.** Storage is this component with
   different rows. Search is this component filtered. Trash is this component
   with a restore action. Build it once and four screens follow.
3. **It is the screen users spend their time in.** If it is wrong, nothing else
   matters; if it is right, the root is a pleasure rather than a rescue.
4. **The root is the easiest thing to make beautiful and the least informative
   about whether the system works.** Building it first would feel like progress
   and prove nothing.

**The order inside it:**

1. Content-height rows with a 48dp floor, verified at the largest font setting.
   Hard blocker for everything else.
2. The spine with flat kind tints — no media yet. Render a real 5,000-file
   directory and scroll it.
3. `Mass.quantiseLargest` wired into the streaming size path. Watch the spine
   while a large folder measures. If it jitters, stop and fix it.
4. Press and selection.
5. Media slivers last, behind a flag, with the thumbnail request pipeline
   measured before it ships.

---

# V4 — P · Spine in production

The browse list is built. This section is the implementation record: what a
future engineer needs to reproduce the language without this conversation.

## Row anatomy

```
|<-18->|<---- 26 ---->|<-16->|
|      |  ▉▉▉▉        |      DSC01847.ARW              <- name, 15sp Medium
|      |  ▉▉▉▉        |      51.3 MB · ARW · 1 wk ago  <- size promoted, rest quiet
 gutter    channel     detach  text column starts at 60dp, always
```

| Token | Value | Why |
|---|---|---|
| `Spine.gutter` | 18dp | enough that the spine is inside the composition, not clamped to the bezel |
| `Spine.channel` | 26dp | at 16dp the marks read as coloured tabs; at 26dp, detached, as a landscape |
| `Spine.detach` | 16dp | substrate must show between mark and ink, or the mark is a row decoration |
| `Spine.textInset` | 60dp | fixed — a ragged spine must never make a ragged text column |
| `Spine.markGap` | 2dp | **load-bearing**; without it same-size neighbours fuse into one bar |
| `Spine.rowPadding` | 9dp | with one name line this lands just under the floor |
| `Spine.minHeight` | 48dp | a **floor**, never a target |
| row → row | 0 | rows abut, so a selected run merges into one body |

**The leading glyph was removed.** A 40dp icon beside every name is the
card+icon+text pattern the direction rejects, and kind is already in the
metadata line in words. Removing it reclaims roughly what the 60dp inset costs,
so the name column is no narrower than before.

## Content-height rows

No height is specified anywhere. `defaultMinSize(minHeight = 48dp)` is the only
constraint; the name (2 lines, **3 at fontScale ≥ 1.5**) and the metadata
determine the rest. The mark is drawn from the *measured* size, so the spine
follows a font-scale change automatically.

`RowMetricsTest` composes real rows through the real layout system at scale
1.0 / 1.3 / 2.0 and asserts: never below the floor, always taller when type
grows, taller again when a name wraps, and ten rows exactly ten rows tall (which
guards against a stray margin breaking the selection body).

**The metadata line is a `FlowRow`, not a `Row`.** At 2.0× a single line cannot
hold `6.24 GB · 4,180 items · 3 days ago` on 411dp and a `Row` silently
truncated the date. Each separator dot travels with its piece so a wrap never
orphans one.

## Mass quantisation

```kotlin
Mass.scaleFor(bytes, previous)   // quantised to whole doublings, monotonic
Mass.markWidth(bytes, scale)     // 4dp floor, 26dp ceiling
```

Two properties, both required, both tested:

- **Quantised** — a folder walk emits hundreds of partial totals; snapping the
  reference to doublings caps rescales at the doublings crossed. 400 partials
  produce ≤ 16 rescales.
- **Monotonic within a directory** — the reference may rise (something bigger
  was found) but never fall, or every mark would widen and then narrow. It
  resets to 0 on navigation, because a new directory is a new scale.

`BrowseState.massScale` holds it. Folder measurements fold in with one
comparison (`withMeasured`) rather than an O(n) rescan, so a directory of
thousands does not become O(n²) over a load.

> **Bug found and fixed here.** `quantiseLargest` floors at 1 and `log2` clamps
> its input to 1, so a zero-byte file against a scale of 1 computed a drop of
> zero doublings — "this is the biggest thing here" — and drew at **full
> width**. An empty folder rendered as a wall of maximum marks. `relative` now
> returns 0 for any scale ≤ 1. A `MassTest` assertion had encoded the old
> behaviour and was replaced.

## Selection and press

Both are states of the same thing: the object gaining a body it does not have
at rest.

- **Press** — `World.touched()` cuts a well under the object. `indication = null`
  on the clickable is load-bearing; Compose's default is a ripple.
- **Selection** — full ground inversion. Because rows abut, adjacent selections
  merge into one collective body with the unselected cut out of it. No grouping
  logic, no first/last rounding — just adjacency.
- A selected mark keeps its width **and** its hue (scaled toward the ground, not
  alpha-faded, which would wash every kind to the same colour).
- A trailing tick appears only while a selection is running — secondary
  confirmation, never a checkbox column stealing width from every name forever.

## Media signatures (flag: `spineMedia`, default OFF)

A photograph's mark is tinted with **three tones sampled from the photograph**,
not a crop of it. Drawing the thumbnail itself would mean a bitmap draw per
visible row per frame and a retained bitmap per row, for detail nobody can
resolve in a 26dp strip. What a strip communicates is *tone*, and tone survives
reduction to three colours: a three-stop gradient costs the same as the flat
tint it replaces, and the cache entry is twelve bytes.

`MediaSignature` never decodes on its own — it samples a bitmap the loader has
already produced. `rememberSignature` requests a **32px** target purely to
derive tones. Returning null is normal and the fallback (kind tint) is a
finished design, not a placeholder.

**It stays off until measured on real hardware with a real camera roll.** No
decode-cost claim is made here.

## Findings

- **5,000 files, real pipeline:** 5,040 entries listed in **66 ms**, whole-spine
  geometry in **4 ms** (`LargeDirectoryTest`, sparse files, JVM). This proves
  the listing and geometry paths; it says nothing about frame rate, which needs
  a device.
- **Folder marks were muted** (`catFolder` → `#6E6A5E` / `#8A867E`). At full
  strength a folder was the heaviest object in the spine regardless of size —
  the exact false hierarchy the mark exists to prevent.
- `.RAW` classifies as Other, not Image. That is existing intended behaviour
  (`FileKind` excludes ambiguous bare `raw`), visible now that the mark carries
  kind colour.

## Anti-patterns

- Any `Card`, `Surface`, `ListItem` or `Scaffold` default background in a
  content row. There is exactly one place a body may come from:
  `Modifier.contentObject`.
- `indication` left at its default on a row — that is a ripple.
- A fixed row height.
- A `Row` for the metadata line — it truncates at large font scales.
- Deriving a mark tint from anything other than magnitude, kind or the object's
  own content. Never from a filename hash.
- Letting the mark be the only place a size appears.

## Not yet done

- **Folder composite marks** from contents. `SizeResolver` already walks every
  folder and could accumulate a kind histogram for almost nothing; deferred
  rather than half-built, so folders currently use a neutral.
- **Grid view, Properties, Duplicates** still use the pre-V4 `FileMark`
  (thumbnail or glyph). They were moved to `FileMark.kt` untouched — a component
  that is neither the old language nor the new one is worse than either.
- **Threshold composition** (the display-scale figure at the top of a listing)
  is designed but not built; the browse screen keeps its V2 path rail and
  action bar.
