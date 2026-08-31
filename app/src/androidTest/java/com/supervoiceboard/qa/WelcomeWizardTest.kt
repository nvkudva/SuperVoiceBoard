// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsActivity
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What a first-run user sees: the wizard, before the IME is enabled. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class WelcomeWizardTest {

    @get:Rule(order = 0)
    val screenshots = ScreenshotOnFailure()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<SettingsActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @After
    fun restoreIme() {
        Qa.makeThisImeCurrent()
    }

    @Test
    fun firstRunShowsTheWizardWithItsSetupSteps() {
        compose.onNodeWithText(
            string(R.string.setup_welcome_title).format(string(R.string.english_ime_name))
        ).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.setup_start_action)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.setup_step1_action)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.setup_voice_action)).assertIsDisplayed()
        Qa.screenshot("welcome-wizard-steps")
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun imeIsNotSetUpYet() {
            Qa.makeThisImeNotCurrent()
        }
    }
}
