// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.settings

import android.content.SharedPreferences
import com.vboard.core.session.SilenceTimeout
import com.vboard.core.text.CleanupOptions

/**
 * The voice layer's settings.
 *
 * VBoard's snapshot also carried the keyboard's own settings — theme, haptics,
 * key preview, autocorrect mode, number row, clipboard history. HeliBoard owns
 * every one of those, so carrying them here would give the app two disagreeing
 * sources of truth for the same switch. What is left is what the voice layer
 * alone decides (PLAN.md R7).
 */
data class SettingsSnapshot(
    /** Capitalization of dictated text; the keyboard's own auto-capitalize is separate. */
    val autoCapitalize: Boolean = true,
    /**
     * Dictate without leaving the keyboard: the mic key starts listening in
     * place, the keys stay live, and spoken words land at the cursor.
     */
    val inlineDictation: Boolean = true,
    val removeFillers: Boolean = true,
    val aggressiveFillers: Boolean = false,
    val resolveSelfCorrections: Boolean = true,
    val autoPunctuate: Boolean = true,
    val spokenCommands: Boolean = true,
    val rawTranscriptMode: Boolean = false,
    val llmRefineEnabled: Boolean = false,
    /**
     * How long the mic may stay open with nothing being said. See
     * [SilenceTimeout]: the shipped default is 8s.
     */
    val silenceTimeout: SilenceTimeout = SilenceTimeout.DEFAULT,
    /**
     * W7.3: opt-in, content-free measurement of how often dictated text is sent
     * without editing, and how long that takes. Off unless the user turns it on,
     * and it never leaves the device — see PLAN.md R24.
     */
    val telemetryEnabled: Boolean = false,
) {
    fun cleanupOptions(): CleanupOptions =
        if (rawTranscriptMode) {
            CleanupOptions.RAW
        } else {
            CleanupOptions(
                removeFillers = removeFillers,
                aggressiveFillers = aggressiveFillers,
                resolveSelfCorrections = resolveSelfCorrections,
                collapseRepetitions = true,
                autoPunctuate = autoPunctuate,
                autoCapitalize = autoCapitalize,
                spokenCommands = spokenCommands,
            )
        }
}

/**
 * Voice settings, on the keyboard's own SharedPreferences.
 *
 * VBoard kept these in a DataStore of its own. Here they share HeliBoard's
 * preference file: the settings UI is HeliBoard's (W2.5) and its preference
 * composables read and write SharedPreferences, so a second store would mean two
 * places to look for the same switch and a synchronous read on the dictation
 * path replaced by a suspending one.
 */
class SettingsRepository(private val prefs: SharedPreferences) {

    object Keys {
        const val AUTO_CAP = "voice_auto_capitalize"
        const val INLINE_DICTATION = "voice_inline_dictation"
        const val REMOVE_FILLERS = "voice_remove_fillers"
        const val AGGRESSIVE_FILLERS = "voice_aggressive_fillers"
        const val SELF_CORRECTIONS = "voice_self_corrections"
        const val AUTO_PUNCTUATE = "voice_auto_punctuate"
        const val SPOKEN_COMMANDS = "voice_spoken_commands"
        const val RAW_TRANSCRIPT = "voice_raw_transcript"
        const val LLM_REFINE = "voice_llm_refine"
        const val SILENCE_TIMEOUT = "voice_silence_timeout"
        const val TELEMETRY = "voice_telemetry"
    }

    object Defaults {
        const val AUTO_CAP = true
        const val INLINE_DICTATION = true
        const val REMOVE_FILLERS = true
        const val AGGRESSIVE_FILLERS = false
        const val SELF_CORRECTIONS = true
        const val AUTO_PUNCTUATE = true
        const val SPOKEN_COMMANDS = true
        const val RAW_TRANSCRIPT = false
        const val LLM_REFINE = false
        val SILENCE_TIMEOUT: SilenceTimeout = SilenceTimeout.DEFAULT
        const val TELEMETRY = false
    }

    /** Read on the dictation path; cheap, and never blocks on IO after first load. */
    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        autoCapitalize = prefs.getBoolean(Keys.AUTO_CAP, Defaults.AUTO_CAP),
        inlineDictation = prefs.getBoolean(Keys.INLINE_DICTATION, Defaults.INLINE_DICTATION),
        removeFillers = prefs.getBoolean(Keys.REMOVE_FILLERS, Defaults.REMOVE_FILLERS),
        aggressiveFillers = prefs.getBoolean(Keys.AGGRESSIVE_FILLERS, Defaults.AGGRESSIVE_FILLERS),
        resolveSelfCorrections = prefs.getBoolean(Keys.SELF_CORRECTIONS, Defaults.SELF_CORRECTIONS),
        autoPunctuate = prefs.getBoolean(Keys.AUTO_PUNCTUATE, Defaults.AUTO_PUNCTUATE),
        spokenCommands = prefs.getBoolean(Keys.SPOKEN_COMMANDS, Defaults.SPOKEN_COMMANDS),
        rawTranscriptMode = prefs.getBoolean(Keys.RAW_TRANSCRIPT, Defaults.RAW_TRANSCRIPT),
        llmRefineEnabled = prefs.getBoolean(Keys.LLM_REFINE, Defaults.LLM_REFINE),
        silenceTimeout = silenceTimeout(),
        telemetryEnabled = prefs.getBoolean(Keys.TELEMETRY, Defaults.TELEMETRY),
    )

    private fun silenceTimeout(): SilenceTimeout {
        val raw = prefs.getString(Keys.SILENCE_TIMEOUT, null) ?: return Defaults.SILENCE_TIMEOUT
        return SilenceTimeout.entries.firstOrNull { it.name == raw } ?: Defaults.SILENCE_TIMEOUT
    }
}
