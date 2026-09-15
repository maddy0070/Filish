# FILISH motion

Source of truth: `app/src/main/java/com/filish/design/Motion.kt` and
`app/src/main/java/com/filish/design/glass/Liquid.kt`.

Every animation has to answer one question: **why does this move?** If the
honest answer is "because it looked nice", it was deleted.

---

## The four jobs

What survives falls into exactly four categories. An animation that does not
belong to one of them does not ship.

| Job | Says |
|---|---|
| **Continuity** | the thing you are now looking at is the thing you tapped |
| **Causation** | this changed because you did that |
| **Progress** | work is happening, and this much of it is done |
| **Consequence** | something was destroyed or created; the space reacted |

---

## Durations, by job

Durations are short and **differentiated by job rather than uniform**. A
selection is not a navigation and must not feel like one: selection is a change
of state in something already on screen, so it is nearly immediate, while
navigation is a change of place and needs long enough for the eye to follow the
displacement.

| Token | ms | For |
|---|---|---|
| `QUICK` | 120 | state change on something already on screen |
| `BASE` | 220 | the default |
| `CONSIDERED` | 320 | motion carrying real information — a value settling, a list regrouping |
| `DELIBERATE` | 440 | consequence; destruction and creation may take their time |
| `REDUCED` | 90 | the reduced-motion substitute |
| `Liquid.LEVEL` | 620 | a level changing under its own weight |

`DELIBERATE` is not decorative. It is the window in which a departing row
collapses, and `BrowseViewModel.DEPARTURE_MILLIS` is *derived* from it rather
than restated — a coupling test caught that drifting once already.

---

## Curves

- **`Motion.enter`** — aggressive front, long tail. The object arrives fast and
  eases into place, which reads as weight rather than as a slide.
- **`Motion.exit`** — things leaving accelerate away. They do not need watching.
- **`Motion.adjust`** — symmetric, for values being adjusted rather than moved.
- **`Motion.physical`** — critically damped spring, for anything under direct
  manipulation.
- **`Liquid.viscous`** — reluctant to start, slow to let go. The opposite shape
  to `enter`: for something whose *value* is changing, which should look like it
  took effort.
- **`Liquid.wake`** — instant response, slow relaxation. The one place liquid is
  not viscous, because a user knows exactly when they touched the screen and any
  delay there reads as lag rather than as material.

### Nothing overshoots

Not ever. A bounce implies elasticity, and files are not elastic. Worse, an
overshooting number has briefly displayed a figure that is not true, which in a
file manager is a correctness bug wearing a motion costume.

---

## Liquid physics (V3)

"Liquid" does not mean wobble. It means the three properties of a liquid that
have nothing to do with bouncing:

**Viscosity** — motion starts reluctantly and lets go slowly.
**Finding level** — a quantity settles toward a value asymptotically rather
than arriving at it.
**Surface tension** — a boundary lags behind the body it bounds;
`Liquid.TENSION_LAG` is how far the meniscus trails the volume.

All three are honest about the underlying system. A folder size really is
converging while you watch it; a transfer really is filling something up. That
is the entire argument for this vocabulary: **FILISH's data settles, so
FILISH's motion settles.** If the data were instant, the motion would be
instant.

This is why the engine streams. `SizeResolver`, `SearchEngine`,
`StorageAnalyzer` and `DuplicateFinder` all emit partial results with explicit
`settled` flags, and the motion is the visible form of that flag.

---

## Reduced motion

Not "nothing moves" — feedback is how the interface stays honest, and a user
who asked for less motion has not asked to be told less. What changes is the
*kind* of feedback:

| Element | Full | Reduced |
|---|---|---|
| a level | settles viscously | jumps to its value; the figure still updates |
| the meniscus | trails the volume | no trail — the trail is its only decorative part |
| the band | slides between states | steps |
| a lens | translates | changes tone |
| continuous animation | runs while work is in flight | becomes discrete state |

The information content is identical. Only the physics is dropped.
`Motion.respecting` and `Liquid.respecting` resolve this at the call site, so
forgetting is a visible omission rather than a silent one.

---

## V3.1 — motion as a physical event

The art direction ([visual-language.md](visual-language.md)) gives motion
things to actually be *about*. Each of these is a physical event, not a
transition:

| Event | What moves |
|---|---|
| a folder's size resolving | its mark **grows**, at reduced intensity, and stops when the figure settles |
| press | the body **materialises** under the finger — at rest an object has no surface at all |
| an object selected | the body completes; adjacent selections merge into one continuous mass |
| deselection | the body releases and the object returns to open ground |
| a move in flight | the source's mark narrows, the destination's widens, segments cross the gutter |
| a copy | nothing about the source changes — that is the information |
| departing to trash | the object shrinks toward somewhere it still exists |
| destroyed | the mark goes first, then the space closes |
| restore | the mark returns before the row does |
| entering a folder | this directory recedes; the child arrives in the space it leaves |
| a storage insight | the region's marks light; the bloom is the only thing moving |
| **stalled** | **nothing.** A stalled transfer holds its position rather than pulsing |

That last row is a rule, not an entry. Motion means work. A thing that has
stopped working must stop moving, or the interface is lying — and a pulsing
"still trying" animation over a transfer that died is the most common version
of that lie.

The growing mark is worth calling out: it is not a loading animation standing
in for a measurement, it **is** the measurement. `SizeResolver` streams partial
totals with an explicit `settled` flag, and the mark is that flag made visible.
When it stops growing, the number is true.

### The jitter trap

Marks scale against the largest item in the listing, and that reference moves
while a folder is being measured. Without `Mass.quantiseLargest()` every
partial result rescales every mark on screen — a left edge that shivers for the
whole duration of a walk. Snapping the reference to whole doublings caps the
rescales at the doublings crossed. This is a motion requirement as much as a
performance one.

---

## The performance rule

**At most one continuously animating element on screen at a time, and only
while real work is in flight.**

A directory of fifty thousand files with two hundred shimmering rows is not a
liquid interface, it is a space heater — and on the mid-range hardware this
application is actually for, it is a dropped-frame machine.

A row's band does not animate at rest. The substrate does not animate, ever.
The volume animates only while its figure is changing. When the work finishes
the motion stops, which is also the clearest possible signal that the work
finished.

> **V4 note.** Press and selection are now the same gesture at two strengths:
> the object gains a body it does not have at rest. `indication = null` on every
> content row — Compose's default indication is a ripple.
