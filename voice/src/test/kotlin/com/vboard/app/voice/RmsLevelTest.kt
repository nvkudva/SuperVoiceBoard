// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.voice

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The level meter is the only speech signal left in the session — the streaming
 * recognizer that used to report speech is gone, so the silence timeout and the
 * endpoint both read this number. A level that never rises is a session that
 * ends while the user is still talking.
 */
class RmsLevelTest {

    @Test
    fun `an empty chunk is silent`() {
        assertEquals(0f, FloatArray(0).rmsLevel())
    }

    @Test
    fun `digital silence is silent`() {
        assertEquals(0f, FloatArray(512).rmsLevel())
    }

    @Test
    fun `a quiet room stays under the speech threshold`() {
        // SPEECH_LEVEL in the session is 0.02; room tone must not cross it.
        val level = FloatArray(512) { 0.001f }.rmsLevel()
        assertTrue(level < 0.02f, "room tone read as speech: $level")
    }

    @Test
    fun `ordinary speech lands in the middle of the range`() {
        // Speech RMS sits around 0.02..0.2, which the 8x scale maps to 0.16..1.
        val level = FloatArray(512) { 0.05f }.rmsLevel()
        assertTrue(level > 0.2f && level < 0.6f, "speech read as $level")
    }

    @Test
    fun `a loud chunk clamps at one`() {
        assertEquals(1f, FloatArray(512) { 1f }.rmsLevel())
    }

    @Test
    fun `the sign of the sample does not matter`() {
        val positive = FloatArray(256) { 0.05f }.rmsLevel()
        val alternating = FloatArray(256) { if (it % 2 == 0) 0.05f else -0.05f }.rmsLevel()
        assertTrue(abs(positive - alternating) < 1e-6f)
    }

    @Test
    fun `louder audio never reads quieter`() {
        var previous = -1f
        for (amplitude in listOf(0f, 0.005f, 0.01f, 0.05f, 0.1f, 0.3f, 1f)) {
            val level = FloatArray(256) { amplitude }.rmsLevel()
            assertTrue(level >= previous, "level fell at amplitude $amplitude")
            previous = level
        }
    }

    @Test
    fun `a short burst in a quiet chunk does not peg the meter`() {
        // The meter is an average, not a peak: one loud sample in 512 is a click,
        // not somebody speaking, and it must not hold the session open.
        val samples = FloatArray(512).also { it[0] = 1f }
        assertTrue(samples.rmsLevel() < 0.4f, "a single click read as ${samples.rmsLevel()}")
    }
}
