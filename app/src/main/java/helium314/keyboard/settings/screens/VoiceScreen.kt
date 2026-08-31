// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard: voice settings, inside HeliBoard's settings rather than
// carried over from VBoard's own settings app (W2.5). The keys and defaults are
// :voice's — see com.vboard.app.settings.SettingsRepository — so the screen and
// the dictation path cannot disagree about what a switch means.
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vboard.app.settings.SettingsRepository.Defaults as VoiceDefaults
import com.vboard.app.settings.SettingsRepository.Keys as VoiceKeys
import com.vboard.core.session.SilenceTimeout
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun VoiceScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    // Raw mode is the verbatim escape hatch: with it on, none of the cleanup
    // switches below do anything, so they are hidden rather than left lying.
    val raw = prefs.getBoolean(VoiceKeys.RAW_TRANSCRIPT, VoiceDefaults.RAW_TRANSCRIPT)
    val items = listOfNotNull(
        VoiceKeys.INLINE_DICTATION,
        VoiceKeys.SILENCE_TIMEOUT,
        VoiceKeys.RAW_TRANSCRIPT,
        if (raw) null else VoiceKeys.REMOVE_FILLERS,
        if (raw) null else VoiceKeys.AGGRESSIVE_FILLERS,
        if (raw) null else VoiceKeys.SELF_CORRECTIONS,
        if (raw) null else VoiceKeys.AUTO_PUNCTUATE,
        if (raw) null else VoiceKeys.AUTO_CAP,
        if (raw) null else VoiceKeys.SPOKEN_COMMANDS,
        VoiceKeys.LLM_REFINE,
        VoiceKeys.TELEMETRY,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_voice),
        settings = items
    )
}

fun createVoiceSettings(context: Context) = listOf(
    Setting(context, VoiceKeys.INLINE_DICTATION, R.string.voice_inline_dictation, R.string.voice_inline_dictation_summary) {
        SwitchPreference(it, VoiceDefaults.INLINE_DICTATION)
    },
    Setting(context, VoiceKeys.SILENCE_TIMEOUT, R.string.voice_silence_timeout, R.string.voice_silence_timeout_summary) { setting ->
        val ctx = LocalContext.current
        ListPreference(
            setting,
            items = SilenceTimeout.entries.map { timeout ->
                val label = when (timeout) {
                    SilenceTimeout.OFF -> ctx.getString(R.string.voice_silence_timeout_off)
                    else -> ctx.getString(
                        R.string.voice_silence_timeout_seconds,
                        (timeout.millis ?: 0L) / 1000L,
                    )
                }
                label to timeout.name
            },
            default = VoiceDefaults.SILENCE_TIMEOUT.name,
        )
    },
    Setting(context, VoiceKeys.RAW_TRANSCRIPT, R.string.voice_raw_transcript, R.string.voice_raw_transcript_summary) {
        SwitchPreference(it, VoiceDefaults.RAW_TRANSCRIPT)
    },
    Setting(context, VoiceKeys.REMOVE_FILLERS, R.string.voice_remove_fillers, R.string.voice_remove_fillers_summary) {
        SwitchPreference(it, VoiceDefaults.REMOVE_FILLERS)
    },
    Setting(context, VoiceKeys.AGGRESSIVE_FILLERS, R.string.voice_aggressive_fillers, R.string.voice_aggressive_fillers_summary) {
        SwitchPreference(it, VoiceDefaults.AGGRESSIVE_FILLERS)
    },
    Setting(context, VoiceKeys.SELF_CORRECTIONS, R.string.voice_self_corrections, R.string.voice_self_corrections_summary) {
        SwitchPreference(it, VoiceDefaults.SELF_CORRECTIONS)
    },
    Setting(context, VoiceKeys.AUTO_PUNCTUATE, R.string.voice_auto_punctuate, R.string.voice_auto_punctuate_summary) {
        SwitchPreference(it, VoiceDefaults.AUTO_PUNCTUATE)
    },
    Setting(context, VoiceKeys.AUTO_CAP, R.string.voice_auto_capitalize, R.string.voice_auto_capitalize_summary) {
        SwitchPreference(it, VoiceDefaults.AUTO_CAP)
    },
    Setting(context, VoiceKeys.SPOKEN_COMMANDS, R.string.voice_spoken_commands, R.string.voice_spoken_commands_summary) {
        SwitchPreference(it, VoiceDefaults.SPOKEN_COMMANDS)
    },
    Setting(context, VoiceKeys.LLM_REFINE, R.string.voice_llm_refine, R.string.voice_llm_refine_summary) {
        SwitchPreference(it, VoiceDefaults.LLM_REFINE)
    },
    Setting(context, VoiceKeys.TELEMETRY, R.string.voice_telemetry, R.string.voice_telemetry_summary) { setting ->
        val ctx = LocalContext.current
        // W7.3: whoever is asked to turn measurement on gets to see what it
        // measured. The numbers live in the running IME and vanish with it.
        val snapshot = LatinIME.getVoiceMetricsSnapshot()
        val rate = snapshot?.sendReadyRate
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = VoiceDefaults.TELEMETRY,
            description = if (rate == null) setting.description else ctx.getString(
                R.string.voice_telemetry_measured,
                (rate * 100).toInt(),
                snapshot.utterances,
                snapshot.meanTimeToSendReadyMs / 1000.0,
            ),
        )
    },
)

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            VoiceScreen { }
        }
    }
}
