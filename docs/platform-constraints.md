# Platform constraints

What Android actually permits a third-party file manager to do, what it does
not, and where FILISH stops short of a claim it cannot back.

This document exists because the difference between "we did not implement it"
and "the platform does not allow it" matters, and because a file manager that
overstates its guarantees is worse than one that underpromises. Every
limitation here was established against the platform APIs rather than assumed.

---

## 1. Deletion and the system trash

**The requirement.** Deleting should put files in the device's recycle bin,
comparable to Google Files, with the usual ~30-day expiry.

**What exists.** There is no universal, device-wide Trash that any application
may write into. What exists is *MediaStore's* trash. From Android 11 (API 30),
`MediaStore.createTrashRequest()` sets `IS_TRASHED` on rows in MediaProvider's
database. Trashed items vanish from galleries and listings, remain
recoverable, and are permanently removed by MediaProvider itself once
`DATE_EXPIRES` passes. This is a real, OS-managed recycle bin — the same
mechanism the Google Photos and Files bins are built on — and FILISH uses it
as its primary deletion route.

### What FILISH cannot do, and does not claim

| Claim | Reality |
|---|---|
| Put files into **Google Files' Trash** | **Not possible.** That bin is Google Files' private storage plus its own bookkeeping. No API exposes it. An app claiming this is describing MediaStore's trash and calling it Google's. |
| Trash anything on **Android 10 or older** | **Not possible.** `createTrashRequest` does not exist before API 30. On those releases FILISH says "Permanent — this version of Android has no system trash" before the user confirms. |
| Trash a document reached through **SAF** | **Not possible.** `DocumentsContract` has `deleteDocument` and no trash equivalent. A provider's own bin, if it has one, is not addressable. |
| Trash a **folder** | **Not possible.** MediaStore rows are files. Shredding a folder into individually-trashed files would "restore" as a flat pile in the wrong place, so FILISH deletes folders permanently and says so. |
| Promise **"30 days"** | **Not honest.** The retention period is MediaProvider's policy and has varied across releases and vendors. |

### What FILISH does instead

**It reads the expiry back.** After trashing, FILISH queries `DATE_EXPIRES`
on the item it just trashed and reports the actual date. If the platform says
the 14th, the user is told the 14th.

**It widens what can be trashed.** A file with no MediaStore row has nothing
to trash, which would ordinarily force a permanent delete on, say, a `.zip` in
a user-made folder. Before giving up, FILISH asks MediaProvider to index the
file (`MediaScannerConnection.scanFile`); if it accepts, the file acquires an
identity and becomes trashable. This meaningfully extends recoverable deletion
beyond photos and video. It is attempted, then **verified** by reading
`IS_TRASHED` back — never assumed.

**It resolves the route before asking.** `DeleteEngine.plan()` runs before the
confirmation is shown, and the confirmation states the route in the words that
apply to it. "Recoverable from your device's trash" and "Permanent — folders
cannot be placed in the system trash" are different decisions and they look
different.

**It does not double-confirm a reversible delete.** When the route is the
system trash, Android shows its own consent dialog — MediaProvider requires
it. Stacking a second confirmation in front of the platform's is a tax, not a
safeguard, and it teaches people to dismiss dialogs unread. The extra
confirmation is reserved for genuinely irreversible deletions. This is a
preference (Settings → When things move), because reasonable people disagree.

**If provider capabilities change**, `DeleteEngine.Route` is the single place
that decides, and the UI already renders whatever it returns.

---

## 2. Storage access

FILISH requests `MANAGE_EXTERNAL_STORAGE` (All files access). A general file
manager is one of the categories Google documents as a legitimate use, and
without it whole regions of the user's own storage are simply invisible.

The application still works without it. Media-only access (`READ_MEDIA_IMAGES`
/ `VIDEO` / `AUDIO` on API 33+, `READ_EXTERNAL_STORAGE` below) is offered as a
real choice rather than a punishment, and the access screen says plainly what
each one does and does not make visible.

**`Android/data` and `Android/obb` are not readable** on Android 11+ for any
third-party app, by design. FILISH reports the denial with that reason rather
than a generic error.

