package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import io.github.karljuderojas.freepdf.pdf.DisplayRect
import io.github.karljuderojas.freepdf.pdf.PdfRect
import io.github.karljuderojas.freepdf.pdf.pdfToDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Runs under Robolectric so FreePdfApp initialises PdfBox-Android's resources. */
@RunWith(AndroidJUnit4::class)
class TextEditingTest {

    /** A Letter page (or one with [box]) with [draw] on it, saved and loaded again so it is a PDF as a file has it. */
    private fun page(rotation: Int = 0, box: PDRectangle? = null, draw: PDPageContentStream.(PDDocument) -> Unit): PDDocument {
        val document = PDDocument()
        val page = (if (box == null) PDPage() else PDPage(box)).also { it.rotation = rotation }
        document.addPage(page)
        PDPageContentStream(document, page).use { it.draw(document) }
        return reloaded(document)
    }

    /**
     * A Letter page whose content stream is [content] as written, with [fonts] registered as
     * /F1, /F2, ... : for operators PDPageContentStream has no method for.
     */
    private fun rawPage(vararg fonts: PDFont, content: (names: List<String>) -> String): PDDocument {
        val document = PDDocument()
        val page = PDPage().also { it.resources = PDResources() }
        document.addPage(page)
        val names = fonts.map { page.resources.add(it).name }
        val stream = PDStream(document)
        stream.createOutputStream().use { it.write(content(names).toByteArray(Charsets.ISO_8859_1)) }
        page.setContents(stream)
        return reloaded(document)
    }

    private fun reloaded(document: PDDocument): PDDocument {
        val bytes = ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        document.close()
        return PDDocument.load(bytes)
    }

    private fun PDPageContentStream.line(font: PDFont, size: Float, x: Float, y: Float, text: String) {
        beginText()
        setFont(font, size)
        newLineAtOffset(x, y)
        showText(text)
        endText()
    }

    private fun PDDocument.text() = PDFTextStripper().getText(this).trim().lines().joinToString("\n") { it.trim() }

    /** The operands of the page's string-showing operators, in stream order: each a string, or the TJ array's elements. */
    private fun PDDocument.shown(): List<Any> {
        val tokens = PDFStreamParser(getPage(0)).also { it.parse() }.tokens
        val showing = setOf("Tj", "TJ", "'", "\"")
        return tokens.indices.filter { i -> (tokens[i] as? Operator)?.let { it.name in showing } == true }.map { i ->
            when (val operand = tokens[i - 1]) {
                is COSString -> String(operand.bytes, Charsets.ISO_8859_1)
                is COSArray -> operand.toList().map { if (it is COSString) String(it.bytes, Charsets.ISO_8859_1) else (it as COSNumber).floatValue() }
                else -> error("Not a string")
            }
        }
    }

    private fun PDDocument.stream() = String(getPage(0).contents.readBytes(), Charsets.ISO_8859_1)

    // Fractions of a Letter page across and down, for a point x, y points from its bottom left.
    private fun fx(x: Float) = x / 612f
    private fun fy(y: Float) = (792f - y) / 792f

    private fun centre(box: DisplayRect) = (box.left + box.right) / 2 to (box.top + box.bottom) / 2

    private fun liberation(document: PDDocument) = PDType0Font.load(document, PDFBoxResourceLoader.getStream(PdfText.LIBERATION_SANS))

    private fun assertClose(expected: DisplayRect, actual: DisplayRect) {
        val message = "expected $expected but was $actual"
        assertTrue(message, abs(expected.left - actual.left) < 0.004f && abs(expected.right - actual.right) < 0.004f)
        assertTrue(message, abs(expected.top - actual.top) < 0.004f && abs(expected.bottom - actual.bottom) < 0.004f)
    }

