// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The keyboard itself, driven entirely through UiAutomator: it lives in its own
 * window, and the field it types into lives in the test APK's own process, so
 * neither is reachable with Espresso or ActivityScenario.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardVoiceFlowTest {

    @get:Rule
    val screenshots = ScreenshotOnFailure()

    private val timeout = 15_000L

    @Before
    fun openAFieldWithOurKeyboard() {
        Qa.makeThisImeCurrent()
        // Whatever the previous case left on screen — a voice row, a dialog —
        // goes away before the field is asked for again.
        Qa.device.pressBack()
        Qa.shell("am force-stop ${Qa.testAppId}")
        Qa.shell("am start -W -n ${Qa.testAppId}/com.supervoiceboard.qa.TestInputActivity")
        Qa.device.waitForIdle()
        field().click()
        Qa.device.waitForIdle()
    }

    private fun field(): UiObject2 =
        Qa.device.wait(Until.findObject(By.clazz("android.widget.EditText")), timeout)
            ?: throw AssertionError("the QA input field never appeared")

    private fun typed(): String = field().text.orEmpty()

    private fun waitForDesc(description: String): UiObject2? =
        Qa.device.wait(Until.findObject(By.desc(description)), timeout)

    private fun requireDesc(description: String, why: String): UiObject2 =
        waitForDesc(description) ?: throw AssertionError(why)

    @Test
    fun typingReachesTheField() {
        // The field starts empty, so the layout comes up shifted: the key is "A".
        val a = Qa.device.wait(Until.findObject(By.text(Pattern.compile("[aA]"))), timeout)
            ?: throw AssertionError("keyboard did not come up")
        a.click()
        Qa.device.waitForIdle()
        val text = typed()
        assertTrue("expected the keypress to reach the field, got '$text'", text.contains("a"))
    }

    @Test
    fun micKeyIsPinnedToTheSuggestionRow() {
        val mic = requireDesc(VOICE_INPUT, "no mic key on the suggestion strip")
        assertTrue("mic key is not usable", mic.isEnabled)
        Qa.screenshot("suggestion-row-with-mic")
    }

    @Test
    fun tappingTheMicSwapsInTheVoiceRow() {
        requireDesc(VOICE_INPUT, "no mic key on the suggestion strip").click()

        val cancel = waitForDesc(STOP_DICTATING)
        Qa.screenshot("voice-row-active")
        cancel ?: throw AssertionError("the voice row never appeared after tapping the mic")
        // Done and minimize belong to the listening state only — VoiceStripView
        // hides them on error, which is where a device with no model installed
        // lands, so they are not asserted here.

        cancel.click()
        Qa.device.waitForIdle()
        assertNotNull(
            "cancelling dictation did not bring the suggestion row back",
            waitForDesc(VOICE_INPUT),
        )
    }

    /** Cancelling a session must not leave anything behind in the field. */
    @Test
    fun cancellingDictationCommitsNothing() {
        requireDesc(VOICE_INPUT, "no mic key on the suggestion strip").click()
        waitForDesc(STOP_DICTATING)?.click()
        Qa.device.waitForIdle()
        val text = typed()
        assertTrue("cancelled dictation committed '$text'", text.isEmpty())
    }

    private companion object {
        // The content descriptions the voice row ships with (strings.xml).
        const val VOICE_INPUT = "Voice input"
        const val STOP_DICTATING = "Stop dictating"
    }
}
