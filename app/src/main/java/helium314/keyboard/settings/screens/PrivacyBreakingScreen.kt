// SPDX-License-Identifier: GPL-3.0-only
//
// WaveKey. New file: the two features that break the fork's
// on-device-only promise live here, behind their own switches and nowhere else.
// Both default to off; nothing leaves the device until one is turned on.
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import helium314.keyboard.settings.preferences.SwitchPreference

object PrivacyBreakingSettings {
    /** Dictation goes to Google's recognizer instead of the on-device models. */
    const val PREF_GOOGLE_VOICE = "pref_google_voice_typing"
    const val DEFAULT_GOOGLE_VOICE = false

    /** Offer Google Password Manager (inline autofill) fills on login fields. */
    const val PREF_GOOGLE_PASSWORD_MANAGER = "pref_google_password_manager"
    const val DEFAULT_GOOGLE_PASSWORD_MANAGER = false

    fun googleVoiceEnabled(prefs: SharedPreferences) =
        prefs.getBoolean(PREF_GOOGLE_VOICE, DEFAULT_GOOGLE_VOICE)

    fun passwordManagerEnabled(prefs: SharedPreferences) =
        prefs.getBoolean(PREF_GOOGLE_PASSWORD_MANAGER, DEFAULT_GOOGLE_PASSWORD_MANAGER)
}

fun createPrivacyBreakingSettings(context: Context) = listOf(
    Setting(
        context, PrivacyBreakingSettings.PREF_GOOGLE_VOICE,
        R.string.privacy_breaking_google_voice, R.string.privacy_breaking_google_voice_summary,
    ) {
        SwitchPreference(it, PrivacyBreakingSettings.DEFAULT_GOOGLE_VOICE)
    },
    Setting(
        context, PrivacyBreakingSettings.PREF_GOOGLE_PASSWORD_MANAGER,
        R.string.privacy_breaking_password_manager, R.string.privacy_breaking_password_manager_summary,
    ) {
        SwitchPreference(it, PrivacyBreakingSettings.DEFAULT_GOOGLE_PASSWORD_MANAGER)
    },
)
