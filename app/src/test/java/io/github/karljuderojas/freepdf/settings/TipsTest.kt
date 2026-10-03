package io.github.karljuderojas.freepdf.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TipsTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tips-test", Context.MODE_PRIVATE)

    // Each instance stands for one app session.
    private fun tips() = Tips(prefs)

    @Test
    fun aTipShowsOnlyOnceEvenInLaterSessions() {
        val tips = tips()
        assertTrue(tips.shouldShow(Tip.Annotate))
        tips.markSeen(Tip.Annotate)
        assertFalse(tips.shouldShow(Tip.Annotate))
        assertFalse(tips().shouldShow(Tip.Annotate))
    }

    @Test
    fun atMostOneTipShowsPerSession() {
        val tips = tips()
        tips.markSeen(Tip.ReadZoom)
        assertFalse(tips.shouldShow(Tip.Sign))
        assertTrue(tips().shouldShow(Tip.Sign))
    }

    @Test
    fun turnedOffTipsDoNotShowAndStayOff() {
        val tips = tips()
        tips.setEnabled(false)
        assertFalse(tips.shouldShow(Tip.Pages))
        assertFalse(tips().enabled.value)
        assertFalse(tips().shouldShow(Tip.Pages))
        tips.setEnabled(true)
        assertTrue(tips.shouldShow(Tip.Pages))
    }

    @Test
    fun resetLetsEveryTipShowAgain() {
        val tips = tips()
        tips.markSeen(Tip.Annotate)
        tips.resetAll()
        assertTrue(tips.shouldShow(Tip.Annotate))
        assertTrue(tips().shouldShow(Tip.Annotate))
    }
}
