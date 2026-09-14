# FILISH Visual Design System V3 — Substrate & Lens

Status: **design language complete, screens not yet built.**

This document is the record of how V3's material was arrived at, what it is,
and what was deliberately thrown away. It exists because the decisions in it
are almost all counter-intuitive, and a token file full of numbers with no
argument behind it gets edited back to the obvious answer within a month.

Nothing in this round rewired a screen. That was the point of the round.

---

## 1. The brief, and the first thing it ran into

The ask was a *liquid glass* visual language: distinctive, premium, tactile —
and explicitly **not** generic glassmorphism, not everything blurred, not every
component a transparent card.

Generic glassmorphism is, concretely, **backdrop blur**: a surface that
diffuses whatever is behind it. On Android that is not a style choice, it is a
wall:

- `RenderEffect`, which backs `Modifier.blur()`, is **API 31+**. FILISH
  supports API 26. A third of the supported range gets no blur at all — the
  effect silently becomes nothing, so the design does not degrade, it simply
  fails to exist.
- **Compose has no backdrop-blur primitive.** `Modifier.blur()` blurs a
  composable's *own* content, not what is behind it. Blurring a backdrop means
  rendering it into an offscreen buffer and blurring that — per surface, per
  frame, while scrolling a directory of fifty thousand files.

So a design that needs backdrop blur looks broken on older devices and drops
frames on newer ones. That is not a compromise to manage. It is a direction to
reject, and rejecting it early is what made the rest of the round productive.

**Blur survives in exactly one place**: behind a modal. A modal genuinely
occludes, it is one surface, it is not scrolling, and it is on screen briefly.
Below API 31 it degrades to a heavier tint, which carries the same meaning.
That is the whole blur budget: `Material.modalBlur`, one value, one use.

---

## 2. Five directions, and what each one was wrong about

Each was built as a **rendered specimen**, not a description — the same four
file rows, the same selected row, the same primary action, so the comparison
was between materials rather than between layouts. (`app/src/test/.../lab/`,
rendered through real Skia by Robolectric in native graphics mode.)

| | Direction | Idea | Verdict |
|---|---|---|---|
| A | **Vapour** | Textbook frosted glass: blur + transparency | **Rejected.** Blur rendered as a no-op — which *mirrors real API<31 behaviour*, the strongest possible evidence. Worse: metadata contrast varied with whatever was behind the panel. In a file manager the backdrop is a wall of photo thumbnails, so the legibility of a file size became a function of the user's camera roll. |
| B | **Contact** | Thin glass *in contact* with a surface: tint, edge light, tight contact shadow | **Kept** — the material model. Physically right, costs a fill and a stroke, identical on API 26 and 35. |
| C | **Deep** | Thick glass, heavy shadows, strong parallax | **Rejected.** Reads as floating, which is a lie: files rest *on* storage. Also the most expensive option for the least information. |
| D | **Crystalline** | Faceted, precise, refraction at cut edges | **Kept** — the signature. The refraction band came from here. The rest was decoration looking for a job. |
| E | **Strata** | The ground is the liquid; storage has a level | **Kept** — the meaning. The only idea in the round that made the material *about* the product. |

The synthesis is **B's physics + D's signature + E's meaning**.

---

## 3. Substrate & Lens

Three materials, and one rule.

**SUBSTRATE** — the ground. Storage itself. The only genuinely liquid element:
it has depth, and where storage is the subject it has a level and a meniscus.

**LENS** — thin glass resting on the substrate. Chrome, controls, rows,
transient surfaces. It tints, it catches an edge light, it refracts at its rim.
**It never blurs.**

**INK** — the content. File names, sizes, dates. Solid, opaque, maximum
contrast.

> ### THE RULE: information is never made of glass.
>
> Everything a user reads in order to make a decision is ink on a settled
> surface. Translucency is for the things *around* information, never for
> information itself.

This one rule is what stops the material eating legibility, and it is why the
existing, already-audited `Palette` is untouched by V3. Ink is not glass, so
ink did not change. The new `Skin` describes only what the surface *under* the
ink is made of.

### Where the liquid belongs

An early version put the storage level behind the whole file list. **Removing
it was the single biggest improvement in the round.** Behind a scrolling list a
waterline is wallpaper, and every row cuts it into fragments, so it reads as a
rendering fault rather than as a level.

The volume appears only where storage is the subject: the header gauge, the
storage screen, a delete confirmation, a transfer. Everywhere else the
substrate is a quiet tonal ground.

### The Band — the signature

A 4dp vertical sliver of light at the **leading edge** of every interactive
object, brightest where light enters and falling away down the edge. It is the
refraction you would see at the cut rim of a real lens.

It earns its place by doing three jobs at once, which is the test every
decorative idea has to pass:

