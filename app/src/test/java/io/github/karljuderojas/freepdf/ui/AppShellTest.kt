package io.github.karljuderojas.freepdf.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eachTabKeepsItsSaveableStateWhileAnotherIsShown() {
        composeRule.setContent {
            var tab by remember { mutableStateOf(MainTab.Home) }
            AppShell(selected = tab, onSelect = { tab = it }) { selected, modifier ->
                // Every tab shows the same counter; kept state shows as a count that survives switching.
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Button(onClick = { taps++ }, modifier = modifier.testTag("counter")) { Text("${selected.name} $taps") }
            }
        }
        composeRule.onNodeWithTag("counter").performClick().performClick()
        composeRule.onNodeWithTag("counter").assertTextEquals("Home 2")

        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithTag("counter").assertTextEquals("Tools 0")
        composeRule.onNodeWithTag("counter").performClick()
        composeRule.onNodeWithTag("counter").assertTextEquals("Tools 1")

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithTag("counter").assertTextEquals("Home 2")
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithTag("counter").assertTextEquals("Tools 1")
    }
}
