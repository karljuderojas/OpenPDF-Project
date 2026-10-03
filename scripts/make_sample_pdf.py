#!/usr/bin/env python3
"""Writes the sample PDFs used by the tests, then renders their pages to PNG.

agreement.pdf is a plain two-page contract. form.pdf is a one-page sign-up form with fillable
fields (text, checkboxes, radio buttons, a dropdown) for the Fill form tool.

Usage: scripts/make_sample_pdf.py   (needs pdftoppm from poppler-utils)
The PDF is written by hand with only the standard Helvetica fonts, so no Python packages are needed.
"""
import pathlib
import subprocess
import textwrap

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/test/resources/sample"
W, H = 612, 792  # US Letter, in points

PARAGRAPHS = [
    ("1. Services", "Northwind Studio (the Provider) will design and deliver a new website for "
     "Lakeside Bakery (the Client), including five page templates, a menu editor and an online "
     "ordering form, as described in Schedule A."),
    ("2. Timeline", "Work begins on the start date below. The Provider will share a first draft "
     "within three weeks and the finished site within eight weeks, unless both parties agree in "
     "writing to change these dates."),
    ("3. Fees and payment", "The total fee is $6,400. The Client pays 40% when signing, 30% on "
     "approval of the first draft and 30% on launch. Invoices are due within 14 days."),
    ("4. Changes", "Requests outside Schedule A are quoted separately and start only after the "
     "Client approves the quote by email."),
    ("5. Ownership", "Once paid in full, the Client owns the finished site and its content. The "
     "Provider may show the work in its portfolio."),
    ("6. Confidentiality", "Each party keeps the other's non-public information private and uses "
     "it only for this project."),
    ("7. Termination", "Either party may end this agreement with 14 days' written notice. The "
     "Client pays for work completed up to that date."),
]


def esc(s):
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def text(x, y, size, s, font="F1"):
    return f"BT /{font} {size} Tf {x} {y} Td ({esc(s)}) Tj ET\n"


def page_one():
    ops = "0.12 0.29 0.53 rg\n" + text(72, 712, 22, "Service Agreement", "F2") + "0 g\n"
    ops += text(72, 690, 10, "Agreement no. 2026-114  |  Effective October 1, 2026")
    ops += "0.8 G 1 w 72 676 m 540 676 l S\n"
    y = 650
    for heading, body in PARAGRAPHS[:5]:
        ops += text(72, y, 12, heading, "F2")
        y -= 17
        for line in textwrap.wrap(body, 92):
            ops += text(72, y, 10.5, line)
            y -= 14
        y -= 12
    ops += text(72, 48, 9, "Page 1 of 2")
    return ops


def page_two():
    ops = ""
    y = 720
    for heading, body in PARAGRAPHS[5:]:
        ops += text(72, y, 12, heading, "F2")
        y -= 17
        for line in textwrap.wrap(body, 92):
            ops += text(72, y, 10.5, line)
            y -= 14
        y -= 12
    ops += text(72, y - 10, 12, "Signatures", "F2")
    y -= 70
    for party, name in (("Provider", "Northwind Studio"), ("Client", "Lakeside Bakery")):
        ops += "0 G 0.75 w " + f"72 {y} m 300 {y} l S 340 {y} m 540 {y} l S\n"
        ops += text(72, y - 14, 9.5, f"{party} signature  ({name})")
        ops += text(340, y - 14, 9.5, "Date")
        y -= 80
    ops += text(72, 48, 9, "Page 2 of 2")
    return ops


def build_pdf(contents):
    objects = []

    def add(body):
        objects.append(body)
        return len(objects)

    font1 = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    font2 = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")
    pages_id = len(objects) + 2 * len(contents) + 1
    kids = []
    for ops in contents:
        data = ops.encode("latin-1")
        stream = add(f"<< /Length {len(data)} >>\nstream\n{ops}endstream")
        kids.append(add(
            f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {W} {H}] /Contents {stream} 0 R "
            f"/Resources << /Font << /F1 {font1} 0 R /F2 {font2} 0 R >> >> >>"))
    assert add(f"<< /Type /Pages /Kids [{' '.join(f'{k} 0 R' for k in kids)}] /Count {len(kids)} >>") == pages_id
    catalog = add(f"<< /Type /Catalog /Pages {pages_id} 0 R >>")

    return serialize(objects, root=catalog)


FORM_TEXT = [
    # (name, label, x, y, width, height, multiline)
    ("name", "Full name", 72, 640, 468, 22, False),
    ("email", "Email", 72, 590, 260, 22, False),
    ("phone", "Phone", 350, 590, 190, 22, False),
    ("comments", "Anything we should know?", 72, 290, 468, 70, True),
]
DAYS = [("saturday", "Saturday", 72), ("sunday", "Sunday", 200)]
SIZES = [("S", 72), ("M", 140), ("L", 208), ("XL", 276)]
TEAMS = ["Kitchen", "Front of house", "Delivery"]


