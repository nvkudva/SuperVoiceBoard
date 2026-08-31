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

/**
 * The voice models screen on a device with nothing downloaded — the state a
 * new install is actually in. No model is fetched here; CI has no business
 * pulling hundreds of megabytes to prove a screen renders.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class VoiceModelsTest {

    @get:Rule(order = 0)
    val screenshots = ScreenshotOnFailure()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<SettingsActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @Before
    fun openModelsScreen() {
        Qa.makeThisImeCurrent()
        compose.onNodeWithText(string(R.string.settings_screen_voice)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.settings_screen_voice_models)).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun screenIsUsableInTheUiProcess() {
        compose.onNodeWithText(string(R.string.settings_screen_voice_models)).assertIsDisplayed()
        // The download path is :ui-process only; if we landed anywhere else the
        // screen says so instead of offering buttons that cannot work.
        compose.onAllNodes(hasText(string(R.string.voice_models_unavailable)))
            .fetchSemanticsNodes()
            .let { assert(it.isEmpty()) { "models screen reports the wrong process" } }
    }

    @Test
    fun offersDownloadAndImportWhenNothingIsInstalled() {
        compose.onAllNodes(hasText(string(R.string.voice_models_download))).fetchSemanticsNodes()
            .let { assert(it.isNotEmpty()) { "no download action offered" } }
        compose.onNodeWithText(string(R.string.voice_models_import)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backReturnsToVoiceSettings() {
        Qa.device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.voice_inline_dictation)).performScrollTo().assertIsDisplayed()
    }
}
