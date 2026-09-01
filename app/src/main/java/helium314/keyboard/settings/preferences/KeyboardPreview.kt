// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SettingsActivity

private const val TAG = "KeyboardPreview"

/**
 * The real keyboard, drawn in the settings screen so appearance changes can be seen without
 * switching apps. [KeyboardView] is a plain View with a setKeyboard() — it needs no IME, no
 * action listener and handles no touches, so this is the drawing half of the keyboard only.
 */
@Composable
fun KeyboardPreview(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // KeyboardView reads Settings.getValues().mColors in its constructor, and the layout set
    // reads geometry at build time, so a pref change means a new view rather than an update.
    val prefChanged = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val density = LocalDensity.current
    val widthPx = with(density) { ctx.resources.displayMetrics.widthPixels }
    val heightPx = keyboardHeightPx(ctx)
    if (heightPx <= 0) return

    Box(modifier.fillMaxWidth().height(with(density) { heightPx.toDp() })) {
        key(prefChanged?.value) {
            AndroidView(
                factory = { buildPreview(it, widthPx) ?: View(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The keyboard plus the strip stacked above it. */
private fun keyboardHeightPx(context: Context): Int = runCatching {
    ResourceUtils.getKeyboardHeight(context.resources, Settings.getValues()) +
        context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
}.getOrDefault(0)

/** Mirrors KeyboardSwitcher.loadKeyboard, minus everything that needs a running IME. */
private fun buildPreview(context: Context, widthPx: Int): View? = runCatching {
    val themeContext = ContextThemeWrapper(context, KeyboardTheme.getKeyboardTheme(context).mStyleId)
    val values = Settings.getValues()
    val subtype = SubtypeSettings.getEnabledSubtypes(true).firstOrNull()
    val layoutSet = KeyboardLayoutSet.Builder(themeContext, null)
        .setKeyboardGeometry(widthPx, ResourceUtils.getKeyboardHeight(themeContext.resources, values))
        .setSubtype(RichInputMethodSubtype.get(subtype))
        .setVoiceInputKeyEnabled(values.mShowsVoiceInputKey)
        .setNumberRowEnabled(values.mShowsNumberRow)
        .setNumberRowInSymbolsEnabled(values.mShowsNumberRowInSymbols)
        .setLanguageSwitchKeyEnabled(values.isLanguageSwitchKeyEnabled)
        .setEmojiKeyEnabled(values.mShowsEmojiKey)
        .setSplitLayoutEnabled(values.mIsSplitKeyboardEnabled)
        .build()
    val keyboardView = KeyboardView(themeContext, null).apply {
        setKeyboard(layoutSet.getKeyboard(KeyboardElement.ALPHABET))
    }
    LinearLayout(themeContext).apply {
        orientation = LinearLayout.VERTICAL
        stripView(themeContext)?.let { addView(it) }
        addView(keyboardView)
        // a preview is for looking at: swallow every touch, so none of the strip's
        // keys can fire without the listener a running IME would have given them
        setOnTouchListener { _, _ -> true }
    }
}.onFailure {
    // a preview is never worth taking the settings screen down with it
    Log.w(TAG, "could not build the keyboard preview", it)
}.getOrNull()

/**
 * The suggestion strip as the keyboard's top row. It builds its own toolbar keys from
 * prefs in its init, so it needs no listener — and it is left in whatever mode the user
 * configured rather than forced open, so the preview shows the strip they will actually
 * get (in the default Expandable mode, the expand key and the pinned keys).
 */
private fun stripView(themeContext: Context): View? = runCatching {
    LayoutInflater.from(themeContext).inflate(R.layout.strip_container, null).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            themeContext.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        )
    }
}.onFailure {
    Log.w(TAG, "could not build the toolbar preview", it)
}.getOrNull()
