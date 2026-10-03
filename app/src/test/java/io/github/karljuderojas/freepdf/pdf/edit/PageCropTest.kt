package io.github.karljuderojas.freepdf.pdf.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageCropTest {

    private fun sample(): PDDocument =
        PDDocument.load(javaClass.classLoader!!.getResourceAsStream("sample/agreement.pdf"))

    @Test
    fun trimsTheEdgesOfAnUprightPage() {
        sample().use { document ->
            val before = document.getPage(0).cropBox
            PageCrop.crop(document, listOf(0), CropMargins(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.05f))
            val after = document.getPage(0).cropBox
            assertEquals(before.lowerLeftX + before.width * 0.1f, after.lowerLeftX, 0.01f)
            assertEquals(before.upperRightX - before.width * 0.3f, after.upperRightX, 0.01f)
            // PDF's y axis runs up, so the top margin comes off the upper edge.
            assertEquals(before.upperRightY - before.height * 0.2f, after.upperRightY, 0.01f)
            assertEquals(before.lowerLeftY + before.height * 0.05f, after.lowerLeftY, 0.01f)
        }
    }

    @Test
    fun marginsFollowThePageAsItIsShownWhenItIsRotated() {
        sample().use { document ->
            val page = document.getPage(0)
            page.rotation = 90
            val before = page.cropBox
            // Shown turned a quarter clockwise, the page's own bottom edge is on the left.
            PageCrop.crop(document, listOf(0), CropMargins(left = 0.2f))
            val after = page.cropBox
            assertEquals(before.lowerLeftY + before.height * 0.2f, after.lowerLeftY, 0.01f)
            assertEquals(before.lowerLeftX, after.lowerLeftX, 0.01f)
            assertEquals(before.upperRightX, after.upperRightX, 0.01f)
            assertEquals(before.upperRightY, after.upperRightY, 0.01f)
        }
    }

    @Test
    fun onlyTheNamedPagesAreCropped() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            val untouched = document.getPage(1).cropBox.width
            PageCrop.crop(document, listOf(0), CropMargins(left = 0.25f, right = 0.25f))
            assertEquals(untouched, document.getPage(1).cropBox.width, 0.01f)
            assertTrue(document.getPage(0).cropBox.width < untouched)
        }
    }

    @Test
    fun resetShowsTheWholePageAgain() {
        sample().use { document ->
            val media = document.getPage(0).mediaBox
            PageCrop.crop(document, listOf(0), CropMargins(left = 0.1f, top = 0.1f, right = 0.1f, bottom = 0.1f))
            assertTrue(PageCrop.isCropped(document.getPage(0)))
            assertTrue(PageCrop.reset(document, listOf(0)))
            val box = document.getPage(0).cropBox
            assertEquals(media.width, box.width, 0.01f)
            assertEquals(media.height, box.height, 0.01f)
            assertFalse(PageCrop.isCropped(document.getPage(0)))
        }
    }

    @Test
    fun resetOnPagesThatAreNotCroppedChangesNothing() {
        sample().use { document ->
            PageEditor.insertBlank(document, 1)
            assertFalse(PageCrop.reset(document, listOf(0, 1)))
            PageCrop.crop(document, listOf(1), CropMargins(top = 0.1f))
            // One cropped page among the two is enough for the reset to count.
            assertTrue(PageCrop.reset(document, listOf(0, 1)))
            assertFalse(PageCrop.reset(document, listOf(0, 1)))
        }
    }

    @Test
    fun printBoxesShrinkWithTheCropAndGoWithTheReset() {
        sample().use { document ->
            val page = document.getPage(0)
            val media = page.mediaBox
            page.trimBox = PDRectangle(media.lowerLeftX, media.lowerLeftY, media.width, media.height)
            // An art box already inside what is kept stays as it is.
            val art = PDRectangle(media.lowerLeftX + 100f, media.lowerLeftY + 100f, 50f, 50f)
            page.artBox = art
            PageCrop.crop(document, listOf(0), CropMargins(left = 0.1f, top = 0.1f))
            val crop = page.cropBox
            assertEquals(crop.lowerLeftX, page.trimBox.lowerLeftX, 0.01f)
            assertEquals(crop.upperRightY, page.trimBox.upperRightY, 0.01f)
            assertEquals(media.upperRightX, page.trimBox.upperRightX, 0.01f)
            assertEquals(art.lowerLeftX, page.artBox.lowerLeftX, 0.01f)
            assertEquals(art.width, page.artBox.width, 0.01f)
            // A page without a bleed box does not get one.
            assertFalse(page.cosObject.containsKey(COSName.BLEED_BOX))
            assertTrue(PageCrop.reset(document, listOf(0)))
            assertFalse(page.cosObject.containsKey(COSName.TRIM_BOX))
            assertFalse(page.cosObject.containsKey(COSName.ART_BOX))
        }
    }

    @Test
    fun refusesToCropAwayTheWholePage() {
        sample().use { document ->
            assertThrows(IllegalArgumentException::class.java) {
                PageCrop.crop(document, listOf(0), CropMargins(left = 0.5f, right = 0.5f))
            }
            assertFalse(CropMargins(left = -0.1f).isValid)
            assertTrue(CropMargins().isEmpty)
        }
    }
}
