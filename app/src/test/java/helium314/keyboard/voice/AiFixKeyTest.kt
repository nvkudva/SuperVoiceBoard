// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.voice

import android.content.Context
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.LayerDrawable
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import com.vboard.app.voice.VoiceRuntime
import com.vboard.core.correct.FixButtonState
import com.vboard.core.text.FieldKind
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.ToolbarKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * WaveKey: the AI-fix key can be in the expanded row, pinned to the strip, or
 * both at once, so the thing worth proving is that every view wearing the
 * [ToolbarKey.AI_FIX] tag says the same thing — a key that still offers "fix"
 * while its twin offers "undo" is a key that loses text.
 */
@RunWith(RobolectricTestRunner::class)
class AiFixKeyTest {

    private lateinit var context: Context
    private lateinit var ime: LatinIME
    private lateinit var root: FrameLayout
    private lateinit var buttons: List<ImageButton>
    private lateinit var key: AiFixKey

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ime = Mockito.mock(LatinIME::class.java)
        Mockito.`when`(ime.resources).thenReturn(context.resources)
        Mockito.`when`(ime.getString(Mockito.anyInt())).thenReturn("")

        root = FrameLayout(context)
        val tagged = List(2) { ImageButton(context).apply { tag = ToolbarKey.AI_FIX } }
        val other = ImageButton(context).apply { tag = ToolbarKey.VOICE }
        val nested = FrameLayout(context).apply { addView(tagged[1]) }
        root.addView(tagged[0])
        root.addView(other)
        root.addView(nested)
        buttons = tagged

        key = AiFixKey(ime, Mockito.mock(VoiceRuntime::class.java)) { FieldKind.TEXT }
        key.setStripRoot(root)
    }

    private fun apply(state: FixButtonState) = key.updateFixButton(state, "desc-$state")

    private fun iconOf(button: ImageButton) = shadowOf(button.drawable).createdFromResId

    private fun tileOf(button: ImageButton): Int {
        val background = button.background
        val tile = if (background is LayerDrawable) background.getDrawable(0) else background
        return shadowOf(tile).createdFromResId
    }

    private fun borderOf(button: ImageButton): RunningBorderDrawable? {
        val background = button.background as? LayerDrawable ?: return null
        return (0 until background.numberOfLayers)
            .map { background.getDrawable(it) }
            .filterIsInstance<RunningBorderDrawable>()
            .firstOrNull()
    }

    @Test fun `disabled dims every tagged key and offers the fix glyph`() {
        apply(FixButtonState.DISABLED)
        for (button in buttons) {
            assertFalse(button.isEnabled)
            assertEquals(0.4f, button.alpha, 0f)
            assertEquals(R.drawable.ic_ai_fix, iconOf(button))
            assertEquals("desc-DISABLED", button.contentDescription)
            assertEquals(null, borderOf(button))
        }
    }

    @Test fun `idle is a live key at full strength`() {
        apply(FixButtonState.DISABLED)
        apply(FixButtonState.IDLE)
        for (button in buttons) {
            assertTrue(button.isEnabled)
            assertEquals(1f, button.alpha, 0f)
            assertEquals(R.drawable.ic_ai_fix, iconOf(button))
            assertEquals("desc-IDLE", button.contentDescription)
            assertEquals(null, borderOf(button))
        }
    }

    @Test fun `running keeps the fix glyph and adds the travelling border`() {
        apply(FixButtonState.RUNNING)
        for (button in buttons) {
            assertTrue(button.isEnabled)
            assertEquals(1f, button.alpha, 0f)
            assertEquals(R.drawable.ic_ai_fix, iconOf(button))
            assertNotNull("running border missing", borderOf(button))
            assertEquals(R.drawable.spectrum_tile, tileOf(button))
        }
    }

    @Test fun `undo swaps the glyph and drops the border again`() {
        apply(FixButtonState.RUNNING)
        apply(FixButtonState.UNDO)
        for (button in buttons) {
            assertTrue(button.isEnabled)
            assertEquals(1f, button.alpha, 0f)
            assertEquals(R.drawable.ic_ai_fix_undo, iconOf(button))
            assertEquals("desc-UNDO", button.contentDescription)
            assertEquals(null, borderOf(button))
        }
    }

    @Test fun `the spectrum tile and glyph colour are worn in every state`() {
        for (state in FixButtonState.entries) {
            apply(state)
            for (button in buttons) {
                assertEquals("tile in $state", R.drawable.spectrum_tile, tileOf(button))
                val filter = button.colorFilter as PorterDuffColorFilter
                assertEquals("glyph in $state", SuggestionStripView.SPECTRUM_GLYPH, shadowOf(filter).color)
            }
        }
    }

    @Test fun `an untagged key in the same tree is left alone`() {
        apply(FixButtonState.UNDO)
        val other = root.getChildAt(1) as ImageButton
        assertEquals(null, other.drawable)
        assertEquals(null, other.contentDescription)
    }

    @Test fun `destroy cancels the scope so no further work can run`() {
        val field = AiFixKey::class.java.getDeclaredField("scope").apply { isAccessible = true }
        val scope = field.get(key) as CoroutineScope
        assertFalse(scope.coroutineContext[Job]!!.isCancelled)

        key.destroy()

        assertTrue("scope still live after destroy", scope.coroutineContext[Job]!!.isCancelled)
    }
}
