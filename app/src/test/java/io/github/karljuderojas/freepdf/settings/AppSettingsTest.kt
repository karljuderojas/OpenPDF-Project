package io.github.karljuderojas.freepdf.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @Test
    fun defaultsFollowThePhoneAndRememberHistory() {
        val settings = AppSettings(prefs)
        assertEquals(ThemeChoice.System, settings.theme.value)
        assertTrue(settings.rememberHistory.value)
        assertEquals(PageColors.Normal, settings.pageColors.value)
    }

    @Test
    fun choicesSurviveARestart() {
        AppSettings(prefs).apply {
            setTheme(ThemeChoice.Dark)
            setRememberHistory(false)
            setPageColors(PageColors.Sepia)
        }
        val restarted = AppSettings(prefs)
        assertEquals(ThemeChoice.Dark, restarted.theme.value)
        assertFalse(restarted.rememberHistory.value)
        assertEquals(PageColors.Sepia, restarted.pageColors.value)
    }
}
