# Roadmap

Goal: the features people use in PDFGear and Xodo, free, offline and open source. Priorities are viewing, annotating, signing and editing. Anything that needs a server (cloud AI, Office conversion, cloud storage, collecting signatures from others) is out.

✅ done in the scaffold · 🧱 code exists, no UI yet · ⬜ not started

The app shows no button for ⬜ items: each tool joins its mode's strip (`ViewerMode`) when it ships, and a mode with no working tools stays off the bar.

## Viewing
- ✅ Open from the file picker and from other apps (file managers, email, browsers)
- ✅ Continuous vertical scroll with PDFium rendering and a bitmap cache
- ✅ Pinch to zoom (basic: scales the whole list)
- ✅ Sharp re-rendering when zoomed: the visible part of each page is rendered at the zoom level
- ✅ Text search with highlighted matches and previous/next, the outline (Contents), go to page (tap "Page 3 of 12")
- ⬜ Page thumbnails while reading
- ✅ Text selection: press and hold, drag, then Highlight / Underline / Strike / Note / Copy from a popup
- ✅ Files tab: documents open this session on top, recent history grouped by Today / Yesterday / Earlier, "Open file" through Android's file picker (stored on the device only)
- ⬜ Switcher between open documents, tab style
- ⬜ Night mode, reading settings
- ✅ Password-protected PDFs: asks for the password, and edits and saves keep the file locked with it

## Annotating (Annotate mode)
- ✅ Highlight, underline, strikeout by dragging over the text, snapping to whole words (an area on scans)
- ✅ Pen, rectangles, sticky notes, eraser (tap a mark to remove it)
- ✅ Sticky tools: one finger draws while a tool is chosen; choose it again to scroll
- ✅ Undo (shared with page edits)
- ✅ Text box (FreeText): colour and font size, editable later; Latin, Greek and Cyrillic text
- ✅ Stamps: Approved, Not approved, Draft, Final, Confidential, For comment, Void
- ⬜ Ellipses
- ✅ Per-tool colours and sizes (remembered per tool), redo
- ✅ Tap a mark to change its colour or size, comment on it or delete it; Comments list of every mark

## Signing (Sign mode), see [signing-design.md](signing-design.md)
- ✅ Signature and initials: draw once on a pad (black or blue ink; too-simple drawings are refused), saved encrypted on the device, then tap the line to place
- ✅ Date, text and checkmark: tap where they go
- ⬜ Move and resize a placed signature (for now: Undo and tap again)
- 🧱 Digital signature with an on-device or imported certificate (`DigitalSigner`, `SigningIdentity`)
- 🧱 Audit trail, SHA-256 fingerprints and audit page
- ✅ Type a signature or initials instead of drawing them, in one of two bundled script fonts
- ⬜ Photograph a signature on paper
- ✅ Finish: lock signatures into the page (with the optional seal) or keep them editable as stamp annotations; Save or Save & share
- ✅ Places to sign: empty signature fields and "Signature" or "Sign here" labels are found and outlined, with a "2 places to sign" banner and Next field; tap one to sign it, then move or resize the signature like any other
- ✅ Fill form: tap a highlighted field of the PDF's own form to type into it, tick it or pick from its list; values are saved in the form, so other apps see them
- ⬜ Verify banner for signed PDFs
- ⬜ RFC 3161 timestamps, LTV

## Editing (Edit and Pages modes)
- ✅ Pages mode: thumbnail grid; rotate, move, insert blank, delete and merge the selected page
- ✅ Edits go to a private working copy with undo; Save writes back, or asks where to save when the file is read-only; leaving with unsaved changes asks first
- ✅ Edit mode: Add text and Add image (photo picker), moved, resized or deleted on the page, then written into the PDF on Done; photos are stored as JPEG, transparent pictures losslessly
- ⬜ Drag to reorder in the page grid, multi-select
- ✅ Extract and split: save chosen pages (typed like 1-3, 5) as a new PDF, or split into two after the selected page or every few pages into a folder you pick; the open PDF stays as it is
- ⬜ Redact (true removal, not a black box), links
- ⬜ Edit existing text (hard; needs font handling)

## More
- ✅ Share: a sheet (top bar or More) offers the PDF with your changes, a locked copy with marks and form fields flattened into the page, some pages only, or pages as images, then Android's share sheet; a Recent row's menu sends the file as it is
- ✅ Print: Android's print dialog, with any printer the phone knows and Save as PDF; prints unsaved changes too, and respects PDFs that turn printing off
- ✅ Password: add a password (AES-256) to a PDF, or change or remove the one it has; undo puts the old one back, and Save keeps the change
- ✅ Document info: title, author, dates, the app that made it, page count and size, file size, PDF version, and whether it is password protected, has form fields or is digitally signed

## Not planned for now
Dropped from scope on 2026-10-02 to keep the app focused: OCR, compression, and converting PDFs to Word or other Office formats.
