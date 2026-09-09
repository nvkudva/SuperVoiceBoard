// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultClipboardToolbarPref
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.painterResourceCompat
import helium314.keyboard.settings.preferences.PreferenceCategory
import java.util.Locale

/**
 * WaveKey: one screen for choosing toolbar keys, replacing three near-identical
 * modal lists of 36 rows each (docs/settings-ia.md).
 *
 * The strip at the top is the thing being edited, in the order it will appear.
 * Tapping a key there selects it and offers the only three things anyone wants
 * — move it left, move it right, take it out — so the order is chosen by
 * looking at it rather than by dragging inside a scrolling dialog.
 */
@Composable
fun ToolbarKeysScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var target by remember { mutableStateOf(Target.MAIN) }
    var entries by remember(target) { mutableStateOf(readEntries(prefs, target)) }
    var selected by remember(target) { mutableStateOf<ToolbarKey?>(null) }

    fun write(new: List<Pair<ToolbarKey, Boolean>>) {
        entries = new
        prefs.edit {
            putString(target.pref, new.joinToString(Separators.ENTRY) { it.first.name + Separators.KV + it.second })
        }
    }

    val enabled = entries.filter { it.second }.map { it.first }
    val disabled = entries.filterNot { it.second }.map { it.first }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.wk_toolbar_keys_title),
        settings = emptyList(),
    ) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.wk_toolbar_keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // The strip, in order. This is the object being edited.
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .horizontalScroll(rememberScrollState())
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (enabled.isEmpty())
                    Text(
                        stringResource(R.string.wk_toolbar_keys_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                enabled.forEach { key ->
                    KeyChip(key, key == selected) { selected = if (selected == key) null else key }
                }
            }

            val current = selected
            if (current != null) {
                val index = enabled.indexOf(current)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        current.name.lowercase(Locale.US).getStringResourceOrName("", ctx),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    ActionChip(stringResource(R.string.wk_move_left), index > 0) {
                        write(swap(entries, current, enabled[index - 1]))
                    }
                    ActionChip(stringResource(R.string.wk_move_right), index < enabled.lastIndex) {
                        write(swap(entries, current, enabled[index + 1]))
                    }
                    ActionChip(stringResource(R.string.wk_remove), true) {
                        write(entries.map { if (it.first == current) it.first to false else it })
                        selected = null
                    }
                }
            }

            PreferenceCategory(stringResource(R.string.wk_toolbar_keys_available))
            // Tapping adds to the end of the strip, where the thumb already is.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    disabled.chunked(6).forEach { row ->
                        Row(
                            Modifier.padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { key ->
                                KeyChip(key, false) {
                                    write(entries.map { if (it.first == key) it.first to true else it })
                                }
                            }
                        }
                    }
                }
            }

            PreferenceCategory(stringResource(R.string.wk_toolbar_keys_which))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Target.entries.forEach { t ->
                    // The chosen strip reads as chosen, not as disabled.
                    SelectChip(stringResource(t.label), t == target) {
                        target = t
                        selected = null
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun KeyChip(key: ToolbarKey, selected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val iconId = KeyboardIconsSet.iconIdsOfStyle(
        ctx.prefs().getString(Settings.PREF_ICON_STYLE, Defaults.PREF_ICON_STYLE(ctx.prefs()))
    )[key.name.lowercase(Locale.US)]
    Box(
        Modifier.size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (iconId != null)
            Icon(
                painterResourceCompat(iconId, 30),
                key.name,
                Modifier.size(22.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        else
            Text(key.name.take(2), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class Target(val pref: String, val default: String, val label: Int) {
    MAIN(Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref, R.string.wk_target_main),
    PINNED(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref, R.string.wk_target_pinned),
    CLIPBOARD(Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref, R.string.wk_target_clipboard),
}

private fun readEntries(prefs: android.content.SharedPreferences, target: Target): List<Pair<ToolbarKey, Boolean>> =
    prefs.getString(target.pref, target.default)!!
        .split(Separators.ENTRY)
        .mapNotNull {
            val split = it.split(Separators.KV)
            val key = runCatching { ToolbarKey.valueOf(split.first()) }.getOrNull() ?: return@mapNotNull null
            key to (split.last() == "true")
        }

/** Swaps two keys' positions, keeping every other entry where it was. */
private fun swap(
    entries: List<Pair<ToolbarKey, Boolean>>,
    a: ToolbarKey,
    b: ToolbarKey,
): List<Pair<ToolbarKey, Boolean>> {
    val ia = entries.indexOfFirst { it.first == a }
    val ib = entries.indexOfFirst { it.first == b }
    if (ia < 0 || ib < 0) return entries
    return entries.toMutableList().also {
        val tmp = it[ia]
        it[ia] = it[ib]
        it[ib] = tmp
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface { ToolbarKeysScreen {} }
    }
}
