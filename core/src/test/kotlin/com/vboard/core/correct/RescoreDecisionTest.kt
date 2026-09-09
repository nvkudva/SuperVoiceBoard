package com.vboard.core.correct

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RescoreDecisionTest {

    private val sentence = listOf(
        WordSlot("I", emptyList()),
        WordSlot("think", listOf("thing")),
        WordSlot("teh", listOf("the", "ten")),
        WordSlot("meeting", emptyList()),
        WordSlot("is", emptyList()),
        WordSlot("on", emptyList()),
        WordSlot("tuesday", listOf("Tuesday")),
    )

    private fun accept(rescored: String) = RescoreDecision.accept(sentence, rescored)

    @Test
    fun `a word the decoder offered is swapped`() {
        val out = accept("I think the meeting is on tuesday")
        assertEquals(listOf(Replacement(2, "teh", "the")), out)
    }

    @Test
    fun `two swaps in one sentence both land`() {
        val out = accept("I think the meeting is on Tuesday")
        assertEquals(
            listOf(Replacement(2, "teh", "the"), Replacement(6, "tuesday", "Tuesday")),
            out,
        )
    }

    @Test
    fun `a word the decoder never offered is refused`() {
        // "that" is a plausible correction and was not on the decoder's list.
        assertTrue(accept("I think that meeting is on tuesday").isEmpty())
    }

    @Test
    fun `a word invented for a slot with no alternatives is refused`() {
        assertTrue(accept("I think teh gathering is on tuesday").isEmpty())
    }

    @Test
    fun `an unchanged sentence asks for nothing`() {
        assertTrue(accept("I think teh meeting is on tuesday").isEmpty())
    }

    @Test
    fun `trailing punctuation on the model's word does not block the match`() {
        val out = accept("I think the meeting is on Tuesday.")
        assertEquals(2, out.size)
        assertEquals("Tuesday", out.last().to)
    }

    @Test
    fun `case is not ignored, because case is the correction`() {
        // "TUESDAY" is not what the decoder offered, so it is not applied.
        assertTrue(accept("I think teh meeting is on TUESDAY").isEmpty())
    }

    // ------------------------------------------------------------ alignment

    @Test
    fun `a reply that dropped a word is discarded whole`() {
        assertTrue(accept("I think the meeting is Tuesday").isEmpty())
    }

    @Test
    fun `a reply that added a word is discarded whole`() {
        assertTrue(accept("I think the big meeting is on Tuesday").isEmpty())
    }

    @Test
    fun `extra whitespace in the reply does not break alignment`() {
        val out = accept("  I   think the\tmeeting is on tuesday ")
        assertEquals(listOf(Replacement(2, "teh", "the")), out)
    }

    // --------------------------------------------------------------- limits

    @Test
    fun `a reply that rewrites too much is refused entirely`() {
        val slots = (1..5).map { WordSlot("w$it", listOf("x$it")) }
        assertTrue(RescoreDecision.accept(slots, "x1 x2 x3 x4 x5").isEmpty())
    }

    @Test
    fun `exactly the maximum number of swaps is still accepted`() {
        val slots = (1..4).map { WordSlot("w$it", listOf("x$it")) }
        val out = RescoreDecision.accept(slots, "x1 x2 x3 w4")
        assertEquals(RescoreDecision.MAX_REPLACEMENTS, out.size)
    }

    // ----------------------------------------------------------- empty input

    @Test
    fun `nothing recorded means nothing to do`() {
        assertTrue(RescoreDecision.accept(emptyList(), "anything at all").isEmpty())
    }

    @Test
    fun `a blank reply means nothing to do`() {
        assertTrue(accept("   ").isEmpty())
    }
}
