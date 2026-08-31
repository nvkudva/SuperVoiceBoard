// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the top-level settings menu and back out of every screen it opens. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    @get:Rule(order = 0)
    val screenshots = ScreenshotOnFailure()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<SettingsActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @Before
    fun imeIsCurrent() {
        Qa.makeThisImeCurrent()
    }

    @Test
    fun mainMenuListsEveryScreen() {
        listOf(
            R.string.settings_screen_voice,
            R.string.settings_screen_preferences,
            R.string.settings_screen_appearance,
            R.string.settings_screen_toolbar,
            R.string.settings_screen_correction,
            R.string.settings_screen_advanced,
            R.string.settings_screen_about,
        ).forEach { id ->
            compose.onNodeWithText(string(id)).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * Every menu entry has to open something and let the user back out again.
     * A screen that opens onto a blank body, or a back arrow that closes the
     * whole activity, both show up here.
     */
    @Test
    fun everyScreenOpensAndComesBack() {
        listOf(
            R.string.settings_screen_voice,
            R.string.settings_screen_preferences,
            R.string.settings_screen_appearance,
            R.string.settings_screen_toolbar,
            R.string.settings_screen_correction,
            R.string.settings_screen_advanced,
            R.string.settings_screen_about,
        ).forEach { id ->
            val title = string(id)
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.waitForIdle()
            // The title survives into the opened screen's app bar.
            compose.onAllNodes(hasText(title)).fetchSemanticsNodes().let {
                assert(it.isNotEmpty()) { "$title did not open" }
            }
            Qa.device.pressBack()
            compose.waitForIdle()
            compose.onNodeWithText(string(R.string.settings_screen_about))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
