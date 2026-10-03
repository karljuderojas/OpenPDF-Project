package io.github.karljuderojas.freepdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.karljuderojas.freepdf.R
import io.github.karljuderojas.freepdf.files.DocumentEntry
import io.github.karljuderojas.freepdf.pdf.render.PageSize
import io.github.karljuderojas.freepdf.pdf.sign.CertificateInfo
import io.github.karljuderojas.freepdf.pdf.sign.SignatureReport
import io.github.karljuderojas.freepdf.pdf.sign.SignatureStore
import io.github.karljuderojas.freepdf.pdf.sign.TimestampReport
import io.github.karljuderojas.freepdf.ui.sign.SignatureInk
import io.github.karljuderojas.freepdf.ui.files.FilesContent
import io.github.karljuderojas.freepdf.ui.theme.FreePdfTheme
import io.github.karljuderojas.freepdf.ui.viewer.ViewerContent
import io.github.karljuderojas.freepdf.ui.viewer.ViewerMode
import io.github.karljuderojas.freepdf.ui.viewer.ViewerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.Calendar

/**
 * Renders each screen to a PNG. CI runs `recordRoborazziDebug` on every pull request and posts
 * the images on the PR for review. Add a test here for every new or changed screen.
 *
 * Pages come from a real sample PDF (resources/sample/agreement.pdf), pre-rendered to PNG by
 * scripts/make_sample_pdf.py, because PDFium's native library does not load under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val samplePages = listOf(loadSample("page-1.png"), loadSample("page-2.png"))
    private val sample = ViewerState.Ready(List(samplePages.size) { PageSize(612f, 792f) })

    @Test
    fun filesEmpty() = capture("files_empty") { files(open = emptyList(), recent = emptyList()) }

    @Test
    fun files() {
        val hour = 60 * 60 * 1000L
        val agreement = DocumentEntry("content://a", "Service Agreement.pdf", now - 1 * hour)
        capture("files") {
            files(
                open = listOf(agreement, DocumentEntry("content://b", "Lease renewal 2027.pdf", now - 2 * hour)),
                recent = listOf(
                    agreement,
                    DocumentEntry("content://c", "W-9 form.pdf", now - 5 * hour),
                    DocumentEntry("content://d", "Bakery menu draft.pdf", now - 20 * hour),
                    DocumentEntry("content://e", "Invoice 1042.pdf", now - 30 * hour),
                    DocumentEntry("content://f", "Lease renewal 2027.pdf", now - 4 * 24 * hour),
                    DocumentEntry("content://g", "Insurance claim.pdf", now - 9 * 24 * hour),
                ),
            )
        }
    }

    @Test
    fun viewerRead() = capture("viewer_read") { viewer(ViewerMode.Read) }

    @Test
    fun viewerReadZoomed() {
        show { viewer(ViewerMode.Read) }
        // Pinch out to about 2.5x while dragging both fingers down, which pans to the page's title.
        composeRule.onNodeWithTag("page-list").performTouchInput {
            pinch(Offset(440f, 250f), Offset(340f, 1300f), Offset(640f, 450f), Offset(740f, 1900f))
        }
        captureRoot("viewer_read_zoomed")
    }

    @Test
    fun viewerUnsaved() = capture("viewer_read_unsaved") {
        viewer(ViewerMode.Read, sample.copy(canUndo = true, hasUnsavedChanges = true))
    }

    @Test
    fun viewerAnnotate() = capture("viewer_annotate") { viewer(ViewerMode.Annotate) }

    @Test
    fun viewerAnnotateHighlight() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_highlight) }
        // Mid-drag across the "1. Services" paragraph, so the preview shows.
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput {
            down(Offset(118f, 258f))
            listOf(200f, 420f, 640f, 836f).forEachIndexed { i, x -> moveTo(Offset(x, 262f + i * 20f)) }
        }
        captureRoot("viewer_annotate_highlight")
    }

    @Test
    fun viewerAnnotatePen() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_pen) }
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput {
            down(Offset(160f, 960f))
            for (i in 1..60) {
                val t = i / 60f
                moveTo(Offset(160f + t * 520f, 960f - 70f * kotlin.math.sin(t * 12f) * (1f - t / 2)))
            }
        }
        captureRoot("viewer_annotate_pen")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerAnnotateNote() {
        show { viewer(ViewerMode.Annotate, tool = R.string.tool_note) }
        // The dialog focuses its text field, whose blinking cursor never lets Compose go idle,
        // so drive the clock by hand from here.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("annotation-layer-0").performTouchInput { click(Offset(860f, 240f)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_annotate_note.png")
    }

    @Test
    fun viewerSign() = capture("viewer_sign") { viewer(ViewerMode.Sign) }

    @Test
    fun viewerSignPlacing() = capture("viewer_sign_placing") {
        viewer(
            ViewerMode.Sign,
            tool = R.string.tool_signature,
            savedSignatures = mapOf(SignatureStore.Kind.Signature to SignatureInk.render(sampleSignature(), 0xFF1A3FA8.toInt(), 6f)),
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignPad() {
        show { viewer(ViewerMode.Sign) }
        // First use of Signature opens the pad.
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithTag("signature-pad").performTouchInput {
            sampleSignature().forEach { stroke ->
                down(stroke.first())
                stroke.drop(1).forEach { moveTo(it) }
                up()
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_pad.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerSignFinish() {
        show { viewer(ViewerMode.Sign, sample.copy(canUndo = true, hasUnsavedChanges = true, hasSignature = true), signerName = "Dana Whitfield") }
        // The dialog's name field takes focus, and its blinking cursor never lets Compose go idle,
        // so drive the clock by hand and capture without further clicks.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_finish.png")
    }

    @Test
    fun viewerPages() = capture("viewer_pages") {
        viewer(ViewerMode.Pages, sample.copy(canUndo = true, hasUnsavedChanges = true), selectedPage = 1)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun viewerPagesDelete() {
        show { viewer(ViewerMode.Pages, selectedPage = 1) }
        composeRule.onNodeWithText("Delete").performScrollTo().performClick()
        composeRule.waitForIdle()
        // The dialog is its own window, so capture the whole screen rather than the root node.
        captureScreenRoboImage("build/outputs/roborazzi/viewer_pages_delete.png")
    }

    @Test
    fun viewerMore() = capture("viewer_more") { viewer(ViewerMode.More) }

    @Test
    fun viewerReadSigned() = capture("viewer_read_signed") {
        viewer(ViewerMode.Read, sample.copy(signatures = listOf(signedByDana)))
    }

    @Test
    fun viewerReadSignedThenChanged() = capture("viewer_read_signed_changed") {
        viewer(ViewerMode.Read, sample.copy(signatures = listOf(signedByDana.copy(coversWholeFile = false))))
    }

    @Test
    fun viewerSignatureDetails() {
        val issued = signedByDana.copy(
            signer = "Sam Ortiz",
            issuer = "Example Trust CA",
            trust = SignatureReport.Trust.Trusted,
            timestamp = TimestampReport(Instant.parse("2026-10-03T16:20:05Z"), "Free TSA", valid = true),
        )
        show {
            viewer(
                ViewerMode.Read,
                sample.copy(signatures = listOf(signedByDana.copy(coversWholeFile = false), issued)),
                showSignatures = true,
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_signature_details.png")
    }

    @Test
    fun viewerSignCertificate() {
        show { viewer(ViewerMode.Sign, showCertificate = true) }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_certificate.png")
    }

    @Test
    fun viewerSignCertificateImported() {
        val expires = Calendar.getInstance().apply { set(2028, Calendar.MARCH, 31) }.time
        show {
            viewer(
                ViewerMode.Sign,
                certificate = CertificateInfo("Dana Whitfield", "Example Trust CA", expires),
                timestampsOn = true,
                showCertificate = true,
            )
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/viewer_sign_certificate_imported.png")
    }

    /** A signature made with FreePDF's device certificate, checked and unchanged. */
    private val signedByDana = SignatureReport(
        fieldName = "Signature1",
        claimedSigner = "Dana Whitfield",
        claimedTime = Instant.parse("2026-10-03T15:04:00Z"),
        reason = "Signed with FreePDF",
        location = null,
        kind = SignatureReport.Kind.Pkcs7Detached,
        integrity = SignatureReport.Integrity.Intact,
        coversWholeFile = true,
        signer = "Dana Whitfield",
        issuer = "Dana Whitfield",
        signingTime = Instant.parse("2026-10-03T15:04:00Z"),
        trust = SignatureReport.Trust.SelfSigned,
    )

    // 3 Oct 2026, 15:00 on the test machine's clock, so Today and Yesterday group the same way everywhere.
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 3, 15, 0, 0) }.timeInMillis

    @Composable
    private fun files(open: List<DocumentEntry>, recent: List<DocumentEntry>) {
        FilesContent(open, recent, onOpenFile = {}, onOpen = {}, onClose = {}, onShare = {}, onForget = {}, now = now)
    }

    @Composable
    private fun viewer(
        mode: ViewerMode,
        state: ViewerState = sample,
        selectedPage: Int = 0,
        tool: Int? = null,
        savedSignatures: Map<SignatureStore.Kind, Bitmap> = emptyMap(),
        signerName: String = "",
        certificate: CertificateInfo? = null,
        timestampsOn: Boolean = false,
        showCertificate: Boolean = false,
        showSignatures: Boolean = false,
    ) {
        ViewerContent(
            state = state,
            onBack = {},
            loadPage = { index, width -> scaled(samplePages[index], width) },
            initialMode = mode,
            initialSelectedPage = selectedPage,
            initialTool = tool,
            savedSignatures = savedSignatures,
            signerName = signerName,
            certificate = certificate,
            timestampsOn = timestampsOn,
            initialShowCertificate = showCertificate,
            initialShowSignatures = showSignatures,
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        show(content)
        captureRoot(name)
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { FreePdfTheme(dynamicColor = false, content = content) }
    }

    private fun captureRoot(name: String) {
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A made-up cursive signature, in pad pixels: a looping first stroke and an underline. */
    private fun sampleSignature(): List<List<Offset>> {
        val loops = (0..80).map { i ->
            val t = i / 80f
            Offset(120f + t * 560f + 30f * kotlin.math.cos(t * 28f), 300f - 90f * kotlin.math.sin(t * 14f) * (1.1f - t))
        }
        val underline = (0..20).map { i -> Offset(150f + i * 30f, 410f - i * 2f) }
        return listOf(loops, underline)
    }

    private fun scaled(page: Bitmap, width: Int): Bitmap =
        Bitmap.createScaledBitmap(page, width, width * page.height / page.width, true)

    private fun loadSample(name: String): Bitmap {
        val stream = javaClass.classLoader!!.getResourceAsStream("sample/$name") ?: error("Missing sample/$name")
        return stream.use { BitmapFactory.decodeStream(it) } ?: error("Could not decode sample/$name")
    }
}
