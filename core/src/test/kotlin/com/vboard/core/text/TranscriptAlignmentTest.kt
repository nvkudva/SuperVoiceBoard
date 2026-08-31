package com.vboard.core.text

import com.vboard.core.text.TranscriptAlignment.Op
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * W7.1 foundation. Nothing here is wired to the UI — the feature it exists for
 * is gated on a measurement that has not been made — so these tests are what
 * stands behind the claim that the foundation is correct.
 */
class TranscriptAlignmentTest {

    @Test
    fun `normalization ignores case, punctuation and spacing`() {
        assertEquals(
            "lets meet at the pub",
            TranscriptAlignment.normalize("  Let's meet, at   the pub!  ").replace("'", ""),
        )
        assertEquals(
            TranscriptAlignment.normalize("Hello, world."),
            TranscriptAlignment.normalize("hello world"),
        )
    }

    @Test
    fun `an apostrophe is part of the word`() {
        // "were" and "we're" are a real disagreement; dropping the apostrophe
        // would hide it.
        assertTrue(TranscriptAlignment.normalize("we're") != TranscriptAlignment.normalize("were"))
        assertEquals(TranscriptAlignment.normalize("don’t"), TranscriptAlignment.normalize("don't"))
    }

    @Test
    fun `identical transcripts align to matches only`() {
        val pairs = TranscriptAlignment.align("the cat sat", "The cat, sat!")
        assertEquals(3, pairs.size)
        assertTrue(pairs.all { it.op == Op.MATCH })
    }

    @Test
    fun `a substituted word is aligned as a substitution`() {
        val pairs = TranscriptAlignment.align("the cat sat", "the hat sat")
        assertEquals(listOf(Op.MATCH, Op.SUBSTITUTION, Op.MATCH), pairs.map { it.op })
        assertEquals("hat", pairs[1].hypothesis)
        assertEquals("cat", pairs[1].reference)
    }

    @Test
    fun `an extra word in the hypothesis is an insertion`() {
        val pairs = TranscriptAlignment.align("the cat sat", "the big cat sat")
        assertEquals(1, pairs.count { it.op == Op.INSERTION })
        assertEquals("big", pairs.first { it.op == Op.INSERTION }.hypothesis)
    }

    @Test
    fun `a missing word in the hypothesis is a deletion`() {
        val pairs = TranscriptAlignment.align("the big cat sat", "the cat sat")
        assertEquals(1, pairs.count { it.op == Op.DELETION })
        assertEquals("big", pairs.first { it.op == Op.DELETION }.reference)
    }

    @Test
    fun `empty transcripts align to nothing`() {
        assertEquals(emptyList(), TranscriptAlignment.align("", ""))
        assertEquals(3, TranscriptAlignment.align("", "one two three").size)
        assertTrue(TranscriptAlignment.align("", "one two three").all { it.op == Op.INSERTION })
    }

    @Test
    fun `scoring returns exactly the committed words, in order`() {
        val scored = TranscriptAlignment.score("the big cat sat", "the cat sat down")
        assertEquals(listOf("the", "cat", "sat", "down"), scored.map { it.word })
    }

    @Test
    fun `disagreement marks a word but never changes it`() {
        val scored = TranscriptAlignment.score("the cat sat", "the hat sat")
        assertEquals(listOf("the", "hat", "sat"), scored.map { it.word })
        assertEquals(listOf(true, false, true), scored.map { it.confident })
    }

    @Test
    fun `disagreement rate is zero when the models agree and one when they share nothing`() {
        assertEquals(0.0, TranscriptAlignment.disagreementRate("a b c", "A, b. c!"))
        assertEquals(1.0, TranscriptAlignment.disagreementRate("a b c", "x y z"))
        assertEquals(0.0, TranscriptAlignment.disagreementRate("", ""))
    }

    @Test
    fun `punctuation-only differences are never disagreement`() {
        // Two recognizers differing about a comma is not disagreement about
        // words, and treating it as such would make every sentence look shaky.
        assertEquals(
            0.0,
            TranscriptAlignment.disagreementRate(
                "so, we met at the pub — and then we left.",
                "So we met at the pub and then we left",
            ),
        )
    }
}
