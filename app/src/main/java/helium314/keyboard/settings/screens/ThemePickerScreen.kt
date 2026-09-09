// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeleteButton
import helium314.keyboard.latin.utils.EditButton
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.contentTextDirectionStyle
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.LoadThemeDialog
import helium314.keyboard.settings.dialogs.themeFilePicker
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.PreferenceCategory

/**
 * Gboard-style theme page: every available theme as a small mock of the keyboard drawn in that
 * theme's own colors, grouped by brightness, with the applied one checked.
 */
@Composable
fun ThemePickerScreen(
    isNight: Boolean,
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")

    val prefKey = if (isNight) Settings.PREF_THEME_COLORS_NIGHT else Settings.PREF_THEME_COLORS
    val default = if (isNight) Defaults.PREF_THEME_COLORS_NIGHT else Defaults.PREF_THEME_COLORS
    val targetScreen = if (isNight) SettingsDestination.ColorsNight else SettingsDestination.Colors
    val selected = prefs.getString(prefKey, default)!!

    // Both of these read the whole preference store — prefs.all copies every
    // entry, user themes included — so they are keyed to the same triggers as
    // the palette map below rather than re-run on every recomposition.
    val defaultThemes = remember(b?.value, isNight) {
        KeyboardTheme.getAvailableDefaultColors(prefs, isNight)
    }
    val userThemes = remember(b?.value) {
        // prefs.all is null in preview only
        (prefs.all ?: mapOf(Settings.PREF_USER_COLORS_PREFIX + "usercolor" to "")).keys.mapNotNull {
            when {
                it.startsWith(Settings.PREF_USER_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_COLORS_PREFIX)
                it.startsWith(Settings.PREF_USER_ALL_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_ALL_COLORS_PREFIX)
                it.startsWith(Settings.PREF_USER_MORE_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_MORE_COLORS_PREFIX)
                else -> null
            }
        }.toSortedSet()
    }
    if (selected !in defaultThemes)
        userThemes.add(selected) // there are cases where we have no settings for a user theme

    val palettes = remember(b?.value, isNight, defaultThemes, userThemes) {
        (defaultThemes + userThemes).associateWith { paletteOf(ctx, it, isNight) }
    }
    val (light, dark) = defaultThemes.partition { palettes[it].isLight() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    val loadFilePicker = themeFilePicker()

    @Composable
    fun Tile(name: String, modifier: Modifier) = ThemeTile(
        name = name,
        colors = palettes[name],
        isSelected = name == selected,
        isUser = name in userThemes,
        prefKey = prefKey,
        targetScreen = targetScreen,
        modifier = modifier
    )

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(if (isNight) R.string.theme_page_title_night else R.string.theme_page_title)) },
        menu = listOf(stringResource(R.string.load) to { showLoadDialog = true }),
        filteredItems = { search ->
            (userThemes + defaultThemes).filter { it.contains(search, true) || it.displayName(ctx).contains(search, true) }
        },
        itemContent = { Tile(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).width(120.dp)) },
        content = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                fun section(title: Int, names: List<String>) {
                    if (names.isEmpty()) return
                    item(span = { GridItemSpan(maxLineSpan) }) { PreferenceCategory(stringResource(title)) }
                    items(names) { Tile(it, Modifier) }
                }
                section(R.string.theme_page_section_light, light)
                section(R.string.theme_page_section_dark, dark)
                item(span = { GridItemSpan(maxLineSpan) }) { PreferenceCategory(stringResource(R.string.theme_page_section_user)) }
                items(userThemes.toList()) { Tile(it, Modifier) }
                item { AddThemeTile { showAddDialog = true } }
            }
        }
    )
    if (showAddDialog)
        AddThemeDialog({ showAddDialog = false }, userThemes, targetScreen, prefKey)
    if (showLoadDialog)
        LoadThemeDialog({ showLoadDialog = false }, loadFilePicker)
}

