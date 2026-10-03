# Signing design

How FreePDF makes signing easy for the signer and makes the signed file hold up as an official record.

DocuSeal (github.com/docusealco/docuseal) is the reference for the flow. DocuSeal is AGPL-3.0 and FreePDF is Apache-2.0, so **we borrow ideas and user flows only. No DocuSeal code, assets, text or exact visuals are copied into this repository.** Anyone contributing to signing should follow the same rule. Read DocuSeal's public docs rather than its source when possible.

Everything below runs on the phone. There is no FreePDF server, so collecting signatures from other people by email is out of scope for now (see "Later").

## Goals

1. Someone who has never used the app can open a PDF from an email and sign it in under a minute.
2. A signed file shows anyone who opens it who signed, when, and whether it changed afterwards.
3. Visual signing never forces the user to learn about certificates; certificate signing is one extra choice at the end.

## Signer flow

Matches the Sign mode in the UX blueprint (Read → Sign → Finish).

1. **Enter Sign mode.** From the viewer's mode bar, or the Home "Sign a PDF" shortcut. The signature sheet opens on its own.
2. **Create a signature once.** Three ways, as in DocuSeal:
   - **Draw** on a pad with a baseline. Reject marks that are too small or too simple and ask to redraw (DocuSeal does the same check). Black or blue ink. Landscape gives a wider pad.
   - **Type** a name, rendered in a bundled script font (an OFL-licensed font such as Dancing Script).
   - **Photo/upload** a signature on paper, with the background made transparent.
   - Initials are a separate saved item with the same three options.
   - "Save for next time" is on by default. Saved signatures stay on the device, encrypted with a key from the Android Keystore.
3. **Find the places to sign.** First use real AcroForm signature and text fields in the PDF. If there are none, search the page text for "Signature", "Sign here", "Initial", "Date" and long underlines, and suggest those spots. If nothing is found, show "Tap where you want to sign."
4. **Guided walk through the fields.** Like DocuSeal's step-by-step form: a banner shows "Place 1 of 3", Next scrolls to the next empty field, and tapping a field on the page jumps to it. Required fields that are still empty have an amber dashed outline. The walk can be collapsed so the signer can read the whole document.
5. **Fill each field.** Signature and initials drop in sized to fit, with handles to move and resize. Date fills today's date in the device format. Name fills from the saved profile. Text and checkmark fields are tapped and filled. Interactive form fields are filled from the same mode.
6. **Finish.** If anything required is empty, Finish offers to go there first. Otherwise one sheet with one decision:
   - **Lock signatures (default):** flattened into the page content.
   - **Keep editable:** signatures stay as annotations that can be moved later.
   - **Add a digital certificate:** everything in "Making it official" below.
   - Consent line under the button, as DocuSeal does: "By finishing, you agree to sign this document electronically." It links to a short disclosure in the app.
   - Saves as "Name (signed).pdf" next to the original, so the original stays untouched.

## Making it official

A drawn signature alone is easy to fake and easy to dispute. DocuSeal makes its results defensible with an audit trail, document fingerprints and a cryptographic signature. FreePDF does the same, on-device.

### 1. Audit trail
Code: `pdf/sign/AuditTrail.kt`.

Recorded while signing:
- Document name, a random document ID, and the **SHA-256 of the original file** (`DocumentHash`).
- Events with timestamps: opened, each field filled, signed, completed.
- For each signer: name, optional email, time signed, method (drawn, typed or uploaded), optional reason, the device model and Android version, and the exact consent text shown.

DocuSeal also records IP address, email verification and SMS codes. Those need a server, so FreePDF leaves them out. That's a known limit of an offline app, and the audit page says so plainly instead of implying more than it proves.

### 2. Audit page ("Signing certificate")
Code: `pdf/sign/AuditPageWriter.kt`.

Appended as the last page(s) of the signed copy, before the digital signature is applied, so the signature covers it. It lists the document ID, the original's SHA-256, every signer and the event log. An image of each signature is planned. It's optional in Settings, on by default when a certificate is used.