- **identity** — it is what makes a FILISH row recognisable without a logo
- **kind** — it carries the file-category tint, so type is legible at a glance
  instead of by reading an extension
- **state** — full strength on selection, the signal colour on focus

Because it is a solid mark rather than a tint on the whole row, it survives
greyscale and it reduces the contrast of no text at all.

---

## 4. Six tiers

Deliberately few. A system with nine surface types has no hierarchy, because
nobody can hold nine levels in mind — they read it as "some things are glassy".

| Tier | What it is | Edge | Shadow |
|---|---|---|---|
| **Substrate** | the ground | none | none |
| **Well** | cut *into* the ground: fields, tracks, the hole a dragged item leaves | reversed | none |
| **Resting** | rows, chips, tokens — most of the interface | hairline | **none** |
| **Lifted** | action bar, ledger, transfer bar | hairline | real |
| **Modal** | sheets and confirmations; the only tier allowed blur behind it | hairline | real |
| **Carried** | in the hand | hairline | real, tighter |

`Well` was not in the original model. It was added because the rendered tier
ladder showed a search field drawn as a lens coming out **lighter** than the
surface it was supposed to be a hole in. A recess is not a surface with a
different radius; it is the other direction, and the model had no word for it.

---

## 5. Two laws that came out of measurement

These are the findings. Both were discovered by a test failing, not by taste.

### Edges are drawn away from the ground, not toward the light

The first build drew every rim as a bright white hairline, because that is what
a lit glass edge looks like and it is what every glass interface does. The
audit measured the light theme's resting rim at **1.03:1** against the surface
it was supposed to be separating. It was not subtle; it was absent.

The cause is structural. A lens tints *toward* white, so on a near-white ground
the lens is already close to white and there is no tonal headroom above it.
Light can only be caught at an edge when the surround is darker than the object.

So: **night → the rim is light. Day → the rim is shade.** Which is also just
what a sheet of glass on white paper looks like: not a bright edge, a fine dark
line. The same law governs the meniscus, which measured **1.28:1** as white and
is now a dark waterline in day. It is what keeps the light theme from looking
like cling film, which is the characteristic failure of light glassmorphism
everywhere.

Applied vertically, the same law gives the rim its gradient — strongest at the
top in night, strongest at the bottom in day — and reversing it is what makes a
well read as a hole.

### Translucency needs light behind it

A semi-transparent white over a near-black ground does not reveal the ground.
It produces a muddy grey, and every percent of white it adds is a percent of
contrast taken from the text on top. Dark Resting was specified at 13% white
and measured **3.97:1** for metadata — a clear AA failure that looked perfectly
pleasant on screen.

So in dark mode **only the lightest tier stays glass.** A row is thin enough
that the tint reads as material; everything above it becomes an honest opaque
tone with a lit rim. The hierarchy is unchanged — only the medium carrying it —
exactly as `Plane` already does for shadow.

This is why dark glassmorphism universally looks worse than light
glassmorphism. Most systems accept it. This one does not.

---

## 6. The remove-30% test

The full material was rendered beside a version with three of the lens's six
effects stripped out (`v3-strip-day.png`, `v3-strip-night.png`). The question
was not which is prettier. It was **whether the removed third does any work.**

The stripped column won, clearly. Three things changed as a result:

**The resting contact shadow was deleted entirely.** A resting row cannot
afford a real blurred shadow — there are hundreds of them — so it had a
hard-edged offset outline at 6%. Side by side that did not read as contact, it
read as a printing misregistration: a grey duplicate of the row sitting two
pixels down. Nothing is lost; the rim and the tint already separate a row from
the ground. One full-size draw call per row is gained back.

**The band's fall-off was halved** (1.0 → 0.26 became 1.0 → 0.62). The deep
fade looked like refraction in isolation and like the band running out of ink
in a list — an image row's band lost its orange entirely in its lower half, so
the kind signal, the band's primary job, was legible only at the top of the
mark. Decoration yields to the job.

**The body gradient was narrowed** (0.55 → 0.20 became 0.55 → 0.38). The wide
version dissolved the bottom third of every row back into the substrate, so the
row's lower boundary went ambiguous and a column of them read as smeared.
Physical accuracy lost that argument: in a file manager the edges of a row are
how you scan.

What survived the strip: the shape, the band, the rim, and a narrow tint
gradient. The gradients survive because they are free — the same brush either
way — not because they were carrying the design. That is worth being honest
about.

---

## 7. Interaction states, as material

Every state is a different thing *happening to the material*, so every state
survives greyscale and sunlight, and none of them borrows a colour the
file-kind system is already using.

