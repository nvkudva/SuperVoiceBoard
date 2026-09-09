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
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceGroup

/**
 * WaveKey: the Typing door (docs/settings-ia.md).
 *
 * Everything about turning finger movement into text, in one place: what the
 * keys do, what gets corrected, where words come from, and how swipe behaves.
 * Upstream scattered these across four root entries, two of them named after
 * the code rather than the task — "Preferences" and "Secondary layouts".
 */
@Composable
fun TypingScreen(
    onClickKeysAndFeedback: () -> Unit,
    onClickCorrections: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickSwipe: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_door_typing),
        settings = emptyList(),
    ) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .then(Modifier.padding(innerPadding))
                    .padding(bottom = 24.dp)
            ) {
                PreferenceGroup {
                    Preference(
                        name = stringResource(R.string.settings_screen_keys_feedback),
                        description = stringResource(R.string.settings_screen_keys_feedback_summary),
                        onClick = onClickKeysAndFeedback,
                        icon = R.drawable.ic_settings_preferences
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_correction),
                        description = stringResource(R.string.settings_screen_correction_summary),
                        onClick = onClickCorrections,
                        icon = R.drawable.ic_settings_correction
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.dictionary_settings_category),
                        description = stringResource(R.string.settings_screen_dictionaries_summary),
                        onClick = onClickDictionaries,
                        icon = R.drawable.ic_dictionary
                    ) { NextScreenIcon() }
                    // Swipe typing needs the proprietary library or the built-in
                    // decoder; without either there is nothing to configure.
                    if (JniUtils.sHaveGestureLib)
                        Preference(
                            name = stringResource(R.string.settings_screen_swipe),
                            onClick = onClickSwipe,
                            icon = R.drawable.ic_settings_gesture
                        ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_symbol_layouts),
                        onClick = onClickLayouts,
                        icon = R.drawable.ic_settings_layout
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
        Surface { TypingScreen({}, {}, {}, {}, {}, {}) }
    }
}
