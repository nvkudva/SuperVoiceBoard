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
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceGroup

/**
 * WaveKey: the Privacy & advanced door (docs/settings-ia.md).
 *
 * The two questions that belong together and were three root rows apart: what
 * this keyboard sends anywhere, and the long tail of settings for people who
 * came looking for one.
 */
@Composable
fun PrivacyAdvancedScreen(
    onClickPrivacyBreaking: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_door_privacy),
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
                        name = stringResource(R.string.settings_screen_privacy_breaking),
                        description = stringResource(R.string.privacy_breaking_summary),
                        onClick = onClickPrivacyBreaking,
                        icon = R.drawable.ic_settings_advanced
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_advanced),
                        description = stringResource(R.string.settings_screen_advanced_summary),
                        onClick = onClickAdvanced,
                        icon = R.drawable.ic_settings_advanced
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
        Surface { PrivacyAdvancedScreen({}, {}, {}) }
    }
}
