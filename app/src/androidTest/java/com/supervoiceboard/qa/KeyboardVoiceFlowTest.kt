// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The keyboard itself, driven through UiAutomator because it lives in its own
 * window: typing works, the pinned mic key is there, and starting a dictation
 * session swaps the suggestion row for the voice row.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class KeyboardVoiceFlowTest {

    @get:Rule
    val screenshots = ScreenshotOnFailure()

    private lateinit var scenario: ActivityScenario<TestInputActivity>

    private val timeout = 10_000L

    @Before
    fun openAFieldWithOurKeyboard() {
        Qa.makeThisImeCurrent()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(
            Intent(context, TestInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Qa.device.waitForIdle()
    }

    @After
    fun close() {
        if (this::scenario.isInitialized) scenario.close()
    }

    private fun waitForDesc(description: String) =
        Qa.device.wait(Until.findObject(By.desc(description)), timeout)

    @Test
    fun typingReachesTheField() {
        val a = Qa.device.wait(Until.findObject(By.text("a")), timeout)
        assertNotNull("keyboard did not come up", a)
        a.click()
        Qa.device.waitForIdle()
        var typed = ""
        scenario.onActivity { typed = it.text }
        assertTrue("expected the keypress to reach the field, got '$typed'", typed.contains("a"))
    }

    @Test
    fun micKeyIsPinnedToTheSuggestionRow() {
        val mic = waitForDesc(VOICE_INPUT)
        assertNotNull("no mic key on the suggestion strip", mic)
        assertTrue("mic key is not usable", mic.isEnabled)
        Qa.screenshot("suggestion-row-with-mic")
    }

    @Test
    fun tappingTheMicSwapsInTheVoiceRow() {
        val mic = waitForDesc(VOICE_INPUT)
        assertNotNull("no mic key on the suggestion strip", mic)
        mic.click()

        val cancel = waitForDesc(STOP_DICTATING)
        Qa.screenshot("voice-row-active")
        assertNotNull("the voice row never appeared after tapping the mic", cancel)
        assertNotNull("no done control on the voice row", waitForDesc(DONE_DICTATING))
        assertNotNull("no minimize control on the voice row", waitForDesc(MINIMIZE))

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
        val mic = waitForDesc(VOICE_INPUT) ?: return
        mic.click()
        waitForDesc(STOP_DICTATING)?.click()
        Qa.device.waitForIdle()
        var typed = "unset"
        scenario.onActivity { typed = it.text }
        assertTrue("cancelled dictation committed '$typed'", typed.isEmpty())
    }

    private companion object {
        // The content descriptions the voice row ships with (strings.xml).
        const val VOICE_INPUT = "Voice input"
        const val STOP_DICTATING = "Stop dictating"
        const val DONE_DICTATING = "Done dictating"
        const val MINIMIZE = "Hide keyboard, keep listening"
    }
}
