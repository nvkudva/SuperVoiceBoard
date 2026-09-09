package com.vboard.core.correct

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentenceCandidatesTest {

    private fun candidates() = SentenceCandidates()

    // ------------------------------------------------------ sentence shape

    @Test
    fun `a word mid-sentence returns nothing`() {
        val c = candidates()
        assertNull(c.record("the", listOf("thr"), " "))
        assertEquals(1, c.size)
    }

    @Test
    fun `a terminator returns the sentence and clears the buffer`() {
        val c = candidates()
        c.record("teh", listOf("the"), " ")
        val sentence = c.record("cat", listOf("car"), ".")
        assertEquals(listOf("teh", "cat"), sentence?.map { it.committed })
        assertEquals(0, c.size)
    }

    @Test
    fun `a separator carrying a terminator ends the sentence`() {
        val c = candidates()
        c.record("done", listOf("dine"), " ")
        assertTrue(SentenceCandidates.endsSentence(".\n"))
        // Both words belong to the sentence the "!" closed.
        assertEquals(2, c.record("now", listOf("not"), "! ")?.size)
    }

    @Test
    fun `terminators outside Latin end a sentence too`() {
        for (end in listOf("।", "。", "？", "…")) {
            val c = candidates()
            assertEquals(1, c.record("word", listOf("word2"), end)?.size, "'$end' should end a sentence")
        }
    }

    // ------------------------------------------------- what is worth scoring

    @Test
    fun `a sentence with no alternatives is not worth scoring`() {
        val c = candidates()
        c.record("the", emptyList(), " ")
        assertNull(c.record("cat", emptyList(), "."))
        assertEquals(1, c.stats().sentencesSeen)
        assertEquals(0, c.stats().sentencesWorthScoring)
    }

    @Test
    fun `the committed word is never offered back as its own alternative`() {
        val c = candidates()
        val sentence = c.record("cat", listOf("cat", "car", "cat"), ".")!!
        assertEquals(listOf("car"), sentence.single().alternatives)
    }

    @Test
    fun `duplicate and empty alternatives are dropped`() {
        val c = candidates()
        val sentence = c.record("cat", listOf("car", "", "car", "cot"), ".")!!
        assertEquals(listOf("car", "cot"), sentence.single().alternatives)
    }

    @Test
    fun `alternatives are capped and keep the decoder's order`() {
        val c = SentenceCandidates(maxAlternatives = 2)
        val sentence = c.record("a", listOf("b", "c", "d"), ".")!!
        assertEquals(listOf("b", "c"), sentence.single().alternatives)
    }

    @Test
    fun `a word with no alternatives is not ambiguous`() {
        assertFalse(WordSlot("cat", emptyList()).isAmbiguous)
        assertTrue(WordSlot("cat", listOf("car")).isAmbiguous)
    }

    // ------------------------------------------------------------- limits

    @Test
    fun `a sentence past the word limit is abandoned, not truncated`() {
        val c = SentenceCandidates(maxWords = 3)
        repeat(5) { c.record("word$it", listOf("word"), " ") }
        assertNull(c.record("end", listOf("and"), "."))
        // It still counts as a sentence seen, so the rate is not flattered.
        assertEquals(1, c.stats().sentencesSeen)
        assertEquals(0, c.stats().sentencesWorthScoring)
    }

    @Test
    fun `an empty word is recorded as nothing`() {
        val c = candidates()
        c.record("", listOf("something"), " ")
        assertEquals(0, c.size)
        assertNull(c.record("", emptyList(), "."))
    }

    // -------------------------------------------------------------- reset

    @Test
    fun `reset abandons the sentence in progress`() {
        val c = candidates()
        c.record("half", listOf("calf"), " ")
        c.reset()
        val sentence = c.record("whole", listOf("hole"), ".")
        assertEquals(listOf("whole"), sentence?.map { it.committed })
    }

    @Test
    fun `reset clears an overflow so the next sentence is scored`() {
        val c = SentenceCandidates(maxWords = 1)
        c.record("one", listOf("won"), " ")
        c.record("two", listOf("too"), " ")
        c.reset()
        assertEquals(1, c.record("three", listOf("tree"), ".")?.size)
    }

    // --------------------------------------------------------------- stats

    @Test
    fun `stats count sentences and ambiguous slots, never words`() {
        val c = candidates()
        // Worth scoring: two ambiguous slots.
        c.record("teh", listOf("the"), " ")
        c.record("cta", listOf("cat"), ".")
        // Not worth scoring: nothing to choose between.
        c.record("ok", emptyList(), ".")

        val stats = c.stats()
        assertEquals(2, stats.sentencesSeen)
        assertEquals(1, stats.sentencesWorthScoring)
        assertEquals(2, stats.ambiguousSlots)
        assertEquals(0.5, stats.worthScoringRate)
    }

    @Test
    fun `the rate is zero rather than undefined before anything is typed`() {
        assertEquals(0.0, candidates().stats().worthScoringRate)
    }
}
