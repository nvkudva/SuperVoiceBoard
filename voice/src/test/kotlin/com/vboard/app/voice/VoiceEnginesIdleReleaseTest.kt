// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.voice

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The idle release is armed and disarmed through private state, so these read it
 * back by reflection rather than weakening what is asserted: the contract is
 * "a refused release re-arms the timer", and a timer is what has to be observed.
 */
class VoiceEnginesIdleReleaseTest {

    private fun field(name: String) =
        VoiceEngines.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun idleJob(): Job? = field("idleJob").get(VoiceEngines) as Job?

    private fun claims(): AtomicInteger = field("claims").get(VoiceEngines) as AtomicInteger

    @BeforeEach
    fun reset() {
        VoiceEngines.cancelIdleRelease()
        claims().set(0)
    }

    @Test
    fun `cancelIdleRelease leaves nothing armed`() {
        VoiceEngines.scheduleIdleRelease()
        assertNotNull(idleJob())
        VoiceEngines.cancelIdleRelease()
        assertNull(idleJob())
    }

    @Test
    fun `a release refused while the engines are in use re-arms the idle release`() {
        VoiceEngines.beginUse()
        VoiceEngines.releaseAll()

        val rearmed = assertNotNull(idleJob(), "refused release dropped the idle timer")
        assertTrue(rearmed.isActive)
    }

    @Test
    fun `a refused refiner release re-arms too`() {
        VoiceEngines.beginUse()
        VoiceEngines.releaseRefiner()

        assertTrue(assertNotNull(idleJob()).isActive)
    }

    @Test
    fun `a second release after the claim ends actually releases`() {
        VoiceEngines.beginUse()
        VoiceEngines.releaseAll()
        val refusedJob = assertNotNull(idleJob())

        VoiceEngines.endUse()
        assertEquals(0, claims().get())

        VoiceEngines.cancelIdleRelease()
        VoiceEngines.releaseAll()

        assertFalse(refusedJob.isActive)
        assertNull(idleJob(), "release went through, so nothing should be re-armed")
        assertFalse(VoiceEngines.isLoaded)
    }

    @Test
    fun `beginUse and endUse balance, and only the last endUse frees the engines`() {
        VoiceEngines.beginUse()
        VoiceEngines.beginUse()
        VoiceEngines.endUse()
        assertEquals(1, claims().get())

        VoiceEngines.cancelIdleRelease()
        VoiceEngines.releaseAll()
        assertNotNull(idleJob(), "one outstanding claim still refuses the release")

        VoiceEngines.endUse()
        assertEquals(0, claims().get())

        VoiceEngines.cancelIdleRelease()
        VoiceEngines.releaseAll()
        assertNull(idleJob())
    }
}
