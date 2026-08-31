// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.After
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
        // Never force-stop this package: the instrumentation runs inside it.
        repeat(3) {
            Qa.shell(
                "am start -W -f 0x14000000 " +
                    "-n ${Qa.testAppId}/com.supervoiceboard.qa.TestInputActivity"
            )
            Qa.device.waitForIdle()
            if (findField() != null) return@repeat
            Qa.device.pressBack()
        }
        field().click()
        Qa.device.waitForIdle()
    }

    @After
    fun putTheKeyboardAway() {
        Qa.hideKeyboard()
    }

    private fun findField(): UiObject2? =
        Qa.device.wait(Until.findObject(By.clazz("android.widget.EditText")), timeout)

    private fun field(): UiObject2 =
        findField() ?: throw AssertionError("the QA input field never appeared")

    private fun typed(): String = field().text.orEmpty()

    private fun waitForDesc(description: String): UiObject2? =
        Qa.device.wait(Until.findObject(By.desc(description)), timeout)

    private fun requireDesc(description: String, why: String): UiObject2 =
        waitForDesc(description) ?: throw AssertionError(why)

    /**
     * HeliBoard draws its keys onto a single canvas, so there is no per-key node
     * for UiAutomator to press. What can be checked is that focusing a field
     * brings our IME up rather than some other one.
     */
    @Test
    fun ourKeyboardComesUpForATextField() {
        val ime = Qa.shell("dumpsys input_method")
        assertTrue("the IME window is not showing:\n${ime.lineSequence().take(40).joinToString("\n")}",
            ime.contains("mInputShown=true"))
        assertTrue("a different IME is in charge", ime.contains(Qa.appId))
    }

    @Test
    fun micKeyIsPinnedToTheSuggestionRow() {
        val mic = requireDesc(VOICE_INPUT, "no mic key on the suggestion strip")
        assertTrue("mic key is not usable", mic.isEnabled)
        Qa.screenshot("suggestion-row-with-mic")
    }

    /**
     * Tapping the mic hands the suggestion row over to the voice row. On a
     * device with no speech model installed the session cannot start, so the
     * row comes up in its error state: a status line and the back control,
     * with done and minimize deliberately hidden.
     */
    @Test
    fun tappingTheMicSwapsInTheVoiceRow() {
        requireDesc(VOICE_INPUT, "no mic key on the suggestion strip").click()
        val cancel = waitForDesc(STOP_DICTATING)
        Qa.screenshot("voice-row-active")
        cancel ?: throw AssertionError("the voice row never appeared after tapping the mic")
        assertTrue("the voice row control is not usable", cancel.isEnabled)
    }

    /**
     * With nothing to dictate with, the row's control is repurposed into the
     * fix for the problem — it opens the voice settings, rather than dead-ending
     * (VoiceStripView, VoiceErrorAction.OPEN_DOWNLOAD).
     */
    @Test
    fun theErrorRowLeadsToTheVoiceSettings() {
        requireDesc(VOICE_INPUT, "no mic key on the suggestion strip").click()
        requireDesc(STOP_DICTATING, "the voice row never appeared").click()
        val settings = Qa.device.wait(
            Until.hasObject(By.textContains("Voice")),
            timeout,
        )
        Qa.screenshot("voice-row-error-action")
        assertTrue("the error row did not lead anywhere", settings)
    }

    /** Nothing is committed to the field by a session that never started. */
    @Test
    fun aFailedSessionCommitsNothing() {
        requireDesc(VOICE_INPUT, "no mic key on the suggestion strip").click()
        waitForDesc(STOP_DICTATING)
        val text = typed()
        assertTrue("a session that never started committed '$text'", text.isEmpty())
    }

    private companion object {
        // The content descriptions the voice row ships with (strings.xml).
        const val VOICE_INPUT = "Voice input"
        const val STOP_DICTATING = "Stop dictating"
    }
}
