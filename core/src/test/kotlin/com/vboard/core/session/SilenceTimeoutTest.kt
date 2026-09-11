package com.vboard.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SilenceTimeoutTest {

    /** How a stored setting string becomes a timeout: by enum name, else the default. */
    private fun fromStored(raw: String?) =
        SilenceTimeout.entries.firstOrNull { it.name == raw } ?: SilenceTimeout.DEFAULT

    @Test
    fun `every stored name maps back to its own entry`() {
        for (entry in SilenceTimeout.entries) {
            assertEquals(entry, fromStored(entry.name))
        }
    }

    @Test
    fun `nothing stored yet means the shipped default`() {
        assertEquals(SilenceTimeout.DEFAULT, fromStored(null))
    }

    @Test
    fun `a legacy or unknown stored value falls back to the default`() {
        assertEquals(SilenceTimeout.DEFAULT, fromStored("S30"))
        assertEquals(SilenceTimeout.DEFAULT, fromStored("s8"))
        assertEquals(SilenceTimeout.DEFAULT, fromStored(""))
    }

    @Test
    fun `off has no deadline at all`() {
        assertNull(SilenceTimeout.OFF.millis)
    }

    @Test
    fun `every other choice is a real deadline`() {
        for (entry in SilenceTimeout.entries - SilenceTimeout.OFF) {
            val millis = assertNotNull(entry.millis, "${entry.name} should have a deadline")
            assertTrue(millis > 0)
        }
    }

    @Test
    fun `the named seconds are the milliseconds`() {
        assertEquals(5_000L, SilenceTimeout.S5.millis)
        assertEquals(8_000L, SilenceTimeout.S8.millis)
        assertEquals(15_000L, SilenceTimeout.S15.millis)
    }

    @Test
    fun `the default is the tighter of the two specs, not thirty seconds`() {
        assertEquals(SilenceTimeout.S8, SilenceTimeout.DEFAULT)
    }
}
