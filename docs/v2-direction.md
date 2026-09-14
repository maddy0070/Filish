# FILISH V2 — audit and direction

Written after the V1 build was installed and used on a real device. V1 was the
engineering foundation; this is the pass that makes it a product.

---

## 1. The audit

### What works and stays

- **The selection ledger.** Leading with total size rather than a count is
  still the strongest idea in the product, and folder measurement streaming
  into it is the thing no other file manager does.
- **The path rail.** Pinning both ends and scrolling only the middle survives
  contact with real paths.
- **Honest deletion language.** Naming the mechanism and reading the real
  expiry date back was right. It was the *execution* underneath that was broken.
- **Storage findings that open the files they describe.**
- **The Conduit.** Tying segment speed to measured throughput means a stalled
  transfer visibly stops.

### What was broken

**Deleting several gigabytes of RAW files did nothing at all.** Root cause was
not animation or refresh. FILISH holds `MANAGE_EXTERNAL_STORAGE`, but every
delete was routed through `MediaStore.createTrashRequest` — the consent flow
that exists for apps *without* broad storage access. An app holding all-files
access can set `IS_TRASHED` directly. The dialog path was both redundant and
the one with a Binder size limit, so it threw on large selections. The throw
was swallowed by `runCatching{}.getOrNull()`, and the confirm handler was an
empty lambda, so the failure had nowhere to go. The confirmation sheet the
user saw *was the error path* rendering as though everything were fine.

Three compounding defects, each individually survivable, together producing
perfect silence. Fixed in commit "Fix the delete that silently did nothing".

### What felt generic

- **The control strip was four 20dp icons of equal weight, none labelled.**
  That is the Android overflow row, and it is generic because it treats four
  unrelated things as interchangeable.
- **Sort, filter and layout were three controls answering one question.**
  Splitting "how should this folder be presented to me" across three anonymous
  icons is why none were discoverable — each was a fragment of an intention
  the user could not name.
- **Selection was invisible.** Long press worked and always had, but nothing
  said so. A gesture nobody knows about is a feature nobody has.
- **`+` never said what it made.**
- **There were two openings.** Android 12+ draws a system splash on every cold
  start whether asked to or not. FILISH had not configured it, so it got the
  default treatment and then played its own opening afterwards — a stutter,
  not an entrance.

---

## 2. The direction

**Name everything a beginner needs; hide only what they don't.**

The V1 interface was quiet to the point of being mute. V2 does not add
chrome — it *labels* the things that were already there, and merges the
controls that were answering the same question.

### What changed

| | V1 | V2 |
|---|---|---|
| Organising | 3 icons (sort / filter / layout) | **Arrange** — one door, state shown on the trigger |
| Selecting | long press only, undiscoverable | **Select** — a named mode, ledger appears with guidance |
| Creating | `+` → new folder | **New** — a place, a text file, or bring what's staged |
| Deleting | silent failure | verified, batched, progress-reported, two departure motions |
| Opening | system splash, then a second one | one continuous move from the same mark |

### The principles that drove it

1. **A label costs one word and buys discoverability.** Icons are for things
   already understood, not for teaching.
2. **One question gets one control.** Arrange exists because sorting, grouping,
   filtering and layout were never four intentions.
3. **Add means "what can arrive here"**, not "what can be constructed". You
   cannot create a photograph; things arrive. So the staged clipboard belongs
   under New, not in an unrelated bar.
4. **Two outcomes must not look identical.** Recoverable and permanent
   deletion get different motions, because animating them the same way quietly
   tells the user they are the same act.
5. **No path may end in silence.** Every delete outcome is a completed result
   or a stated reason. There is no branch that does nothing.

---

## 3. Deferred, honestly

Ranked by value, not by ease.

1. **A real device pass.** The build environment has no KVM, so there is no
   emulator. Screenshots come from Robolectric rendering the real Compose tree
   through real Skia, which catches layout, contrast and hierarchy — but not
   frame timing, not scroll feel, and not how the splash handoff actually
   looks on a phone. The delete fix in particular was diagnosed by reading the
   platform contract, not by reproducing on hardware.
2. **Colour.** V1's palette is contrast-audited and semantically coherent, but
   it is quiet. It was left alone this pass because the discoverability and
   correctness problems were worth more than a repaint, and repainting before
   fixing hierarchy would have been polishing a bad structure.
3. **Storage intelligence depth** — the categories and findings are sound but
   the screen is still a list of sections rather than a designed argument.
4. **Settings as a control centre** rather than grouped switches.
5. **Archives, background operations, operation history** — see
   [platform-constraints.md](platform-constraints.md) §7.
6. **View modes beyond list and grid.** Compact and details views are
   plausible; neither was built, so neither is offered.

---

## 4. What would still make someone choose FILISH

The honest differentiator is not the visual design. It is that **FILISH
answers questions other file managers decline to answer**:

- how much space does this folder actually take
- what is my storage made of, and what can I act on
- what exactly will happen when I delete this, and did it happen

V2 made the first and third of those trustworthy. The second is where the next
pass should go.
