// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.settings.SettingsSections
import helium314.keyboard.settings.preferences.PreferenceCategory
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.GestureDataGatheringSettings.filterBackgroundGatheringToolbarKeys
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.dialogs.ToolbarKeysCustomizer
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.ReorderSwitchPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.utils.previewDark

@Composable
fun ToolbarScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val toolbarMode = Settings.readToolbarMode(prefs)
    // Three sections: pick the toolbar, choose what sits on it, then tune how it
    // behaves. Every entry below the first section is mode-dependent, and a section
    // whose entries are all null is dropped along with its header.
    val items = listOf(
        if (toolbarMode == ToolbarMode.HIDDEN) Settings.PREF_TOOLBAR_HIDING_GLOBAL else null,
        if (toolbarMode != ToolbarMode.HIDDEN) Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE else null,

        R.string.settings_category_toolbar_keys,
        // WaveKey: one editor for all three strips, in place of three modal
        // lists of 36 rows (docs/settings-ia.md).
        SettingsWithoutKey.TOOLBAR_KEYS_EDITOR,

        R.string.settings_category_toolbar_behavior,
        if (toolbarMode == ToolbarMode.EXPANDABLE) Settings.PREF_QUICK_PIN_TOOLBAR_KEYS else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE) Settings.PREF_AUTO_SHOW_TOOLBAR else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE) Settings.PREF_AUTO_HIDE_TOOLBAR else null,
        if (toolbarMode != ToolbarMode.HIDDEN) Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD else null,
        if (toolbarMode != ToolbarMode.HIDDEN) Settings.PREF_VARIABLE_TOOLBAR_DIRECTION else null,

    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_door_toolbar),
        settings = emptyList(),
        content = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
            ) {
                PreferenceCategory(stringResource(R.string.toolbar_mode))
                ToolbarModeTiles(toolbarMode)
                SettingsSections(items)
            }
        }
    )
}

/**
 * WaveKey: the toolbar mode, chosen from four pictures of the strip rather than
 * from a list of four names. What the modes differ in is what the strip looks
 * like, which a list cannot show (docs/ux-review.md).
 */
@Composable
private fun ToolbarModeTiles(current: ToolbarMode) {
    val prefs = LocalContext.current.prefs()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ToolbarMode.entries.forEach { mode ->
            val selected = mode == current
            Surface(
                onClick = {
                    prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, mode.name) }
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    StripSketch(mode)
                    Text(
                        mode.name.lowercase().getStringResourceOrName("toolbar_mode_", LocalContext.current),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        stringResource(when (mode) {
                            ToolbarMode.EXPANDABLE -> R.string.wk_mode_expandable_summary
                            ToolbarMode.TOOLBAR_KEYS -> R.string.wk_mode_toolbar_keys_summary
                            ToolbarMode.SUGGESTION_STRIP -> R.string.wk_mode_suggestion_strip_summary
                            ToolbarMode.HIDDEN -> R.string.wk_mode_hidden_summary
                        }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** What that mode's strip looks like: words, keys, both, or nothing. */
@Composable
private fun StripSketch(mode: ToolbarMode) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (mode) {
            ToolbarMode.EXPANDABLE -> {
                repeat(3) { Word(ink, Modifier.weight(1f)) }
                Blip(ink)
            }
            ToolbarMode.TOOLBAR_KEYS -> repeat(7) { Blip(ink) }
            ToolbarMode.SUGGESTION_STRIP -> repeat(3) { Word(ink, Modifier.weight(1f)) }
            ToolbarMode.HIDDEN -> Text(
                stringResource(R.string.wk_mode_hidden_summary),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun Word(ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(ink.copy(alpha = 0.45f)))
}

@Composable
private fun Blip(ink: Color) {
    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(ink.copy(alpha = 0.3f)))
}

fun createToolbarSettings(context: Context) = listOf(
    Setting(context, SettingsWithoutKey.TOOLBAR_KEYS_EDITOR, R.string.wk_toolbar_keys_title) {
        Preference(
            name = stringResource(R.string.wk_toolbar_keys_title),
            onClick = { SettingsDestination.navigateTo(SettingsDestination.ToolbarKeys) },
        ) { NextScreenIcon() }
    },
    Setting(context, Settings.PREF_TOOLBAR_MODE, R.string.toolbar_mode) { setting ->
        val ctx = LocalContext.current
        val items =
            ToolbarMode.entries.map { it.name.lowercase().getStringResourceOrName("toolbar_mode_", ctx) to it.name }
        ListPreference(
            setting,
            items,
            Defaults.PREF_TOOLBAR_MODE
        ) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_TOOLBAR_HIDING_GLOBAL, R.string.toolbar_hiding_global) {
        SwitchPreference(it, Defaults.PREF_TOOLBAR_HIDING_GLOBAL) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE, R.string.toolbar_swipe_down_to_hide, R.string.toolbar_swipe_down_to_hide_summary) {
        SwitchPreference(it, Defaults.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE)
    },
    Setting(context, Settings.PREF_TOOLBAR_KEYS, R.string.toolbar_keys) {
        val keys = Defaults.PREF_TOOLBAR_KEYS.filterBackgroundGatheringToolbarKeys(LocalContext.current.prefs())
        ReorderSwitchPreference(it, keys)
    },
    Setting(context, Settings.PREF_PINNED_TOOLBAR_KEYS, R.string.pinned_toolbar_keys) {
        val keys = Defaults.PREF_PINNED_TOOLBAR_KEYS.filterBackgroundGatheringToolbarKeys(LocalContext.current.prefs())
        ReorderSwitchPreference(it, keys)
    },
    Setting(context, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, R.string.clipboard_toolbar_keys) {
        val keys = Defaults.PREF_CLIPBOARD_TOOLBAR_KEYS.filterBackgroundGatheringToolbarKeys(LocalContext.current.prefs())
        ReorderSwitchPreference(it, keys)
    },
    Setting(context, Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, R.string.customize_toolbar_key_codes) {
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = it.title,
            onClick = { showDialog = true },
        )
        if (showDialog)
            ToolbarKeysCustomizer(
                key = it.key,
                onDismissRequest = { showDialog = false }
            )
    },
    Setting(context, Settings.PREF_QUICK_PIN_TOOLBAR_KEYS,
        R.string.quick_pin_toolbar_keys, R.string.quick_pin_toolbar_keys_summary)
    {
        SwitchPreference(it, Defaults.PREF_QUICK_PIN_TOOLBAR_KEYS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_AUTO_SHOW_TOOLBAR, R.string.auto_show_toolbar, R.string.auto_show_toolbar_summary)
    {
        SwitchPreference(it, Defaults.PREF_AUTO_SHOW_TOOLBAR)
    },
    Setting(context, Settings.PREF_AUTO_HIDE_TOOLBAR, R.string.auto_hide_toolbar, R.string.auto_hide_toolbar_summary)
    {
        SwitchPreference(it, Defaults.PREF_AUTO_HIDE_TOOLBAR)
    },
    Setting(context, Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD,
        R.string.toolbar_only_with_hw_keyboard, R.string.toolbar_only_with_hw_keyboard_summary)
    {
        SwitchPreference(it, Defaults.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload() // necessary for updating insets
        }
    },
    Setting(context, Settings.PREF_VARIABLE_TOOLBAR_DIRECTION,
        R.string.var_toolbar_direction, R.string.var_toolbar_direction_summary)
    {
        SwitchPreference(it, Defaults.PREF_VARIABLE_TOOLBAR_DIRECTION)
    }
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            ToolbarScreen { }
        }
    }
}
