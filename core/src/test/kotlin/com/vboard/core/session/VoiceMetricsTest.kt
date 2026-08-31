package com.vboard.core.session

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceMetricsTest {

    @Test
    fun `nothing dictated means no rate to report`() {
        val snapshot = VoiceMetrics().snapshot()
        assertEquals(0, snapshot.utterances)
        assertNull(snapshot.sendReadyRate)
        assertEquals(0L, snapshot.meanTimeToSendReadyMs)
    }

    @Test
    fun `send-ready rate counts unedited utterances`() {
        val metrics = VoiceMetrics()
        metrics.record(edited = false, elapsedMs = 1_000)
        metrics.record(edited = true, elapsedMs = 3_000)
        metrics.record(edited = false, elapsedMs = 2_000)
        val snapshot = metrics.snapshot()
        assertEquals(3, snapshot.utterances)
        assertEquals(2, snapshot.sentUnedited)
        assertEquals(2.0 / 3.0, snapshot.sendReadyRate!!)
        assertEquals(2_000L, snapshot.meanTimeToSendReadyMs)
    }

    @Test
    fun `a negative duration is not recorded at all`() {
        // A clock that went backwards must not silently corrupt the mean, and
        // must not inflate the utterance count either.
        val metrics = VoiceMetrics()
        metrics.record(edited = false, elapsedMs = -1)
        assertEquals(0, metrics.snapshot().utterances)
    }

    @Test
    fun `turning it off drops what was collected`() {
        val metrics = VoiceMetrics()
        metrics.record(edited = false, elapsedMs = 1_000)
        metrics.clear()
        val snapshot = metrics.snapshot()
        assertEquals(0, snapshot.utterances)
        assertNull(snapshot.sendReadyRate)
    }
}
