package io.github.karljuderojas.freepdf.pdf.sign

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
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

    fun append(document: PDDocument, trail: AuditTrail) {
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
        }

        var page = newPage(document)
        var stream = PDPageContentStream(document, page)
        var y = page.mediaBox.height - MARGIN
        for (line in lines) {
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

    private data class Line(val text: String, val font: PDFont = PDType1Font.HELVETICA, val size: Float = 10f)
}
