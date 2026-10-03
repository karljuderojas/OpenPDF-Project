#!/usr/bin/env python3
"""Writes the sample PDF used by the screenshot tests, then renders its pages to PNG.

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

    out = bytearray(b"%PDF-1.7\n")
    offsets = []
    for i, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root {catalog} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    pdf = OUT / "agreement.pdf"
    pdf.write_bytes(build_pdf([page_one(), page_two()]))
    # 1080 px wide matches the Pixel 7 screenshot width.
    subprocess.run(["pdftoppm", "-png", "-scale-to-x", "1080", "-scale-to-y", "-1", str(pdf), str(OUT / "page")], check=True)
    print("Wrote", pdf, "and", sorted(p.name for p in OUT.glob("page-*.png")))
