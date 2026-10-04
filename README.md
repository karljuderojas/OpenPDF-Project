# FreePDF

A free PDF app for Android. Read, mark up, sign and edit PDFs on your phone. There are no ads, no accounts, no subscriptions and nothing locked behind a payment. Everything happens on your phone: your documents never leave it.

## Download

**[Get the latest FreePDF APK](https://github.com/karljuderojas/OpenPDF-Project/releases/latest)**

On that page, tap the file ending in `.apk` under **Assets** to download it.

### Install on your phone

1. Open the download page above in your phone's browser and tap the `.apk` file.
2. Android will ask whether you allow installs from this source (your browser or Files app). Tap **Settings**, switch on **Allow from this source**, then go back.
3. Tap **Install**. If Play Protect shows a warning, choose **Install anyway**: FreePDF is not on the Play Store, so Android has not seen it before.
4. Open FreePDF. To update later, install the newer file the same way; your documents stay where they are.

You need Android 8.0 or newer.

## What you can do

**Read**
- Open PDFs from the app, or from your email, browser or file manager.
- Scroll through pages, pinch to zoom, and the text stays sharp.
- Search for words, jump to a page, and use a document's table of contents.
- Switch to night or sepia page colours when reading in the dark.
- Open password-protected PDFs.
- Print, or have the app read the text aloud.
- Keep several documents open and flip between them.

**Mark up**
- Highlight, underline and strike through text by dragging over it.
- Draw with a pen, add shapes, arrows and sticky notes, and rub out mistakes.
- Add text boxes and ready-made stamps such as Approved or Draft.
- Change a mark's colour, size or comment later, and see all comments in one list.
- Undo and redo.

**Sign**
- Draw your signature, type it, or use a photo of one. Place it where you want it, then move or resize it.
- The app finds "Sign here" lines for you and takes you from one to the next.
- Fill in forms, add the date, or tick a box.
- Add a proper digital signature that other PDF programs can check.
- Your saved signature is stored encrypted on your phone.

**Edit**
- Change the words of existing text, add text and pictures, and move or delete them.
- Reorder, rotate, add, delete and merge pages.
- Add a watermark, trim page margins, and add links.
- Black out private information so it is truly removed from the file, not just covered.
- Lock a PDF with a password, or take restrictions off a file you own.

**Share and organise**
- Send a PDF by any app on your phone, as a locked copy, as some pages only, or as images.
- Split a PDF or save chosen pages as a new file.
- Scan paper with the camera, or turn photos into a PDF.
- A Files tab shows recent documents, with rename, delete and folder browsing.
- Document info shows the title, author, size and whether the file is signed or protected.

## Your privacy

FreePDF works without the internet. The only time it goes online is if you choose to add a trusted time stamp to a digital signature. It has no ads, no tracking and no account. Your documents and signatures stay on your phone.

## Help and ideas

Found a problem or have a suggestion? [Open an issue](https://github.com/karljuderojas/OpenPDF-Project/issues). The list of what works and what is planned is in [docs/ROADMAP.md](docs/ROADMAP.md).

## Licence

Free and open source under the Apache-2.0 licence. See [LICENSE](LICENSE).

---

## For developers

### Tech stack

| Part | Choice | Licence |
| --- | --- | --- |
| Language and UI | Kotlin, Jetpack Compose, Material 3 | Apache-2.0 |
| Rendering | [PDFium](https://pdfium.googlesource.com/pdfium/) via [PdfiumAndroidKt](https://github.com/johngray1965/PdfiumAndroidKt) | BSD / Apache-2.0 |
| Editing, annotating, signing | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (with BouncyCastle for signatures) | Apache-2.0 / MIT |
| Typed signature fonts | [Dancing Script](https://github.com/googlefonts/DancingScript) and [Caveat](https://github.com/googlefonts/caveat), in `app/src/main/res/font` | SIL OFL 1.1, see [docs/licenses](docs/licenses) |

MuPDF was ruled out because its AGPL licence would force the whole app to be AGPL.

### How the code is laid out

```
app/src/main/java/io/github/karljuderojas/freepdf/
├── pdf/                 PDF engine layer, no UI
│   ├── render/          PdfRenderer: PDFium page rendering (read-only, fast)
│   ├── edit/            PageEditor, PdfDocuments: PdfBox load/save and page edits
│   ├── annotate/        Annotator: standard PDF annotations with appearance streams
│   └── sign/            SignatureStamper, DigitalSigner, SigningIdentity, AuditTrail, AuditPageWriter
└── ui/                  Compose screens
    ├── home/            Home: open a PDF
    └── viewer/          Viewer: page list, mode bar (Annotate, Sign, Edit, Pages, More)
```

PDFium draws every page on screen. Every change goes through PdfBox, which saves the file, and the viewer then reopens it with PDFium. Annotations are standard PDF annotations, so they show up in other readers too.

### Signing

Signing places a drawn, typed or photographed signature on the page and can add a standard digital signature that other PDF readers verify. See [docs/signing-design.md](docs/signing-design.md).

### Building

Requires JDK 21 (Robolectric screenshot tests need it) and the Android SDK (API 37).

```sh
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
```

Or open the project in Android Studio. CI builds a debug APK on every push and pull request.

### Releasing

Run the **Release** workflow from the Actions tab and type a version number. It builds the APK, tags the commit and publishes a GitHub Release. To sign with the project's own key, create a keystore with `keytool -genkeypair -v -keystore freepdf.keystore -alias freepdf -keyalg RSA -keysize 4096 -validity 10000`, never commit it, and add these repository secrets: `FREEPDF_KEYSTORE_BASE64` (the file run through `base64 -w0`), `FREEPDF_KEYSTORE_PASSWORD`, `FREEPDF_KEY_ALIAS` and `FREEPDF_KEY_PASSWORD`. Without them the release is signed with a test key.