| State | What happens |
|---|---|
| Rest | the lens sits on the substrate |
| Hover | it lifts a hair (pointer and stylus only; never fires on a phone) |
| **Pressed** | **it becomes a well** — fill inverted, rim light flipped to the far side |
| Focused | the band takes the signal colour, the rim doubles in width |
| Selected | the ground inverts; the band goes solid |
| Disabled | it stops being an object: no fill, no rim, ink steps down the ramp |
| Dragging | it leaves the ground entirely, and leaves a well behind |

**Press deserves its own note**, because it was broken once. The first version
relied on the contact shadow collapsing. Then the remove-30% pass deleted the
resting contact shadow — correctly — and took the press signal with it.
Rendered side by side, rest, hover and pressed were indistinguishable. For the
most-used interaction in the application that is not a polish issue, it is a
broken control.

The fix was already in the system: pushing an object into a soft ground far
enough makes it a recess, and `Tier.Well` is exactly that. So press
interpolates the whole lens toward a well. No ripple, no scale, no shadow, no
new token, two interpolations of work. A ripple would have been a claim that
the surface is a pool of ink spreading from the touch point — a fine idea
belonging to somebody else's material.

**Disabled is a destination, not an operation.** It resolves *to* the substrate
rather than fading whatever the control happened to be. In V2, "the same thing
at 38% alpha" produced solid black slabs, because `Color.Transparent` is black
at zero alpha and scaling its alpha makes it visible. `TransparentAlphaTest`
pins the trap.

---

## 8. Shape

**Asymmetric shapes rest ON the substrate and can be acted on.** Leading edge
nearly square, trailing edge soft.
**Symmetric shapes are cut INTO the substrate** — wells, fields, tracks — or
arrive from a screen edge.

So the silhouette alone says whether something is an object or a hole, before
any colour or label is read.

The leading edge is 4dp rather than 12dp because the band lives there. Against
a 12dp corner the band gets clipped into a pair of crescents that read as a
rendering artefact. 4dp against a 4dp band keeps the band a band while still
killing the hard mathematical corner that makes an interface feel like a
spreadsheet. The result is directional — the object reads as entering from the
start side — and in RTL it reverses for free, because these are start/end
corners and the band is drawn at the leading edge for the same reason.

This is an **open revision of V2's position**, which was "FILISH rounds almost
nothing". That was right for a system built out of tone and space. V3 changes
the claim — a lens *is* a discrete object with a cut edge — so it changes the
shape.

---

## 9. Motion

"Liquid" here does **not** mean wobble. Elasticity is a claim about the
material, and a file is not elastic — a 40 GB video does not boing. An
interface that says it does is lying about the thing it manages, and the lie
gets tiring by the second week, which is when a file manager actually gets used.

Liquid means the three properties of a liquid that have nothing to do with
bouncing:

- **Viscosity** — motion starts reluctantly and lets go slowly. It never snaps.
- **Finding level** — a quantity settles toward a value asymptotically rather
  than arriving at it.
- **Surface tension** — a boundary lags behind the body it bounds. The meniscus
  reaches its level slightly after the volume does.

All three are honest about the underlying system: a folder size really is
converging while you watch it, and a transfer really is filling something up.
That is the entire argument for this vocabulary. **FILISH's data settles, so
FILISH's motion settles.** If the data were instant the motion would be instant.

Nothing overshoots, anywhere. An overshooting number has briefly displayed a
figure that is not true, which in a file manager is a correctness bug wearing a
motion costume.

`Motion`'s four jobs — continuity, causation, progress, consequence — and all
its durations are unchanged. `Liquid` adds the physics for the one new thing V3
introduces: material with a level.

**Reduced motion** keeps the information and drops the physics: the level jumps
to its value while the figure beside it still updates; the meniscus stops
trailing; the band steps instead of sliding; a lens changes tone instead of
translating.

---

## 10. Performance

- **No backdrop blur anywhere**, except one modal. No `saveLayer`, no offscreen
  buffers, no `RenderEffect` in any scrolling context.
- **The tier that appears most often is the tier that costs nothing.** Resting
  has no blurred shadow and now no drawn shadow either: a fill, a stroke, a
  4dp rect. `Material.castsRealShadow` states which tiers may force a graphics
  layer, and it is the three transient ones.
- **Brushes and outlines are built in `drawWithCache`**, so they are recreated
  on resize, not on every frame.
- **At most one continuously animating element on screen**, and only while real
  work is in flight. A directory with two hundred shimmering rows is not a
  liquid interface, it is a space heater. When the work stops, the motion stops
  — which is also the clearest possible signal that the work finished.
- Identical rendering on API 26 and API 35. Nothing in the material is gated on
  a version except the one modal blur.

---

## 11. Accessibility

`GlassContrastTest` audits **real composited surfaces**, not the opaque
approximations a designer wishes they were.

