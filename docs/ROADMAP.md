# Roadmap

Goal: the features people use in PDFGear and Xodo, free, offline and open source. Priorities are viewing, annotating, signing and editing. Anything that needs a server (cloud AI, Office conversion, cloud storage, collecting signatures from others) is out.

✅ done in the scaffold · 🧱 code exists, no UI yet · ⬜ not started

The app shows no button for ⬜ items: each tool joins its mode's strip (`ViewerMode`) when it ships, and Edit mode stays off the bar until its first tool does.

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
- ⬜ Text box (FreeText), stamps, ellipses
- ✅ Per-tool colours and sizes (remembered per tool), redo
- ⬜ Comments list

## Signing (Sign mode), see [signing-design.md](signing-design.md)
- ✅ Signature and initials: draw once on a pad (black or blue ink; too-simple drawings are refused), saved encrypted on the device, then tap the line to place
- ✅ Date, text and checkmark: tap where they go
- ⬜ Move and resize a placed signature (for now: Undo and tap again)
- 🧱 Digital signature with an on-device or imported certificate (`DigitalSigner`, `SigningIdentity`)
- 🧱 Audit trail, SHA-256 fingerprints and audit page
- ✅ Type a signature or initials instead of drawing them, in one of two bundled script fonts
- ⬜ Photograph a signature on paper
- ⬜ Field detection and the guided next-field walk
- ⬜ Fill AcroForm fields
- ⬜ Verify banner for signed PDFs
- ⬜ RFC 3161 timestamps, LTV

## Editing (Edit and Pages modes)
- ✅ Pages mode: thumbnail grid; rotate, move, insert blank, delete and merge the selected page
- ✅ Edits go to a private working copy with undo; Save writes back, or asks where to save when the file is read-only; leaving with unsaved changes asks first
- 🧱 Add text (`PageEditor.addText`)
- ⬜ Drag to reorder in the page grid, multi-select
- ⬜ Extract and split
- ⬜ Add image, redact (true removal, not a black box), links
- ⬜ Edit existing text (hard; needs font handling)

## More
- ✅ Share: send the current PDF, unsaved changes included, to any app through Android's share sheet; also from a Recent row's menu
- ✅ Print: Android's print dialog, with any printer the phone knows and Save as PDF; prints unsaved changes too, and respects PDFs that turn printing off
- ⬜ Password protect / remove password
- ✅ Document info: title, author, dates, the app that made it, page count and size, file size, PDF version, and whether it is password protected, has form fields or is digitally signed

## Not planned for now
Dropped from scope on 2026-10-02 to keep the app focused: OCR, compression, and converting PDFs to Word or other Office formats.