    @Test
    fun findsTheLineUnderATapAndNothingElsewhere() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Hello world") }.use { document ->
            assertEquals("Hello world", TextEditing.lineAt(document, 0, fx(90f), fy(703f))?.text)
            assertNull(TextEditing.lineAt(document, 0, fx(300f), fy(300f)))
        }
    }

    @Test
    fun replacesTheWordsInTheSameFontAndLeavesOtherLinesAlone() {
        page {
            line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Hello world")
            line(PDType1Font.HELVETICA_BOLD, 14f, 72f, 650f, "Second line")
        }.use { document ->
            val outcome = TextEditing.replace(document, 0, fx(90f), fy(703f), "Hello world", "Goodbye moon")
            assertEquals(TextEditing.Outcome.SameFont, outcome)
            assertEquals("Goodbye moon\nSecond line", document.text())
        }
    }

    @Test
    fun theOldWordsAreRemovedNotPaintedOver() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Secret figure") }.use { document ->
            TextEditing.replace(document, 0, fx(90f), fy(703f), "Secret figure", "Public figure")
            // One line of text on the page, so nothing of the old words is left under the new.
            assertEquals("Public figure", document.text())
        }
    }

    @Test
    fun aLineMadeOfSeveralStringsIsOneLineButAFarColumnIsNot() {
        page {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            newLineAtOffset(72f, 700f)
            showText("Total: ")
            showText("100 USD")
            endText()
            line(PDType1Font.HELVETICA, 12f, 400f, 700f, "Right column")
        }.use { document ->
            assertEquals("Total: 100 USD", TextEditing.lineAt(document, 0, fx(80f), fy(703f))?.text)
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Total: 100 USD", "Total: 250 USD")
            assertEquals("Total: 250 USD Right column", document.text().replace(Regex("\\s+"), " "))
        }
    }

    @Test
    fun aSubsetFontKeepsToTheLettersItHasAndOtherLettersUseTheBundledFont() {
        page { document -> line(liberation(document), 12f, 72f, 700f, "Pay the invoice") }.use { document ->
            assertEquals("Pay the invoice", TextEditing.lineAt(document, 0, fx(90f), fy(703f))?.text)
            val same = TextEditing.replace(document, 0, fx(90f), fy(703f), "Pay the invoice", "Pay the in")
            assertEquals(TextEditing.Outcome.SameFont, same)
            assertEquals("Pay the in", document.text())
            // "z" was never drawn, so the subset has no glyph for it.
            val other = TextEditing.replace(document, 0, fx(90f), fy(703f), "Pay the in", "Pay the zebra")
            assertEquals(TextEditing.Outcome.OtherFont, other)
            assertEquals("Pay the zebra", document.text())
        }
    }

    @Test
    fun lettersHelveticaLacksMoveTheLineToTheBundledFont() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Date: Oct 3") }.use { document ->
            val outcome = TextEditing.replace(document, 0, fx(90f), fy(703f), "Date: Oct 3", "Дата: 3 окт")
            assertEquals(TextEditing.Outcome.OtherFont, outcome)
            assertEquals("Дата: 3 окт", document.text())
        }
    }

    @Test
    fun anEmptyReplacementDeletesTheLine() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Remove me") }.use { document ->
            TextEditing.replace(document, 0, fx(90f), fy(703f), "Remove me", "")
            assertEquals("", document.text())
        }
    }

    @Test
    fun aLineThatChangedSinceItWasReadIsRefused() {
        page { line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Hello world") }.use { document ->
            val failure = runCatching { TextEditing.replace(document, 0, fx(90f), fy(703f), "Something else", "X") }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals("Hello world", document.text())
        }
    }

    @Test
    fun textThatReadsUprightOnARotatedPageCanBeEdited() {
        page(rotation = 90) {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            setTextMatrix(Matrix.getRotateInstance(Math.toRadians(90.0), 100f, 100f))
            showText("Sideways ok")
            endText()
        }.use { document ->
            // Shown turned a quarter clockwise, the text runs left to right from near the top left.
            var found: TextEditing.EditableLine? = null
            var at = 0f to 0f
            for (i in 0 until 60) for (j in 0 until 60) {
                TextEditing.lineAt(document, 0, i / 60f, j / 60f)?.let { found = it; at = i / 60f to j / 60f }
            }
            assertEquals("Sideways ok", found?.text)
            TextEditing.replace(document, 0, at.first, at.second, "Sideways ok", "Turned")
            assertEquals("Turned", document.text())
        }
    }

    @Test
    fun wordsSpacedByTjOffsetsReadWithSpacesAndStayApart() {
        page {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            newLineAtOffset(72f, 700f)
            showTextWithPositioning(arrayOf<Any>("Hello", -500f, "world"))
            endText()
        }.use { document ->
            assertEquals("Hello world", TextEditing.lineAt(document, 0, fx(90f), fy(703f))?.text)
            assertEquals(TextEditing.Outcome.SameFont, TextEditing.replace(document, 0, fx(90f), fy(703f), "Hello world", "Hello there world"))
            assertEquals("Hello there world", document.text())
        }
    }

    @Test
    fun aSubsetFontWithoutASpaceGlyphKeepsItsFontWithSpacesAsGaps() {
        // Like pdfTeX: no space glyph is ever drawn; the gaps between words are TJ offsets.
        page { document ->
            beginText()
            setFont(liberation(document), 12f)
            newLineAtOffset(72f, 700f)
            showTextWithPositioning(arrayOf<Any>("Pay", -278f, "the", -278f, "invoice"))
            endText()
        }.use { document ->
            assertEquals("Pay the invoice", TextEditing.lineAt(document, 0, fx(90f), fy(703f))?.text)
            val outcome = TextEditing.replace(document, 0, fx(90f), fy(703f), "Pay the invoice", "Pay the nice invoice")
            assertEquals(TextEditing.Outcome.SameFont, outcome)
            assertEquals("Pay the nice invoice", document.text())
            val array = document.shown().single() as List<*>
            // Four words and three gaps, each a negative adjustment.
            assertEquals(7, array.size)
            assertTrue(array.filterIsInstance<Float>().all { it < 0f })
            assertEquals(4, array.filterIsInstance<String>().size)
        }
    }

    @Test
    fun aSpaceAddedToAPlainStringInAFontWithoutOneTurnsItIntoATjArray() {
        page { document -> line(liberation(document), 12f, 72f, 700f, "Pay") }.use { document ->
            assertEquals(TextEditing.Outcome.SameFont, TextEditing.replace(document, 0, fx(80f), fy(703f), "Pay", "Pa y"))
            assertEquals("Pa y", document.text())
            assertEquals(3, (document.shown().single() as List<*>).size)
        }
    }

    @Test
    fun theNextLineOperatorsAreFoundAndEdited() {
        rawPage(PDType1Font.HELVETICA) { (f) ->
            "BT /$f 12 Tf 14 TL 72 714 Td (Pay) ' 1 0.5 (the bill) \" ET"
        }.use { document ->
            assertEquals(listOf("Pay", "the bill"), TextEditing.lines(document, 0).map { it.text })
            val outcome = TextEditing.replace(document, 0, fx(80f), fy(689f), "the bill", "the invoice")
            assertEquals(TextEditing.Outcome.SameFont, outcome)
            assertEquals("Pay\nthe invoice", document.text())
        }
    }

    @Test
    fun textOnAPageTurnedUpsideDownCanBeEdited() {
        page(rotation = 180) {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            setTextMatrix(Matrix.getRotateInstance(Math.toRadians(180.0), 400f, 100f))
            showText("Upside")
            endText()
        }.use { document ->
            val line = TextEditing.lines(document, 0).single()
            assertEquals("Upside", line.text)
            val (x, y) = centre(line.box)
            assertEquals("Upside", TextEditing.lineAt(document, 0, x, y)?.text)
            assertEquals(TextEditing.Outcome.SameFont, TextEditing.replace(document, 0, x, y, "Upside", "Down"))
            assertEquals("Down", document.text())
        }
    }

    @Test
    fun theBoxIsWhereTheTextIsShownForEveryRotationAndAnOffsetCropBox() {
        val crop = PdfRect(50f, 60f, 450f, 560f)
        val width = PDType1Font.HELVETICA.getStringWidth("Box") / 1000f * 12f
        for (rotation in listOf(0, 90, 180, 270)) {
            page(rotation, PDRectangle(50f, 60f, 400f, 500f)) {
                beginText()
                setFont(PDType1Font.HELVETICA, 12f)
                // Turned with the page, so it reads upright as shown.
                setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rotation.toDouble()), 100f, 100f))
                showText("Box")
                endText()
            }.use { document ->
                // The glyph box (ascent 0.8, descent 0.22 of the size) through the same rotation about 100, 100.
                val c = cos(Math.toRadians(rotation.toDouble())).toFloat()
                val s = sin(Math.toRadians(rotation.toDouble())).toFloat()
                fun turned(px: Float, py: Float) = (px * c - py * s + 100f) to (px * s + py * c + 100f)
                val corners = listOf(turned(0f, -12f * 0.22f), turned(width, 12f * 0.8f), turned(0f, 12f * 0.8f), turned(width, -12f * 0.22f))
                val inPdf = PdfRect(corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
                val expected = pdfToDisplay(inPdf, rotation, crop)
                val line = TextEditing.lines(document, 0).single()
                assertEquals("Box", line.text)
                assertClose(expected, line.box)
                val (x, y) = centre(expected)
                assertEquals("rotation $rotation", "Box", TextEditing.lineAt(document, 0, x, y)?.text)
            }
        }
    }

    @Test
    fun aNonEmbeddedCidFontOnlyReusesLettersThePageDrew() {
        // "Arial" by name only, Identity-H: new letters would be encoded with whatever font stands in for it here.
        val fresh = PDDocument()
        val page = PDPage().also { it.resources = PDResources() }
        fresh.addPage(page)
        val name = page.resources.add(nonEmbeddedArial(fresh)).name
        val stream = PDStream(fresh)
        stream.createOutputStream().use { it.write("BT /$name 12 Tf 72 700 Td <000100020003> Tj ET".toByteArray(Charsets.ISO_8859_1)) }
        page.setContents(stream)
        reloaded(fresh).use { document ->
            assertEquals("Pay", TextEditing.lineAt(document, 0, fx(80f), fy(703f))?.text)
            assertEquals(TextEditing.Outcome.SameFont, TextEditing.replace(document, 0, fx(80f), fy(703f), "Pay", "Pa"))
            assertEquals("Pa", document.text())
            assertEquals(TextEditing.Outcome.OtherFont, TextEditing.replace(document, 0, fx(80f), fy(703f), "Pa", "Paz"))
            assertEquals("Paz", document.text())
        }
    }

    /** A Type 0 font named Arial with no font file, whose codes 1, 2, 3 are P, a, y. */
    private fun nonEmbeddedArial(document: PDDocument): PDFont {
        val descriptor = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT_DESC)
            setName(COSName.FONT_NAME, "Arial")
            setInt(COSName.FLAGS, 32)
            setItem(COSName.FONT_BBOX, PDRectangle(-665f, -325f, 2000f, 1006f).cosArray)
            setInt(COSName.ITALIC_ANGLE, 0)
            setInt(COSName.ASCENT, 905)
            setInt(COSName.DESCENT, -212)
            setInt(COSName.CAP_HEIGHT, 716)
            setInt(COSName.STEM_V, 80)
        }
        val cid = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.CID_FONT_TYPE2)
            setName(COSName.BASE_FONT, "Arial")
            setItem(COSName.CIDSYSTEMINFO, COSDictionary().apply {
                setString(COSName.REGISTRY, "Adobe")
                setString(COSName.ORDERING, "Identity")
                setInt(COSName.SUPPLEMENT, 0)
            })
            setItem(COSName.FONT_DESC, descriptor)
            setInt(COSName.DW, 600)
        }
        val toUnicode = PDStream(document)
        toUnicode.createOutputStream().use {
            it.write(
                """
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                /CMapName /Adobe-Identity-UCS def
                /CMapType 2 def
                1 begincodespacerange
                <0000> <FFFF>
                endcodespacerange
                3 beginbfchar
                <0001> <0050>
                <0002> <0061>
                <0003> <0079>
                endbfchar
                endcmap
                CMapName currentdict /CMap defineresource pop
                end
                end
                """.trimIndent().toByteArray(Charsets.ISO_8859_1),
            )
        }
        val type0 = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.TYPE0)
            setName(COSName.BASE_FONT, "Arial")
            setItem(COSName.ENCODING, COSName.IDENTITY_H)
            setItem(COSName.DESCENDANT_FONTS, COSArray().apply { add(cid) })
            setItem(COSName.TO_UNICODE, toUnicode)
        }
        return PDType0Font(type0)
    }

    @Test
    fun actualTextOfAMarkedContentSpanGoesWithTheOldWords() {
        // A named property list, as PdfBox writes it ...
        page {
            beginMarkedContent(COSName.getPDFName("Span"), PDPropertyList.create(COSDictionary().apply { setString(COSName.ACTUAL_TEXT, "Secret figure") }))
            line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Public figure")
            endMarkedContent()
        }.use { document ->
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Public figure", "Other figure")
            assertEquals("Other figure", document.text())
            val properties = document.getPage(0).resources.cosObject.getCOSDictionary(COSName.PROPERTIES)
            assertNotNull(properties)
            assertTrue(properties!!.keySet().all { properties.getCOSDictionary(it)?.containsKey(COSName.ACTUAL_TEXT) == false })
        }
        // ... and an inline dictionary, as Word and InDesign write it.
        rawPage(PDType1Font.HELVETICA) { (f) ->
            "/Span <</ActualText (Secret figure) /Alt (Secret figure)>> BDC BT /$f 12 Tf 72 700 Td (Public figure) Tj ET EMC"
        }.use { document ->
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Public figure", "Other figure")
            assertEquals("Other figure", document.text())
            assertFalse(document.stream().contains("Secret"))
            assertTrue(document.stream().contains("BDC"))
        }
    }

    @Test
    fun aLineSetInTwoFontsIsOneLineAndEachWordKeepsItsFont() {
        // Three strings placed by advance in one text object: the later ones move with the change.
        page {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            newLineAtOffset(72f, 700f)
            showText("Pay ")
            setFont(PDType1Font.HELVETICA_BOLD, 12f)
            showText("now")
            setFont(PDType1Font.HELVETICA, 12f)
            showText(" please")
            endText()
        }.use { document ->
            val line = TextEditing.lineAt(document, 0, fx(80f), fy(703f))
            assertEquals("Pay now please", line?.text)
            assertEquals(TextEditing.Outcome.SameFont, TextEditing.replace(document, 0, fx(80f), fy(703f), "Pay now please", "Pay later please"))
            assertEquals("Pay later please", document.text())
            assertEquals(listOf("Pay ", "later", " please"), document.shown())
        }
        // Three strings each put at its own place: the ones after a change that alters the width join it.
        page {
            line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Pay ")
            line(PDType1Font.HELVETICA_BOLD, 12f, 95f, 700f, "now")
            line(PDType1Font.HELVETICA, 12f, 118f, 700f, " please")
        }.use { document ->
            assertEquals("Pay now please", TextEditing.lineAt(document, 0, fx(80f), fy(703f))?.text)
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Pay now please", "Pay later please")
            assertEquals("Pay later please", document.text())
            assertEquals(listOf("Pay ", "later please", ""), document.shown())
        }
        // ... but a change of the same width leaves them where they are, in their fonts.
        page {
            line(PDType1Font.HELVETICA, 12f, 72f, 700f, "Pay ")
            line(PDType1Font.HELVETICA_BOLD, 12f, 95f, 700f, "nwo")
            line(PDType1Font.HELVETICA, 12f, 118f, 700f, " please")
        }.use { document ->
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Pay nwo please", "Pay now please")
            assertEquals(listOf("Pay ", "now", " please"), document.shown())
        }
    }

    @Test
    fun linesListsWhatLineAtWouldFind() {
        page {
            line(PDType1Font.HELVETICA, 12f, 72f, 700f, "First")
            line(PDType1Font.HELVETICA, 12f, 72f, 650f, "Second")
            line(PDType1Font.HELVETICA, 12f, 400f, 650f, "Column")
        }.use { document ->
            val lines = TextEditing.lines(document, 0)
            assertEquals(listOf("First", "Second", "Column"), lines.map { it.text })
            lines.forEach { line ->
                val (x, y) = centre(line.box)
                assertEquals(line, TextEditing.lineAt(document, 0, x, y))
            }
        }
    }

    @Test
    fun textDrawnByAFormXObjectIsNotEditableAndDoesNotJoinThePagesOwnLine() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        val form = PDFormXObject(document).apply {
            bBox = PDRectangle(0f, 0f, 612f, 792f)
            resources = PDResources()
        }
        PDPageContentStream(document, form, form.stream.createOutputStream()).use {
            it.line(PDType1Font.HELVETICA, 12f, 300f, 700f, "Inside the form")
            it.line(PDType1Font.HELVETICA, 12f, 72f, 600f, "Also inside")
        }
        PDPageContentStream(document, page).use {
            it.line(PDType1Font.HELVETICA, 12f, 72f, 700f, "On the page")
            it.drawForm(form)
        }
        reloaded(document).use { reloadedDocument ->
            assertNull(TextEditing.lineAt(reloadedDocument, 0, fx(330f), fy(703f)))
            assertNull(TextEditing.lineAt(reloadedDocument, 0, fx(90f), fy(603f)))
            assertEquals(listOf("On the page"), TextEditing.lines(reloadedDocument, 0).map { it.text })
            // The page's own line on the same baseline as form text is still edited on its own.
            val outcome = TextEditing.replace(reloadedDocument, 0, fx(90f), fy(703f), "On the page", "Edited page line")
            assertEquals(TextEditing.Outcome.SameFont, outcome)
            val text = reloadedDocument.text()
            listOf("Edited page line", "Inside the form", "Also inside").forEach { assertTrue(text, it in text) }
            assertFalse(text, "On the page" in text)
        }
    }

    @Test
    fun invisibleTextOfRenderModeSevenIsNotEditable() {
        page {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            setRenderingMode(RenderingMode.NEITHER_CLIP)
            newLineAtOffset(72f, 700f)
            showText("hidden words")
            endText()
        }.use { document ->
            assertNull(TextEditing.lineAt(document, 0, fx(80f), fy(703f)))
            assertTrue(TextEditing.lines(document, 0).isEmpty())
        }
    }

    @Test
    fun aLeadingAdjustmentInATjArrayIsKept() {
        page {
            beginText()
            setFont(PDType1Font.HELVETICA, 12f)
            newLineAtOffset(72f, 700f)
            showTextWithPositioning(arrayOf<Any>(-250f, "Hello"))
            endText()
        }.use { document ->
            TextEditing.replace(document, 0, fx(80f), fy(703f), "Hello", "Hullo")
            assertEquals(listOf(listOf(-250f, "Hullo")), document.shown())
        }
    }
}
