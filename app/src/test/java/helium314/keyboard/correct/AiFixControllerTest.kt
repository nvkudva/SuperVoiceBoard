// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.correct

import android.content.Context
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import com.vboard.app.voice.VoiceRuntime
import com.vboard.core.correct.FixButtonState
import com.vboard.core.correct.TextFixer
import com.vboard.core.text.FieldKind
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * WaveKey: the AI-fix key is a switch (on -> off -> undo), so the things worth
 * proving are the ones a user would notice going wrong — text that cannot be
 * taken back, a rewrite that lands in the wrong field, or a run that quietly
 * dies on an empty field.
 *
 * The model never runs here: the runtime hands back no refiner path, so the
 * deterministic [TextFixer] pass is the whole fix and every expectation is
 * computed from it rather than hard-coded.
 */
@RunWith(RobolectricTestRunner::class)
class AiFixControllerTest {

    /** Runs coroutines only while the test says so, on the test thread. */
    private class PumpDispatcher : CoroutineDispatcher() {
        private val queue = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.put(block)
        }
        fun pump(ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                (queue.poll(5, TimeUnit.MILLISECONDS) ?: continue).run()
            }
        }
    }

    /** The editor's side of the conversation: text, selection, write count. */
    private class Field(var text: String) {
        var start = text.length
        var end = text.length
        var writes = 0
    }

    private class FakeSurface : FixSurface {
        var state: FixButtonState? = null
        var message: String? = null
        override fun updateFixButton(state: FixButtonState, contentDescription: String) {
            this.state = state
        }
        override fun showFixMessage(text: String) { message = text }
        override fun clearFixMessage() { message = null }
    }

    private class FakeHost(
        private val ic: () -> InputConnection?,
        private val kind: FieldKind = FieldKind.TEXT,
    ) : AiFixController.Host {
        var rewrites = 0
        override fun inputConnection(): InputConnection? = ic()
        override fun fieldKind(): FieldKind = kind
        override fun onBeforeFieldRewrite() { rewrites++ }
    }

    private lateinit var context: Context
    private lateinit var pump: PumpDispatcher
    private lateinit var surface: FakeSurface
    private lateinit var field: Field
    private lateinit var controller: AiFixController

    private val original = "hello there "
    private val fixed get() = TextFixer().rulesOnly(original)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pump = PumpDispatcher()
        surface = FakeSurface()
        field = Field(original)
    }

    /** Builds the controller over [field]; [extracted] false forces the fallback read. */
    private fun build(extracted: Boolean = true): AiFixController {
        val ic = connection(field, extracted)
        val host = FakeHost({ ic })
        val app = Mockito.mock(VoiceRuntime::class.java)
        return AiFixController(context, app, CoroutineScope(pump), host).also {
            controller = it
            it.attach(surface)
        }
    }

    private fun connection(f: Field, extracted: Boolean): InputConnection {
        val ic = Mockito.mock(InputConnection::class.java)
        Mockito.`when`(ic.beginBatchEdit()).thenReturn(true)
        Mockito.`when`(ic.endBatchEdit()).thenReturn(true)
        Mockito.`when`(ic.finishComposingText()).thenReturn(true)
        Mockito.`when`(ic.getExtractedText(Mockito.any(ExtractedTextRequest::class.java), anyInt()))
            .thenAnswer {
                if (!extracted) null else ExtractedText().apply {
                    text = f.text
                    startOffset = 0
                    selectionStart = f.start
                    selectionEnd = f.end
                }
            }
        Mockito.`when`(ic.getTextBeforeCursor(anyInt(), anyInt())).thenAnswer {
            val n = it.getArgument<Int>(0)
            f.text.substring(maxOf(0, f.start - n), f.start)
        }
        Mockito.`when`(ic.getTextAfterCursor(anyInt(), anyInt())).thenAnswer {
            val n = it.getArgument<Int>(0)
            f.text.substring(f.end, minOf(f.text.length, f.end + n))
        }
        Mockito.`when`(ic.setSelection(anyInt(), anyInt())).thenAnswer {
            f.start = it.getArgument(0); f.end = it.getArgument(1); true
        }
        Mockito.`when`(ic.deleteSurroundingText(anyInt(), anyInt())).thenAnswer {
            val before: Int = it.getArgument(0)
            val after: Int = it.getArgument(1)
            val from = maxOf(0, f.start - before)
            val to = minOf(f.text.length, f.end + after)
            f.text = f.text.substring(0, from) + f.text.substring(to)
            f.start = from; f.end = from; true
        }
        Mockito.`when`(ic.commitText(Mockito.any(CharSequence::class.java), anyInt())).thenAnswer {
            val s = it.getArgument<CharSequence>(0).toString()
            f.text = f.text.substring(0, f.start) + s + f.text.substring(f.end)
            f.start = f.start + s.length; f.end = f.start
            f.writes++
            true
        }
        return ic
    }

    private fun advance(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

    private fun string(id: Int) = context.getString(id)

    // ------------------------------------------------------------------ tests

    @Test fun `on then off then undo puts the original text back`() {
        build()
        controller.onFixKeyPressed()
        pump.pump(300)
        assertEquals(fixed, field.text)

        controller.onFixKeyPressed() // off, undo re-offered
        assertEquals(FixButtonState.UNDO, surface.state)

        controller.onFixKeyPressed() // undo
        assertEquals(original, field.text)
        assertEquals(string(R.string.ai_fix_undone), surface.message)
    }

    @Test fun `stopping re-records the undo after the window ran out mid-run`() {
        build()
        controller.onFixKeyPressed()
        pump.pump(300)
        advance(20_000) // the undo window is long gone

        controller.onFixKeyPressed() // off: the offer starts again here
        assertEquals(FixButtonState.UNDO, surface.state)

        controller.onFixKeyPressed()
        assertEquals(original, field.text)
    }

    @Test fun `a blank field keeps the run armed`() {
        build()
        controller.onFixKeyPressed()
        pump.pump(300)
        assertEquals(fixed, field.text)

        field.text = ""; field.start = 0; field.end = 0
        advance(500)
        controller.onUserEdit()
        pump.pump(500)
        assertEquals("", field.text)

        // Still on: the next word the user finishes is fixed without a press.
        field.text = "again here "; field.start = 11; field.end = 11
        advance(500)
        controller.onUserEdit()
        pump.pump(600)
        assertEquals(TextFixer().rulesOnly("again here "), field.text)
    }

    @Test fun `auto-fix waits for a word boundary and never repeats itself`() {
        build()
        controller.onFixKeyPressed()
        pump.pump(300)
        assertEquals(1, field.writes)

        // Unchanged text: nothing to do, and no second write.
        advance(500)
        controller.onUserEdit()
        pump.pump(500)
        assertEquals(1, field.writes)

        // Mid-word: still nothing.
        field.text = fixed + " more"; field.start = field.text.length; field.end = field.start
        advance(500)
        controller.onUserEdit()
        pump.pump(500)
        assertEquals(1, field.writes)

        // The space closes the word.
        field.text = fixed + " more "; field.start = field.text.length; field.end = field.start
        advance(500)
        controller.onUserEdit()
        pump.pump(600)
        assertEquals(2, field.writes)
    }

    @Test fun `a new field kills the run and discards a late result`() {
        build()
        controller.onFixKeyPressed() // queued, not yet run
        controller.onStartInput()
        pump.pump(400)

        assertEquals(original, field.text)
        assertEquals(0, field.writes)
        assertNotEquals(FixButtonState.UNDO, surface.state)

        // The run is dead: typing no longer fixes anything.
        advance(500)
        controller.onUserEdit()
        pump.pump(400)
        assertEquals(0, field.writes)
    }

    @Test fun `our own rewrite keeps the undo, a later edit drops it`() {
        build()
        controller.onFixKeyPressed()
        pump.pump(300)
        controller.onFixKeyPressed() // off, undo armed
        controller.onUserEdit() // same instant as our write: not the user
        controller.onFixKeyPressed()
        assertEquals(original, field.text)

        // Again, but with a real edit in between.
        field.text = original; field.start = original.length; field.end = field.start
        field.writes = 0
        controller.onFixKeyPressed()
        pump.pump(300)
        controller.onFixKeyPressed()
        advance(500)
        controller.onUserEdit()
        assertEquals(FixButtonState.IDLE, surface.state)
    }

    @Test fun `a result is dropped when the field changed under the run`() {
        build()
        controller.onFixKeyPressed()
        field.text = "something else "
        field.start = field.text.length; field.end = field.start
        pump.pump(400)

        assertEquals("something else ", field.text)
        assertEquals(0, field.writes)
        assertEquals(string(R.string.ai_fix_field_changed), surface.message)
    }

    @Test fun `undo refuses when the field no longer matches the fix`() {
        build()
        controller.onFixKeyPressed()
        pump.pump(300)
        controller.onFixKeyPressed() // off, undo armed

        field.text = "tampered with"
        field.start = field.text.length; field.end = field.start
        controller.onFixKeyPressed()

        assertEquals("tampered with", field.text)
        assertEquals(string(R.string.ai_fix_undo_stale), surface.message)
    }

    @Test fun `falls back to the cursor-relative read with the right offsets`() {
        field.start = 6; field.end = 6
        build(extracted = false)
        controller.onFixKeyPressed()
        pump.pump(300)
        assertEquals(fixed, field.text)

        controller.onFixKeyPressed()
        controller.onFixKeyPressed()
        assertEquals(original, field.text)
        assertTrue("selection restored", field.start == 6 && field.end == 6)
    }
}
