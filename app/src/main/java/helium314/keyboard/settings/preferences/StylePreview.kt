// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.painterResourceCompat
import helium314.keyboard.latin.utils.previewDark

/** The keys worth showing: the ones that differ most between the three styles. */
private val PREVIEW_ICONS = listOf(
    KeyboardIconsSet.NAME_SHIFT_KEY,
    KeyboardIconsSet.NAME_DELETE_KEY,
    KeyboardIconsSet.NAME_ENTER_KEY,
    KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY,
    "settings",
)

/** Holo keys are square; the other two round their corners. */
private fun keyShape(style: String): Shape =
    if (style == KeyboardTheme.STYLE_HOLO) RectangleShape else RoundedCornerShape(8.dp)

/**
 * A strip of that style's own key icons, so the three options can be told apart in the
 * picker without applying one and looking at the keyboard.
 */
@Composable
fun StylePreview(style: String, modifier: Modifier = Modifier) {
    val ids = KeyboardIconsSet.iconIdsOfStyle(style)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PREVIEW_ICONS.forEach { name ->
            val id = ids[name] ?: return@forEach
            Box(
                Modifier
                    .size(30.dp)
                    .clip(keyShape(style))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResourceCompat(id, 30),
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewStyles() {
    Theme(previewDark) {
        Surface {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardTheme.STYLES.forEach { StylePreview(it) }
            }
        }
    }
}
