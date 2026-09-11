package com.vboard.core.session

/**
 * An opt-in, on-device record of what the speaker said and what the refiner
 * made of it, kept so the prompt and the model can be judged on real
 * utterances rather than on invented test sentences.
 *
 * This is deliberately at odds with [VoiceMetrics], which cannot hold text by
 * construction, and with the rule that the refiner never logs field content
 * (VB-901). Both stand: this journal is a separate, named exception the user
 * switches on, it never leaves the device on its own, and [clear] empties it.
 * Nothing calls [record] unless the setting is on.
 */
class RefinementJournal(private val capacity: Int = DEFAULT_CAPACITY) {

    /**
     * One refinement. [accepted] is false when the validator rejected the
     * model's answer, in which case [refined] is what it *would* have typed —
     * the rejections are the interesting half of the data.
     */
    data class Entry(
        val atMillis: Long,
        val spoken: String,
        val refined: String?,
        val accepted: Boolean,
        val reason: String?,
        val elapsedMs: Long,
    )

    private val entries = ArrayDeque<Entry>()

    val size: Int get() = synchronized(entries) { entries.size }

    fun record(entry: Entry) {
        if (entry.spoken.isBlank()) return
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
        }
    }

    /** Newest first, which is the order anyone reading it wants. */
    fun entries(): List<Entry> = synchronized(entries) { entries.reversed() }

    fun clear() = synchronized(entries) { entries.clear() }

    /**
     * Tab-separated, one refinement per line, newest first. Tabs and newlines
     * inside an utterance become spaces so a line is always one record.
     */
    fun export(): String = buildString {
        appendLine("at\telapsed_ms\tverdict\treason\tspoken\trefined")
        for (e in entries()) {
            append(e.atMillis).append('\t')
            append(e.elapsedMs).append('\t')
            append(if (e.accepted) "accepted" else "rejected").append('\t')
            append(e.reason.orEmpty().flat()).append('\t')
            append(e.spoken.flat()).append('\t')
            appendLine(e.refined.orEmpty().flat())
        }
    }

    private fun String.flat(): String = replace('\t', ' ').replace('\n', ' ').trim()

    companion object {
        /** Enough to see a pattern, small enough that it is not an archive. */
        const val DEFAULT_CAPACITY = 200
    }
}
