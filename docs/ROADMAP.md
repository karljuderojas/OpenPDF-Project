# Roadmap

Goal: the features people use in PDFGear and Xodo, free, offline and open source. Priorities are viewing, annotating, signing and editing. Anything that needs a server (cloud AI, Office conversion, cloud storage, collecting signatures from others) is out.

✅ done in the scaffold · 🧱 code exists, no UI yet · ⬜ not started

## Viewing
- ✅ Open from the file picker and from other apps (file managers, email, browsers)
- ✅ Continuous vertical scroll with PDFium rendering and a bitmap cache
- ✅ Pinch to zoom (basic: scales the whole list)
- ⬜ Proper zoom and pan with sharp re-rendering at high zoom (tiled rendering)
- ⬜ Text search, outline/bookmarks, go to page, page thumbnails
- ⬜ Text selection with a Highlight / Underline / Note popup
- ⬜ Files tab: open documents on top, recent history grouped by day, "Open file" through Android's file picker (stored on the device only)
- ⬜ Switcher between open documents, tab style
- ⬜ Night mode, reading settings
- ⬜ Password-protected PDFs

## Annotating (Annotate mode)
- ✅ Highlight, underline, strikeout by dragging over the text (an area, until text selection lands)
- ✅ Pen, rectangles, sticky notes, eraser (tap a mark to remove it)
- ✅ Sticky tools: one finger draws while a tool is chosen; choose it again to scroll
- ✅ Undo (shared with page edits)
- ⬜ Text box (FreeText), stamps, ellipses
- ⬜ Per-tool colours and sizes, redo
- ⬜ Comments list

## Signing (Sign mode), see [signing-design.md](signing-design.md)
- 🧱 Place a signature image (`SignatureStamper`)
- 🧱 Digital signature with an on-device or imported certificate (`DigitalSigner`, `SigningIdentity`)
- 🧱 Audit trail, SHA-256 fingerprints and audit page
- ⬜ Signature pad: draw, type, photo; saved signatures and initials
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
- ⬜ Share: send the current PDF (or the signed copy) to any app through Android's share sheet
- ⬜ Print
- ⬜ Password protect / remove password
- ⬜ Document info

## Not planned for now
Dropped from scope on 2026-10-02 to keep the app focused: OCR, compression, and converting PDFs to Word or other Office formats.
