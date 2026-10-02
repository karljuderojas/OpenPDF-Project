# Roadmap

Goal: the features people use in PDFGear and Xodo, free, offline and open source. Priorities are viewing, annotating, signing and editing. Converting comes later.

✅ done in the scaffold · 🧱 code exists, no UI yet · ⬜ not started

## Viewing
- ✅ Open from the file picker and from other apps (file managers, email, browsers)
- ✅ Continuous vertical scroll with PDFium rendering and a bitmap cache
- ✅ Pinch to zoom (basic: scales the whole list)
- ⬜ Proper zoom and pan with sharp re-rendering at high zoom (tiled rendering)
- ⬜ Text search, outline/bookmarks, go to page, page thumbnails
- ⬜ Text selection with a Highlight / Underline / Note popup
- ⬜ Recent files on Home, night mode, reading settings
- ⬜ Password-protected PDFs

## Annotating (Annotate mode)
- 🧱 Highlight, underline, strikeout (`Annotator.markText`)
- 🧱 Pen / ink (`Annotator.ink`)
- 🧱 Sticky notes (`Annotator.note`)
- 🧱 Rectangles and ellipses (`Annotator.shape`)
- ⬜ Text box (FreeText), stamps, eraser
- ⬜ Tool strip with sticky tools, per-tool colours and sizes, undo/redo
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
- 🧱 Rotate, delete, move, insert blank pages, merge (`PageEditor`)
- 🧱 Add text (`PageEditor.addText`)
- ⬜ Page thumbnail grid with drag to reorder
- ⬜ Extract and split
- ⬜ Add image, redact (true removal, not a black box), links
- ⬜ Edit existing text (hard; needs font handling)

## More
- ⬜ Password protect / remove password
- ⬜ Compress
- ⬜ OCR (on-device, e.g. Tesseract or ML Kit)
- ⬜ Scan to PDF (camera)
- ⬜ Print and share
- ⬜ Convert: images to PDF first, then PDF to images, and Office later
