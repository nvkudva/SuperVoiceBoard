// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * WaveKey: the four rows that all answer "what does a key look like" — shape,
 * borders, icon set, icon names — behind the one row that asks it.
 */
@Composable
fun KeyStyleScreen(
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.wk_look_key_style),
        settings = listOf(
            Settings.PREF_THEME_STYLE,
            Settings.PREF_THEME_KEY_BORDERS,
            Settings.PREF_ICON_STYLE,
            Settings.PREF_CUSTOM_ICON_NAMES,
        )
    )
}
