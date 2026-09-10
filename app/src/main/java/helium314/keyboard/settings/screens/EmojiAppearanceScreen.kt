// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsWithoutKey

/**
 * WaveKey: the emoji settings, off Look & feel. They configure a different
 * keyboard from the one the rest of that screen draws.
 */
@Composable
fun EmojiAppearanceScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.wk_look_emoji),
        settings = listOf(
            SettingsWithoutKey.CUSTOM_EMOJI_FONT,
            Settings.PREF_EMOJI_FONT_SCALE,
            if (prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE) != 1f)
                Settings.PREF_EMOJI_KEY_FIT else null,
            if (prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0) >= 24)
                Settings.PREF_EMOJI_SKIN_TONE else null,
            // Emoji settings used to sit on four different screens.
            Settings.PREF_EMOJI_MAX_SDK,
            Settings.PREF_SHOW_EMOJI_DESCRIPTIONS,
            Settings.PREF_SUGGEST_EMOJIS,
            Settings.PREF_INLINE_EMOJI_SEARCH,
        )
    )
}
