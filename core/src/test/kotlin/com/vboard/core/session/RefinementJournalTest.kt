package com.vboard.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefinementJournalTest {

    private fun entry(spoken: String, refined: String? = null, accepted: Boolean = true) =
        RefinementJournal.Entry(1L, spoken, refined, accepted, null, 10L)

    @Test
    fun `newest entry comes back first`() {
        val journal = RefinementJournal()
        journal.record(entry("first"))
        journal.record(entry("second"))
        assertEquals(listOf("second", "first"), journal.entries().map { it.spoken })
    }

    @Test
    fun `the oldest entry falls off when capacity is reached`() {
        val journal = RefinementJournal(capacity = 2)
        journal.record(entry("a"))
        journal.record(entry("b"))
        journal.record(entry("c"))
        assertEquals(listOf("c", "b"), journal.entries().map { it.spoken })
    }

    @Test
    fun `a blank utterance is not a record`() {
        val journal = RefinementJournal()
        journal.record(entry("   "))
        assertEquals(0, journal.size)
    }

    @Test
    fun `a rejected refinement keeps what the model wanted to type`() {
        val journal = RefinementJournal()
        journal.record(
            RefinementJournal.Entry(1L, "call me", "Call me at 6pm.", false, "DROPPED_ENTITY", 5L),
        )
        val line = journal.export().lines()[1]
        assertTrue(line.contains("rejected"), line)
        assertTrue(line.contains("DROPPED_ENTITY"), line)
        assertTrue(line.contains("Call me at 6pm."), line)
    }

    @Test
    fun `a tab inside an utterance cannot split a row`() {
        val journal = RefinementJournal()
        journal.record(entry("one\ttwo\nthree"))
        assertEquals(2, journal.export().trim().lines().size)
    }

    @Test
    fun `clearing empties it`() {
        val journal = RefinementJournal()
        journal.record(entry("something"))
        journal.clear()
        assertEquals(0, journal.size)
    }
}
