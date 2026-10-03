package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Appends a "Signing certificate" page listing the document fingerprint, every signer and the
 * event log. Written before the digital signature is applied, so the signature covers it too.
 */
object AuditPageWriter {

    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())
    private const val MARGIN = 54f
    private const val LINE = 14f

    // Helvetica only covers Western European letters. Names such as Łukasz or Владимир use
    // PdfBox-Android's bundled Liberation Sans (Latin, Greek, Cyrillic), embedded in the file.
    private const val UNICODE_FONT = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"

    /** [notes] are printed at the end, e.g. what the record does and does not prove. */
    fun append(document: PDDocument, trail: AuditTrail, notes: List<String> = emptyList()) {
        val lines = buildList {
            add(Line("Signing certificate", PDType1Font.HELVETICA_BOLD, 16f))
            add(Line("Document: ${trail.documentName}"))
            add(Line("Document ID: ${trail.documentId}"))
            add(Line("Original SHA-256: ${trail.originalSha256}", size = 8f))
            add(Line(""))
            add(Line("Signers", PDType1Font.HELVETICA_BOLD, 12f))
            trail.signers.forEach { s ->
                add(Line("${s.name}${s.email?.let { " <$it>" }.orEmpty()}", PDType1Font.HELVETICA_BOLD))
                add(Line("Signed ${timeFormat.format(s.signedAt)} by ${s.method.name.lowercase()} signature"))
                s.reason?.let { add(Line("Reason: $it")) }
                add(Line("Device: ${s.device}"))
                add(Line("Consent: ${s.consentText}", size = 8f))
                add(Line(""))
            }
            add(Line("Event log", PDType1Font.HELVETICA_BOLD, 12f))
            trail.events.forEach { e ->
                add(Line("${timeFormat.format(e.at)}  ${e.type}  ${e.actor}${e.detail?.let { "  ($it)" }.orEmpty()}", size = 9f))
            }
            if (notes.isNotEmpty()) {
                add(Line(""))
                add(Line("Notes", PDType1Font.HELVETICA_BOLD, 12f))
                notes.forEach { add(Line(it, size = 9f)) }
            }
        }
        val unicode by lazy { PDType0Font.load(document, PDFBoxResourceLoader.getStream(UNICODE_FONT)) }
        val printable = lines
            .map { if (it.font.canEncode(it.text)) it else it.copy(font = unicode) }
            .flatMap { it.wrapped(PDRectangle.LETTER.width - 2 * MARGIN) }

        var page = newPage(document)
        var stream = PDPageContentStream(document, page)
        var y = page.mediaBox.height - MARGIN
        for (line in printable) {
            if (y < MARGIN) {
                stream.close()
                page = newPage(document)
                stream = PDPageContentStream(document, page)
                y = page.mediaBox.height - MARGIN
            }
            if (line.text.isNotEmpty()) {
                stream.beginText()
                stream.setFont(line.font, line.size)
                stream.newLineAtOffset(MARGIN, y)
                stream.showText(line.text)
                stream.endText()
            }
            y -= maxOf(LINE, line.size + 4f)
        }
        stream.close()
    }

    private fun newPage(document: PDDocument) = PDPage(PDRectangle.LETTER).also { document.addPage(it) }

    private data class Line(val text: String, val font: PDFont = PDType1Font.HELVETICA, val size: Float = 10f) {

        /** Splits on spaces to fit [width], after swapping characters the font cannot draw for "?". */
        fun wrapped(width: Float): List<Line> {
            val safe = buildString {
                text.codePoints().forEach { cp ->
                    val c = String(Character.toChars(cp))
                    append(if (font.canEncode(c)) c else "?")
                }
            }
            if (safe.isEmpty()) return listOf(this)
            val out = mutableListOf<Line>()
            var current = ""
            for (word in safe.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && widthOf(candidate) > width) {
                    out += copy(text = current)
                    current = word
                } else {
                    current = candidate
                }
            }
            out += copy(text = current)
            return out
        }

        private fun widthOf(s: String) = font.getStringWidth(s) / 1000f * size
    }

    private fun PDFont.canEncode(text: String): Boolean = runCatching { encode(text) }.isSuccess
}