@Composable
private fun ThemeTile(
    name: String,
    colors: Colors?,
    isSelected: Boolean,
    isUser: Boolean,
    prefKey: String,
    targetScreen: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var showDeleteDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.clickable {
            prefs.edit { putString(prefKey, name) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            KeyboardMock(
                colors,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    )
            )
            if (isSelected)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_setup_check),
                        stringResource(R.string.theme_page_selected),
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
        }
        Text(
            text = name.displayName(ctx),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (isUser)
            Row {
                EditButton { SettingsDestination.navigateTo(targetScreen + name) }
                DeleteButton { showDeleteDialog = true }
            }
    }
    if (showDeleteDialog)
        ConfirmationDialog(
            onDismissRequest = { showDeleteDialog = false },
            content = { Text(stringResource(R.string.delete_confirmation, name)) },
            onConfirmed = {
                showDeleteDialog = false
                prefs.edit {
                    remove(Settings.PREF_USER_COLORS_PREFIX + name)
                    remove(Settings.PREF_USER_ALL_COLORS_PREFIX + name)
                    remove(Settings.PREF_USER_MORE_COLORS_PREFIX + name)
                    if (isSelected) remove(prefKey)
                }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        )
}

@Composable
private fun AddThemeTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.add), Modifier.size(28.dp))
        }
        Text(
            text = stringResource(R.string.theme_page_new),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun AddThemeDialog(onDismissRequest: () -> Unit, userThemes: Collection<String>, targetScreen: String, prefKey: String) {
    val prefs = LocalContext.current.prefs()
    val defaultName = KeyboardTheme.getUnusedThemeName(stringResource(R.string.theme_name_user), prefs)
    var text by remember { mutableStateOf("") }
    val name = text.ifEmpty { defaultName }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.theme_page_new)) },
        content = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(defaultName) },
                textStyle = contentTextDirectionStyle,
            )
        },
        checkOk = { name.isNotBlank() && name !in userThemes },
        onConfirmed = {
            prefs.edit { putString(prefKey, name) }
            KeyboardTheme.writeUserMoreColors(prefs, name, Defaults.PREF_USER_MORE_COLORS) // write sth so theme is stored
            SettingsDestination.navigateTo(targetScreen + name)
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    )
}

/** A miniature key grid in [colors], standing in for the real keyboard, which needs an IME to build. */
@Composable
private fun KeyboardMock(colors: Colors?, modifier: Modifier = Modifier) {
    if (colors == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        return
    }
    fun c(type: ColorType) = Color(colors.get(type))
    val shape: Shape = if (colors.themeStyle == KeyboardTheme.STYLE_HOLO) RectangleShape else RoundedCornerShape(2.dp)

    Box(modifier.background(c(ColorType.MAIN_BACKGROUND))) {
        Column(Modifier.fillMaxSize().padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.weight(1f).fillMaxWidth().background(c(ColorType.STRIP_BACKGROUND)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(Modifier.weight(1f).height(2.dp).background(c(ColorType.SUGGESTED_WORD)))
                }
            }
            repeat(3) { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // bottom letter row is flanked by shift and delete
                    if (row == 2) MockKey(1.4f, c(ColorType.FUNCTIONAL_KEY_BACKGROUND), shape)
                    repeat(if (row == 2) 5 else 7) { MockKey(1f, c(ColorType.KEY_BACKGROUND), shape) }
                    if (row == 2) MockKey(1.4f, c(ColorType.FUNCTIONAL_KEY_BACKGROUND), shape)
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MockKey(1.4f, c(ColorType.FUNCTIONAL_KEY_BACKGROUND), shape)
                MockKey(4f, c(ColorType.SPACE_BAR_BACKGROUND), shape)
                MockKey(1.4f, c(ColorType.ACTION_KEY_BACKGROUND), shape)
            }
        }
    }
}

@Composable
private fun RowScope.MockKey(weight: Float, color: Color, shape: Shape) {
    Box(Modifier.weight(weight).fillMaxHeight().clip(shape).background(color))
}

private fun String.displayName(ctx: Context) = getStringResourceOrName("theme_name_", ctx)

private fun Colors?.isLight() = this != null &&
        ColorUtils.calculateLuminance(this.get(ColorType.MAIN_BACKGROUND)) > 0.5

/**
 * Resolves the palette of a theme other than the applied one. [KeyboardTheme] only ever builds the
 * current theme's colors, so the override the color editor uses for live preview is borrowed here.
 */
private fun paletteOf(ctx: Context, themeName: String, isNight: Boolean): Colors? {
    val previousTheme = SettingsActivity.forceTheme
    val previousNight = SettingsActivity.forceNight
    SettingsActivity.forceTheme = themeName
    SettingsActivity.forceNight = isNight
    val colors = runCatching { KeyboardTheme.getColorsForCurrentTheme(ctx) }.getOrNull()
    SettingsActivity.forceTheme = previousTheme
    SettingsActivity.forceNight = previousNight
    return colors
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            ThemePickerScreen(isNight = false) { }
        }
    }
}
