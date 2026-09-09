// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceGroup
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

@Composable
/**
 * WaveKey: six doors (docs/settings-ia.md).
 *
 * Upstream had eleven rows here, three of which — Preferences, Advanced,
 * Secondary layouts — were where a setting went when it fit nowhere else. These
 * six are named after what the user came to do, and every one carries what it
 * currently holds, so the right door is picked without opening any.
 */
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTyping: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickVoice: () -> Unit,
    onClickPrivacyAdvanced: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .then(Modifier.padding(innerPadding))
                    .padding(bottom = 24.dp)
            ) {
                PreferenceGroup {
                    Preference(
                        name = stringResource(R.string.language_and_layouts_title),
                        description = enabledSubtypes.joinToString(", ") { it.displayName() },
                        onClick = onClickLanguage,
                        icon = R.drawable.ic_settings_languages
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_door_typing),
                        description = stringResource(R.string.settings_door_typing_summary),
                        onClick = onClickTyping,
                        icon = R.drawable.ic_settings_preferences
                    ) { NextScreenIcon() }
                    // The differentiator sits in the top group, not under Advanced.
                    Preference(
                        name = stringResource(R.string.settings_screen_voice),
                        description = stringResource(R.string.settings_door_voice_summary),
                        onClick = onClickVoice,
                        icon = R.drawable.ic_settings_voice
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_door_look),
                        description = stringResource(R.string.settings_door_look_summary),
                        onClick = onClickAppearance,
                        icon = R.drawable.ic_settings_appearance
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_door_toolbar),
                        description = stringResource(R.string.settings_door_toolbar_summary),
                        onClick = onClickToolbar,
                        icon = R.drawable.ic_settings_toolbar
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_door_privacy),
                        description = stringResource(R.string.settings_door_privacy_summary),
                        onClick = onClickPrivacyAdvanced,
                        icon = R.drawable.ic_settings_advanced
                    ) { NextScreenIcon() }
                }

                PreferenceGroup {
                    // Upstream's research collection, which deletes itself two
                    // weeks after the gathering phase ends.
                    if (JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS)
                        Preference(
                            name = stringResource(R.string.gesture_data_screen),
                            onClick = onClickDataGathering,
                            icon = R.drawable.ic_settings_gesture
                        ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_about),
                        onClick = onClickAbout,
                        icon = R.drawable.ic_settings_about
                    ) { NextScreenIcon() }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
