package com.vboard.core.session

/**
 * The two numbers the product is actually judged on (W7.3).
 *
 * **send-ready rate** — the fraction of dictated utterances the user sent
 * without editing them first. It is the honest measure of whether the cleanup
 * pipeline works: a transcript the user has to fix is a transcript that failed,
 * however good its word error rate looked.
 *
 * **time-to-send-ready** — how long from pressing the mic to having text the
 * user did not touch again.
 *
 * Content-free by construction. This type cannot hold text: there is nowhere to
 * put it. Everything here is a count or a duration, and the aggregate is the
 * only thing that ever leaves this object — which is why [Snapshot] carries no
 * per-utterance list either, since a sequence of durations is a fingerprint of a
 * session in a way a mean is not.
 *
 * Opt-in is enforced above this: nothing calls it unless the user turned it on.
 */
class VoiceMetrics {

    /** Aggregates only — no per-utterance records, ever. */
    data class Snapshot(
        val utterances: Int,
        val sentUnedited: Int,
        val meanTimeToSendReadyMs: Long,
    ) {
        /** 0..1, or null when nothing has been dictated yet. */
        val sendReadyRate: Double?
            get() = if (utterances == 0) null else sentUnedited.toDouble() / utterances
    }

    private var utterances = 0
    private var sentUnedited = 0
    private var totalTimeMs = 0L

    /**
     * One finished utterance.
     *
     * [edited] is whether the user changed the text afterwards; [elapsedMs] is
     * mic press to committed text. Anything else about the utterance — what was
     * said, how long it was, which app it went to — is deliberately not a
     * parameter, so it cannot be recorded by accident later.
     */
    fun record(edited: Boolean, elapsedMs: Long) {
        if (elapsedMs < 0) return
        utterances++
        if (!edited) sentUnedited++
        totalTimeMs += elapsedMs
    }

    fun snapshot(): Snapshot = Snapshot(
        utterances = utterances,
        sentUnedited = sentUnedited,
        meanTimeToSendReadyMs = if (utterances == 0) 0L else totalTimeMs / utterances,
    )

    /** Called when the user turns telemetry off: what was collected is dropped. */
    fun clear() {
        utterances = 0
        sentUnedited = 0
        totalTimeMs = 0L
    }
}
