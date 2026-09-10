// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import kotlin.math.roundToInt

private val RAIL_HEIGHT = 26.dp
private val THUMB_WIDTH = 12.dp
private val THUMB_HEIGHT = 38.dp

/**
 * WaveKey: colour is chosen on rails, not on a wheel. A wheel puts hue and
 * saturation on one two-dimensional target that needs a fine drag and hides
 * the value under a thumb; four rails each show their whole range at once,
 * are draggable with a thumb, and read as one strip of the spectrum — the
 * same shape as the voice rail the rest of the app is built around.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    onDismissRequest: () -> Unit,
    initialColor: Int,
    title: String,
    showDefault: Boolean,
    onDefault: () -> Unit,
    onConfirmed: (Int) -> Unit,
) {
    val initialHsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initialColor) / 255f) }

    val currentColor = Color.hsv(hue, saturation, value, alpha)
    val opaqueColor = Color.hsv(hue, saturation, value)

    val initialString = initialColor.toUInt().toString(16)
    var textValue by remember { mutableStateOf(TextFieldValue(initialString, TextRange(initialString.length))) }

    fun syncText() {
        val hex = currentColor.toArgb().toUInt().toString(16)
        textValue = TextFieldValue(hex, TextRange(hex.length))
    }

    // WaveKey: a sheet, not a modal that covers everything. The keyboard being
    // recoloured is pinned at the top of the colour screen, and the point of
    // dragging a hue is watching it change there (docs/settings-ia.md).
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier
                                .width(28.dp)
                                .height(20.dp)
                                .background(currentColor, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                        if (showDefault)
                            TextButton(onClick = { onDismissRequest(); onDefault() }) {
                                Text(stringResource(R.string.undo))
                            }
                    }
                }
                ColorRail(
                    fraction = hue / 360f,
                    colors = List(7) { Color.hsv(it * 60f, saturation.coerceAtLeast(0.35f), value.coerceAtLeast(0.55f)) },
                ) { hue = it * 360f; syncText() }
                ColorRail(
                    fraction = saturation,
                    colors = listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)),
                ) { saturation = it; syncText() }
                ColorRail(
                    fraction = value,
                    colors = listOf(Color.hsv(hue, saturation, 0f), Color.hsv(hue, saturation, 1f)),
                ) { value = it; syncText() }
                ColorRail(
                    fraction = alpha,
                    colors = listOf(opaqueColor.copy(alpha = 0f), opaqueColor),
                ) { alpha = it; syncText() }
            }
            TextField(
                value = textValue,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password, // todo: KeyboardType.Password is a crappy way of avoiding suggestions... is there really no way in compose?
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDismissRequest(); onConfirmed(currentColor.toArgb()) }),
                onValueChange = {
                    textValue = it
                    val androidColor = runCatching { "#${it.text}".toColorInt() }.getOrNull()
                    if (androidColor != null) {
                        val hsv = FloatArray(3).also { c -> android.graphics.Color.colorToHSV(androidColor, c) }
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                        alpha = android.graphics.Color.alpha(androidColor) / 255f
                    }
                }
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    onDismissRequest()
                    onConfirmed(currentColor.toArgb())
                }) { Text(stringResource(android.R.string.ok)) }
            }
        }
    }
}

@Composable
private fun ColorRail(
    fraction: Float,
    colors: List<Color>,
    onChange: (Float) -> Unit,
) {
    var trackWidth by remember { mutableIntStateOf(0) }
    val thumbWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { THUMB_WIDTH.toPx() }
    // The thumb stays inside the track, so its travel is the track minus its own width.
    val travel = (trackWidth - thumbWidthPx).coerceAtLeast(1f)
    fun report(x: Float) = onChange(((x - thumbWidthPx / 2) / travel).coerceIn(0f, 1f))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(THUMB_HEIGHT)
            .onSizeChanged { trackWidth = it.width }
            .pointerInput(Unit) { detectTapGestures { report(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> report(change.position.x) } }
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(RAIL_HEIGHT)
                .align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(colors), CircleShape)
        )
        Spacer(
            Modifier
                .offset { IntOffset((fraction.coerceIn(0f, 1f) * travel).roundToInt(), 0) }
                .width(THUMB_WIDTH)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(50))
                .border(1.dp, Color(0x33000000), RoundedCornerShape(50))
        )
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        ColorPickerDialog({}, -0x0f4488aa, "Space bar", true, {}, {})
    }
}
