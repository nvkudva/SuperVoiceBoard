// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every switch on the Voice typing screen: present, toggleable, and it sticks. */
@RunWith(AndroidJUnit4::class)
class VoiceSettingsTest {

    @get:Rule(order = 0)
    val screenshots = ScreenshotOnFailure()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<SettingsActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @Before
    fun openVoiceScreen() {
        Qa.makeThisImeCurrent()
        compose.onNodeWithText(string(R.string.settings_screen_voice)).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun switchFor(id: Int) =
        compose.onNode(isToggleable() and hasAnyAncestor(hasText(string(id))))

    @Test
    fun everyVoiceSettingIsOnScreen() {
        listOf(
            R.string.settings_screen_voice_models,
            R.string.voice_inline_dictation,
            R.string.voice_silence_timeout,
            R.string.voice_raw_transcript,
            R.string.voice_remove_fillers,
            R.string.voice_self_corrections,
            R.string.voice_auto_punctuate,
            R.string.voice_auto_capitalize,
            R.string.voice_spoken_commands,
            R.string.voice_provisional_commit,
            R.string.voice_llm_refine,
            R.string.voice_telemetry,
        ).forEach { id ->
            compose.onNodeWithText(string(id)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun togglingASwitchFlipsItAndSurvivesLeavingTheScreen() {
        val label = R.string.voice_llm_refine // defaults off
        compose.onNodeWithText(string(label)).performScrollTo()
        switchFor(label).assertIsOff().performClick()
        compose.waitForIdle()
        switchFor(label).assertIsOn()

        Qa.device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.settings_screen_voice)).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(string(label)).performScrollTo()
        switchFor(label).assertIsOn().performClick() // leave the device as we found it
    }

    /**
     * Raw transcript means "commit exactly what was said", so the cleanup
     * switches it would override have to disappear while it is on.
     */
    @Test
    fun rawTranscriptHidesTheCleanupSwitches() {
        val raw = R.string.voice_raw_transcript
        compose.onNodeWithText(string(raw)).performScrollTo()
        switchFor(raw).assertIsOff().performClick()
        compose.waitForIdle()

        listOf(
            R.string.voice_remove_fillers,
            R.string.voice_self_corrections,
            R.string.voice_auto_punctuate,
            R.string.voice_auto_capitalize,
            R.string.voice_spoken_commands,
        ).forEach { id ->
            compose.onAllNodes(hasText(string(id))).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "${string(id)} still shown with raw transcript on" }
            }
        }

        compose.onNodeWithText(string(raw)).performScrollTo()
        switchFor(raw).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.voice_remove_fillers)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun silenceTimeoutOpensItsChoices() {
        compose.onNodeWithText(string(R.string.voice_silence_timeout)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.voice_silence_timeout_off)).assertIsDisplayed()
        Qa.device.pressBack()
    }
}