---

## 3. No network, structurally

FILISH declares **no `INTERNET` permission**. The process cannot open a socket
even if a transitive dependency tried to. This is verifiable in the built APK
and in Android's own app info screen — it is enforcement, not a promise, which
is the only kind of privacy claim worth making.

`ACCESS_NETWORK_STATE`, which Media3 merges in for streaming code paths FILISH
never reaches, is explicitly removed in the manifest. Without `INTERNET` it
could not transmit anything regardless, but the claim on the access screen is
only checkable if the permission list is genuinely minimal.

The verified permission set is exactly:

```
READ_EXTERNAL_STORAGE   (maxSdk 32)
WRITE_EXTERNAL_STORAGE  (maxSdk 29)
READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_AUDIO
MANAGE_EXTERNAL_STORAGE
```

---

## 4. The filesystem is not static

Assumed hostile throughout. Files vanish between being listed and being read;
directories become unreadable; removable storage ejects mid-operation.

- Listings report entries they could not `stat` rather than silently omitting
  them — a total that quietly under-reports is worse than no total.
- Operations accumulate per-path errors and continue; ten items requested with
  eight succeeding beats none, provided the user is told which two remain and
  why.
- A file that disappears before it can be copied is recorded as
  "It disappeared before it could be copied", not as a crash.
- Symlinks are counted but never followed. A link to an ancestor is how a
  recursive size calculation becomes an infinite loop, and such links exist in
  the wild.

---

## 5. Moves across volumes

A move within one volume is a single `rename()` — atomic, instant, and
impossible to leave half-done. Across volumes it degrades to copy-then-delete,
which is slow and interruptible.

FILISH detects which it is and tells the truth about it: moving 40 GB inside
internal storage shows no transfer progress, because nothing is being copied.
Cross-volume moves delete each source only after its copy is verified at the
expected size, so an interruption costs time and never data.

`rename()` can also fail across mount points that look like one volume
(emulated storage on some devices). FILISH falls back to a real copy rather
than reporting a failure the user cannot act on.

---

## 6. Media and document formats

**Video and audio** decode through the platform's decoders via Media3. Codec
support varies by device. A playback failure distinguishes *a decoder this
device lacks* (the file is fine; another player may handle it) from *a damaged
or truncated file* (it will not play anywhere), because those imply different
next steps.

**PDF** renders through Android's own `PdfRenderer`. No dependency, no
network. `PdfRenderer` cannot open password-protected documents and FILISH
says so specifically rather than reporting a generic failure. It also permits
only one open page at a time across the whole renderer, so page rendering is
serialised behind a mutex — concurrent renders are not slow, they are
incorrect.

**Office formats (.docx, .xlsx, .pptx) are deliberately not claimed.**
Rendering them faithfully means embedding an office engine — tens of megabytes,
and a rendering subtly wrong in ways that matter when someone is reading a
contract. FILISH classifies them, shows their metadata, and hands them to an
app built for them.

**RAW images** are classified by extension, and the classification is not
claimed to be perfect. Vendors reuse extensions across unrelated formats; only
formats with unambiguous extensions are listed. Where in doubt it is better to
call a RAW file an image than an image a RAW file, because the RAW bucket is
the one the user acts on.

---

## 7. Known limitations in this build

Stated rather than hidden.

- **Operations do not survive process death.** Copy and move run in an
  application-scoped coroutine that outlives navigation, so browsing during a
  transfer is fine — but if Android kills the process, an in-flight transfer
  stops. A foreground service would fix this and is the first item on the
  roadmap.
- **Audio does not play in the background.** Playback is tied to the viewer
  activity. Background playback needs the same foreground service.
- **Archives are not implemented.** No compress, no extract, no inspection.
  The file *kinds* are classified and the glyphs exist; the operations do not.
  Nothing in the interface offers them, so there is no dead end — but it is a
  real gap against a full file manager.
- **Folder comparison and operation history/undo are not implemented.**
- **Thumbnail generation for RAW** depends on the platform extractors and will
  fail for some vendor formats; FILISH shows a damaged-file state rather than
  a blank.