def form_pdf():
    """A one-page AcroForm, written by hand like the agreement. Every field starts empty."""
    objects = {}

    def put(num, body):
        objects[num] = body

    def stream(num, ops, extra=""):
        data = ops.encode("latin-1")
        put(num, f"<< /Length {len(data)} {extra}>>\nstream\n{ops}endstream")

    # 1 catalog, 2 pages, 3 page, 4 contents, 5 and 6 fonts, 7 AcroForm; fields from 10 on.
    ops = "0.12 0.29 0.53 rg\n" + text(72, 712, 22, "Volunteer Sign-up", "F2") + "0 g\n"
    ops += text(72, 690, 10, "Lakeside Bakery community day  |  Saturday 17 and Sunday 18 October 2026")
    ops += "0.8 G 1 w 72 676 m 540 676 l S\n0.55 G 0.75 w\n"
    for _, label, x, y, w, h, _ in FORM_TEXT:
        ops += text(x, y + h + 5, 10, label, "F2") + f"{x} {y} {w} {h} re S\n"
    ops += text(72, 545, 10, "Days you can help", "F2")
    for _, label, x in DAYS:
        ops += f"{x} 520 14 14 re S\n" + text(x + 20, 523, 10.5, label)
    ops += text(72, 490, 10, "T-shirt size", "F2")
    for label, x in SIZES:
        ops += f"{x} 465 14 14 re S\n" + text(x + 20, 468, 10.5, label)
    ops += text(72, 435, 10, "Team", "F2") + "72 400 230 22 re S 0.55 g 286 414 m 296 414 l 291 408 l f 0 g\n"
    ops += text(72, 190, 10, "Signature", "F2") + "0 G 72 170 m 300 170 l S 340 170 m 540 170 l S\n"
    ops += text(340, 158, 9.5, "Date")
    ops += text(72, 48, 9, "Page 1 of 1")
    stream(4, ops)
    put(5, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    put(6, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")

    da = "/DA (/Helv 11 Tf 0 g)"
    widget = "/Type /Annot /Subtype /Widget /F 4 /P 3 0 R"
    fields, annots = [], []
    n = 10
    for name, label, x, y, w, h, multiline in FORM_TEXT:
        ff = " /Ff 4096" if multiline else ""  # Multiline (bit 13)
        put(n, f"<< {widget} /FT /Tx /T ({name}) /TU ({esc(label)}) /Rect [{x} {y} {x + w} {y + h}] {da}{ff} >>")
        fields.append(n)
        annots.append(n)
        n += 1
    check = "0 g 1.6 w 1 J 1 j 3 7 m 6 3.5 l 11.5 11 l S\n"
    dot = "0 g 7 3 m 9.2 3 11 4.8 11 7 c 11 9.2 9.2 11 7 11 c 4.8 11 3 9.2 3 7 c 3 4.8 4.8 3 7 3 c f\n"
    bbox = "/Type /XObject /Subtype /Form /BBox [0 0 14 14] /Resources << >> "
    for name, label, x in DAYS:
        stream(n + 1, check, bbox)
        stream(n + 2, "", bbox)
        put(n, f"<< {widget} /FT /Btn /T ({name}) /TU ({label}) /V /Off /AS /Off /Rect [{x} 520 {x + 14} 534] "
               f"/AP << /N << /Yes {n + 1} 0 R /Off {n + 2} 0 R >> >> /MK << >> >>")
        fields.append(n)
        annots.append(n)
        n += 3
    group = n
    n += 1
    kids = []
    for label, x in SIZES:
        stream(n + 1, dot, bbox)
        stream(n + 2, "", bbox)
        put(n, f"<< {widget} /Parent {group} 0 R /AS /Off /Rect [{x} 465 {x + 14} 479] "
               f"/AP << /N << /{label} {n + 1} 0 R /Off {n + 2} 0 R >> >> /MK << >> >>")
        kids.append(n)
        annots.append(n)
        n += 3
    # Radio (bit 16) and NoToggleToOff (bit 15).
    put(group, f"<< /FT /Btn /Ff 49152 /T (size) /TU (T-shirt size) /V /Off /Kids [{' '.join(f'{k} 0 R' for k in kids)}] >>")
    fields.append(group)
    # Combo (bit 18).
    opts = " ".join(f"({esc(t)})" for t in TEAMS)
    put(n, f"<< {widget} /FT /Ch /Ff 131072 /T (team) /TU (Team) /Opt [{opts}] /Rect [72 400 302 422] {da} >>")
    fields.append(n)
    annots.append(n)
    n += 1
    put(n, f"<< {widget} /FT /Tx /T (date) /TU (Date) /Rect [340 172 540 192] {da} >>")
    fields.append(n)
    annots.append(n)
    n += 1

    put(1, "<< /Type /Catalog /Pages 2 0 R /AcroForm 7 0 R >>")
    put(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    put(3, f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {W} {H}] /Contents 4 0 R "
           f"/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Annots [{' '.join(f'{a} 0 R' for a in annots)}] >>")
    put(7, f"<< /Fields [{' '.join(f'{f} 0 R' for f in fields)}] /DR << /Font << /Helv 5 0 R >> >> {da} >>")
    return serialize([objects.get(i, "null") for i in range(1, n)], root=1)


def serialize(objects, root):
    out = bytearray(b"%PDF-1.7\n")
    offsets = []
    for i, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root {root} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    pdf = OUT / "agreement.pdf"
    pdf.write_bytes(build_pdf([page_one(), page_two()]))
    # 1080 px wide matches the Pixel 7 screenshot width.
    subprocess.run(["pdftoppm", "-png", "-scale-to-x", "1080", "-scale-to-y", "-1", str(pdf), str(OUT / "page")], check=True)
    form = OUT / "form.pdf"
    form.write_bytes(form_pdf())
    subprocess.run(["pdftoppm", "-png", "-scale-to-x", "1080", "-scale-to-y", "-1", "-singlefile", str(form), str(OUT / "form-page")], check=True)
    print("Wrote", pdf, form, "and", sorted(p.name for p in OUT.glob("*.png")))
