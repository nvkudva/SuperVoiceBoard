// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.suggestions.SuggestionStripView

/**
 * WaveKey: the two keys the fork adds to the strip, drawn the same way anywhere
 * else they are named.
 *
 * The mic and the AI fix key wear a spectrum tile with a dark glyph on the
 * keyboard, and a settings row that calls the same thing by a different picture
 * makes the reader work out that they are the same thing. The gradient and the
 * glyph colour come from [SuggestionStripView] rather than being restated, so
 * the keyboard stays the single source of the palette.
 */
@Composable
fun SpectrumTile(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 36,
) {
    Box(
        modifier
            .size(size.dp)
            .background(
                Brush.horizontalGradient(SuggestionStripView.SPECTRUM.map { Color(it) }),
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription,
            Modifier.size((size * 0.56f).dp),
            tint = Color(SuggestionStripView.SPECTRUM_GLYPH),
        )
    }
}
