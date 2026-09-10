// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * WaveKey: every finger movement that is not a key press, in one place.
 *
 * Swipe typing was under Typing while the space-bar swipes, the touchpad and
 * the delete swipe were under Toolbar & keys — two screens for one question.
 */
@Composable
fun GesturesScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val gestureEnabled = JniUtils.sHaveGestureLib && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT)
    val trailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, Defaults.PREF_GESTURE_PREVIEW_TRAIL)
    val floatingPreview = prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
    val items = listOf(
        R.string.settings_screen_swipe,
        Settings.PREF_GESTURE_INPUT,
        if (JniUtils.sHaveGestureLib) null else GLIDE_DECODER_NOTE,
        if (gestureEnabled) Settings.PREF_GESTURE_PREVIEW_TRAIL else null,
        if (gestureEnabled) Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT else null,
        if (gestureEnabled && floatingPreview) Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC else null,
        if (gestureEnabled) Settings.PREF_GESTURE_SPACE_AWARE else null,
        if (gestureEnabled) Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN else null,
        if (gestureEnabled && (trailEnabled || floatingPreview)) Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION else null,

        R.string.wk_cat_spacebar_delete,
        Settings.PREF_KEY_LONGPRESS_TIMEOUT,
        Settings.PREF_SPACE_HORIZONTAL_SWIPE,
        Settings.PREF_SPACE_VERTICAL_SWIPE,
        if (Settings.readHorizontalSpaceSwipe(prefs) == KeyboardActionListener.SwipeAction.SWITCH_LANGUAGE
            || Settings.readVerticalSpaceSwipe(prefs) == KeyboardActionListener.SwipeAction.SWITCH_LANGUAGE)
            Settings.PREF_LANGUAGE_SWIPE_DISTANCE else null,
        if (Settings.readVerticalSpaceSwipe(prefs) == KeyboardActionListener.SwipeAction.TOUCHPAD_MODE)
            Settings.PREF_TOUCHPAD_SENSITIVITY else null,
        if (Settings.readVerticalSpaceSwipe(prefs) == KeyboardActionListener.SwipeAction.TOUCHPAD_MODE)
            Settings.PREF_TOUCHPAD_EDGE_SCROLL else null,
        Settings.PREF_DELETE_SWIPE,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.wk_screen_gestures),
        settings = items,
    )
}