The standard objection to glass interfaces is that nobody can say what the
background of a given glyph is, so nobody can prove it is legible — and in most
such systems that objection is correct, because the backdrop is arbitrary
content. Substrate & Lens is built so the backdrop is never arbitrary: a lens
rests on the substrate and nowhere else, so the composite is **computable**
(`Skin.settled`) and therefore testable. The audit uses the *worst* end of each
lens, not the comfortable one.

Also pinned:

- **The recessed-ground rule.** Any ground tinted *away* from the substrate — a
  well, the storage volume — takes one step up the ink ramp: its quietest text
  is `ink1`, never `ink2`. Day `ink2` measures 4.26:1 in a well and 3.55:1 on
  the light volume, both AA failures, both entirely unremarkable on screen. It
  also makes the classic too-faint-placeholder failure impossible to specify by
  accident.
- The band clears 3:1 against every surface it can appear on, in every category
  tint, in both themes.
- The rim must be measurably distinct from the surface it bounds.
- Objects are lighter than the ground; wells are darker. Pinned so a future
  tint edit cannot quietly invert a hole.
- Every state is structural, so none of it depends on colour discrimination.

---

## 12. What this is not

**Not Apple.** No backdrop blur, no vibrancy, no specular highlight sweep, no
continuous corner curvature, no floating translucent slabs. The material's
governing idea — contact rather than levitation — is the opposite claim.

**Not Material.** No elevation scale, no ripple, no tonal surface tiers, no
FAB, no card-stack ground. Press sinks instead of rippling; depth is a named
plane rather than a dp value; the ground is a substrate rather than a canvas
for cards. There is no Material dependency in the project at all.

**Not another file manager.** The band, the substrate, the storage volume as a
body with a level, selection by inversion rather than blue tint, and the
asymmetric silhouette are all specific to this product.

---

## 13. Screen-level principles (for the round that builds screens)

These came out of the renders and are the things most likely to be got wrong
when the screens are finally assembled.

1. **The gap decides whether this looks like FILISH.** At card spacing the rows
   are cards, and cards on grey is the most common interface in the world. At
   list spacing the *same tokens* read as one continuous body of material with
   cuts in it, and the bands line up into a column that carries the folder's
   composition at a glance — which a card list cannot do at all. **The material
   is not what keeps FILISH from looking generic. The density is.** Evidence:
   `v3-density-day.png`, `v3-density-night.png`.
2. **The volume's figure and its level must describe the same quantity.** The
   current specimen shows "38.4 GB free" over a level drawn at 62% *used*. Pick
   one.
3. **Progress and occupancy never share a shape.** Progress is a track that
   fills toward completion; occupancy is a body with a level. Confusing them
   would make a transfer look like a full disk.
4. **One band column per screen.** The band's power is that it forms a vertical
   strip; two competing strips destroy it.
5. **The substrate is never decorated.** No pattern, no texture, no gradient
   beyond its two near-identical stops.

---

## 14. Where the code is

```
app/src/main/java/com/filish/design/glass/
  Material.kt   tiers, tint/rim strengths, the blur gate, the band
  Skin.kt       substrate and lens colour, compositing, Day/Night
  Facet.kt      shape language
  Liquid.kt     motion physics
  Response.kt   interaction states
  Surface.kt    the drawing modifiers: substrate(), volume(), lens()

app/src/test/java/com/filish/
  GlassContrastTest.kt      the audit
  lab/Directions.kt         the five explorations
  lab/Synthesis.kt          the synthesis
  lab/MaterialLab.kt        specimens built from the PRODUCTION tokens
```

`MaterialLab` imports from `com.filish.design.glass`, so its pictures change
the moment a token stops meaning what it says. That is the only way a design
system stays true to itself once people start editing numbers.

Rendered output lands in `app/build/screenshots/`:
`v3-tiers-*`, `v3-states-*`, `v3-density-*`, `v3-strip-*`, `v3-volume-*`.

---

## 15. Next round

The design language is complete and audited. Screens are not built, on purpose.

1. Component inventory against the tiers — row, header, action bar, arrange
   sheet, create sheet, confirmation, ledger, transfer, gauge, empty state.
2. Rebuild the browse list at **list density** and re-render before anything
   else; density is the highest-risk decision and the cheapest to verify.
3. Migrate existing components tier by tier, re-running the audit each time.
4. Storage screen: the one place the volume belongs.
5. Only then, an APK.

### Open questions

- Does the band survive a grid layout, where there is no left edge to run down?
  It may need to become a top edge in grid mode, or the grid may need thumbnails
  to carry kind instead.
- Thumbnails are pictures with arbitrary colour, and they sit inside a lens. The
  audit covers ink on the lens, not ink on a thumbnail. A caption over a
  thumbnail needs its own rule.
- `Corner` (V2) and `Facet` (V3) now coexist. They should converge once every
  component has moved.
