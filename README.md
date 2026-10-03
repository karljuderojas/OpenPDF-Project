# FreePDF

A free, open-source PDF app for Android. It aims to cover what people use PDFGear and Xodo for (viewing, annotating, signing and editing PDFs) with no ads, accounts or paywalls, and everything running on the phone.

> Early scaffold. See [docs/ROADMAP.md](docs/ROADMAP.md) for what works today.

## Tech stack

| Part | Choice | Licence |
| --- | --- | --- |
| Language and UI | Kotlin, Jetpack Compose, Material 3 | Apache-2.0 |
| Rendering | [PDFium](https://pdfium.googlesource.com/pdfium/) via [PdfiumAndroidKt](https://github.com/johngray1965/PdfiumAndroidKt) | BSD / Apache-2.0 |
| Editing, annotating, signing | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (with BouncyCastle for signatures) | Apache-2.0 / MIT |
| Typed signature fonts | [Dancing Script](https://github.com/googlefonts/DancingScript) and [Caveat](https://github.com/googlefonts/caveat), in `app/src/main/res/font` | SIL OFL 1.1, see [docs/licenses](docs/licenses) |

MuPDF was ruled out because its AGPL licence would force the whole app to be AGPL.

## How the code is laid out

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

## Signing

Signing follows the flow of [DocuSeal](https://github.com/docusealco/docuseal), used as a design reference only: no DocuSeal code is used, since it is AGPL. See [docs/signing-design.md](docs/signing-design.md).

## Building

Requires JDK 21 (Robolectric screenshot tests need it) and the Android SDK (API 37).

```sh
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
```

Or open the project in Android Studio. CI builds a debug APK on every push and pull request.

## Licence

Apache-2.0. See [LICENSE](LICENSE).
