# Roadmap

Honest status. "Done" means built, wired, and reachable from the interface.

## Phase 1 — done

Filesystem engine · storage permissions with an honest access screen · file
browser · path-rail navigation · selection with live aggregate sizing ·
sorting, grouping, filtering · search grammar · file properties · copy · move ·
rename · create folder · delete with provider-aware trash routing · storage
intelligence with actionable findings · duplicate detection · image viewer ·
video player · audio player · PDF and text viewer · custom design system ·
motion language · settings · opening sequence · provisional icon

## Phase 2 — next

Ordered by value against effort.

1. **Foreground service for operations.** The one real correctness gap: a
   transfer stops if Android kills the process. Also unlocks background audio.
2. **Archives.** ZIP first (`java.util.zip`, no dependency): inspect, extract,
   create. Then TAR/GZIP. Nothing is offered in the UI today, so there is no
   dead end — but it is a real gap against a full file manager.
3. **Operation history and undo.** Move and rename are reversible; copy is
   deletable. Delete is only reversible where the trash route was taken —
   which FILISH already knows, so the history can say honestly which entries
   can be undone.
4. **Drag selection.** Long-press then drag across rows. Needs a discoverable
   alternative for accessibility, which the existing refinement chips provide.
5. **Folder comparison.** Two folders: only in A, only in B, same name
   different content. The duplicate engine's fingerprinting already does the
   expensive part.
6. **Similar-image detection.** Perceptual hashing for near-duplicates (a JPEG
   and its re-export). Distinct from exact duplicates and must be labelled as
   a guess, not a match.

## Phase 3 — polish

- A dedicated motion pass, including a proper opening sequence
- Final app icon and identity (the current mark is explicitly provisional)
- Tablet and foldable layouts using width rather than stretching
- Keyboard navigation for desktop-mode and external keyboards
- Performance work against genuinely pathological trees (10⁶ files)
- Localisation

## Deliberately not planned

- **Cloud storage.** FILISH has no `INTERNET` permission and that is the
  product, not a limitation to grow out of.
- **Office document rendering.** See
  [platform-constraints.md](platform-constraints.md) §6.
- **A file index/database.** See product principle 9.
- **Root browsing.** A different product with a different risk profile.