DocuSeal can also stamp a "Document ID" footer on every page. That's a cheap later addition.

### 3. Digital signature (PAdES)
Code: `pdf/sign/DigitalSigner.kt`, `pdf/sign/SigningIdentity.kt`.

- A standard detached PKCS#7/CMS signature over the PDF byte range (`adbe.pkcs7.detached`, moving to `ETSI.CAdES.detached` for PAdES-B). Acrobat, Foxit, Xodo and browsers show it in their signature panels.
- It's saved as an **incremental update**, so a document can carry several signatures and earlier ones stay valid. This is DocuSeal's "one signature per signer" mode. The default is one signature at Finish.
- **Any change after signing breaks the signature.** That's what makes the file tamper-evident.

Where the key comes from:
- **Device certificate (default):** an RSA-2048 key generated inside the Android Keystore, so it never leaves the phone. It comes with a self-signed certificate in the signer's name. It proves the file hasn't changed since signing, but other apps show the signer as "validity unknown", because no authority vouches for the name. The setup screen says this in one sentence.
- **Imported certificate:** a .p12/.pfx file from a certificate authority, an employer or a national eID provider (`SigningIdentity.fromPkcs12`). One that chains to the Adobe Approved Trust List shows as trusted in Acrobat. The file is imported into the Keystore and the original is not kept.

DocuSeal's self-hosted install creates its own CA chain. That isn't trusted by Adobe either, so this is the same trade-off.

### 4. Trusted timestamp (RFC 3161)
Code: `pdf/sign/TimestampClient.kt`. DocuSeal lets admins set a timestamp server URL with a fallback. FreePDF does the same:
- A switch under Sign → Certificate, off by default because it is the only part of signing that uses the network. It asks freetsa.org, then DigiCert's free public server.
- When it's set, the CMS signature gets an unsigned timestamp attribute. This proves *when* the file was signed, independent of the phone's clock.
- It needs a network call at signing time and is skipped with a notice when offline.
- More space must be reserved in the signature placeholder, as DocuSeal does.

### 5. Long-term validation (LTV), later
Embed the certificate chain and revocation data (a DSS dictionary) so signatures can still be checked after certificates expire. Open-source DocuSeal leaves this as a stub. It's low priority until imported CA certificates are common among our users.

### 6. Verify
Code: `pdf/sign/SignatureVerifier.kt`, `ui/sign/SignatureBanner.kt`.

When a signed PDF is opened, show a slim banner: "Signed by K. Rojas · not changed since signing", or "Changed after signing". Tapping it lists each signature with:
- whether the byte-range digest matches;
- whether the signature covers the whole file, or unsigned changes were added after it (DocuSeal's verifier flags this);
- the certificate chain and whether it is trusted. "Trusted" means it chains to a root in the phone's system store; Adobe's trust list is not available offline, so a certificate Acrobat trusts may show as "not recognised" here, and the app says so;
- the signing time and any timestamp.

## Legal footing (not legal advice)

The ESIGN Act and UETA in the US, and eIDAS in the EU, recognise electronic signatures. They rely on intent to sign, consent to sign electronically, a record linked to the signer, and integrity of that record. The consent line, the audit trail and the digital signature map onto those points. FreePDF should describe its output as an "electronic signature" (or an "advanced" one with a CA-issued certificate). It must not claim "qualified" (QES), which needs a qualified trust service provider and hardware.

## Build order

1. Visual signing: capture a signature, place it, flatten it (`SignatureStamper`). **First release.**
2. Guided field walk using AcroForm fields, then text-based field detection.
3. Audit trail and audit page.
4. Device-certificate digital signature and the Verify banner. **Done.**
5. Importing a .p12 certificate. **Done.**
6. RFC 3161 timestamps (**done**), then LTV.

## Later: collecting signatures from others

DocuSeal's core feature is sending a document to several people in order. FreePDF has no server, but a serverless version is possible: "Prepare for signing" places empty fields assigned to roles and shares the PDF through any app. Each signer adds their signature incrementally, and the audit page grows with each one.
