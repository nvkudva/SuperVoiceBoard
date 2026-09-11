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

/**
 * WaveKey: the clipboard, off the keys screen.
 *
 * It was a third group inside "Keys & feedback", which is a screen about what
 * the keys do — the clipboard is a store, not a key.
 */
@Composable
fun ClipboardScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val historyEnabled = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)
    val items = listOf(
        Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
        if (historyEnabled) Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME else null,
        if (historyEnabled) Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST else null,
        if (historyEnabled) Settings.PREF_CLIPBOARD_USE_FILES else null,
        if (historyEnabled && prefs.getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES))
            Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT else null,
        Settings.PREF_SUGGEST_CLIPBOARD_CONTENT,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.wk_screen_clipboard),
        settings = items,
    )
}
