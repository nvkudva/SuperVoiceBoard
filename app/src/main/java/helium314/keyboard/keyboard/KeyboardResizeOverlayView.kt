// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.settings.findIndexOfDefaultSetting
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.abs

/**
 * SuperVoiceBoard: interactive keyboard resize. Sits on top of the keyboard, catches all touches
 * and turns drags of its four handles into live changes of the very same size preferences the
 * appearance sliders write, so both stay in sync and the size survives the keyboard being closed.
 */
class KeyboardResizeOverlayView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    private val prefs = context.prefs()
    private var anchor: View? = null

    // drag state, valid between ACTION_DOWN and ACTION_UP of a single handle
    private var dragStart = 0f
    private var startScale = 0f
    private var baseSizePx = 1f
    private var lastWritten = 0f

    private val anchorLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSize() }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setupHandle(findViewById(R.id.resize_handle_top), Edge.TOP)
        setupHandle(findViewById(R.id.resize_handle_bottom), Edge.BOTTOM)
        setupHandle(findViewById(R.id.resize_handle_left), Edge.LEFT)
        setupHandle(findViewById(R.id.resize_handle_right), Edge.RIGHT)
        findViewById<View>(R.id.resize_done).setOnClickListener { hide() }
        findViewById<View>(R.id.resize_reset).setOnClickListener { reset() }
    }

    /** show the overlay covering [anchorView], which is expected to be the keyboard view wrapper */
    fun show(anchorView: View) {
        anchor?.removeOnLayoutChangeListener(anchorLayoutListener)
        anchor = anchorView
        anchorView.addOnLayoutChangeListener(anchorLayoutListener)
        updateSize()
        visibility = VISIBLE
        bringToFront()
    }

    fun hide() {
        anchor?.removeOnLayoutChangeListener(anchorLayoutListener)
        anchor = null
        visibility = GONE
    }

    val isResizing get() = visibility == VISIBLE

    private fun updateSize() {
        val height = anchor?.height ?: return
        val params = layoutParams ?: return
        if (height <= 0 || params.height == height) return
        params.height = height
        layoutParams = params
    }

    // the keyboard is under the overlay, so nothing below may act on touches that miss a handle
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = true

    private fun reset() {
        prefs.edit {
            remove(heightKey())
            remove(bottomPaddingKey())
            remove(sidePaddingKey())
        }
        KeyboardSwitcher.getInstance().reloadKeyboard()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHandle(handle: View, edge: Edge) {
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    startDrag(edge, event)
                }
                MotionEvent.ACTION_MOVE -> applyDrag(edge, event)
            }
            true
        }
    }

    private fun startDrag(edge: Edge, event: MotionEvent) {
        val sv = Settings.getValues()
        val keyboardHeight = ResourceUtils.getKeyboardHeight(resources, sv)
        when (edge) {
            Edge.TOP -> {
                startScale = sv.mKeyboardHeightScale
                // height the keyboard would have at scale 1, so a dragged pixel is a known fraction of it
                baseSizePx = keyboardHeight / startScale
                dragStart = event.rawY
            }
            Edge.BOTTOM -> {
                startScale = sv.mBottomPaddingScale
                baseSizePx = resources.getFraction(R.fraction.config_keyboard_bottom_padding_holo, keyboardHeight, keyboardHeight)
                dragStart = event.rawY
            }
            Edge.LEFT, Edge.RIGHT -> {
                startScale = sv.mSidePaddingScale
                baseSizePx = resources.getFraction(R.fraction.config_keyboard_left_padding, width, width)
                dragStart = event.rawX
            }
        }
        if (baseSizePx < 1f) baseSizePx = 1f
        lastWritten = startScale
    }

    private fun applyDrag(edge: Edge, event: MotionEvent) {
        val movedPx = when (edge) {
            Edge.TOP, Edge.BOTTOM -> dragStart - event.rawY // up is taller / more bottom padding
            Edge.LEFT -> event.rawX - dragStart // inwards is more side padding
            Edge.RIGHT -> dragStart - event.rawX
        }
        val key: String
        val range: ClosedFloatingPointRange<Float>
        when (edge) {
            Edge.TOP -> { key = heightKey(); range = HEIGHT_RANGE }
            Edge.BOTTOM -> { key = bottomPaddingKey(); range = BOTTOM_PADDING_RANGE }
            Edge.LEFT, Edge.RIGHT -> { key = sidePaddingKey(); range = SIDE_PADDING_RANGE }
        }
        val value = (startScale + movedPx / baseSizePx).coerceIn(range)
        if (abs(value - lastWritten) < STEP) return
        lastWritten = value
        prefs.edit { putFloat(key, value) }
        KeyboardSwitcher.getInstance().reloadKeyboard()
    }

    private val isLandscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun heightKey() = createPrefKeyForBooleanSettings(
        Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, findIndexOfDefaultSetting(isLandscape, FoldableUtils.isFolded), 2)

    private fun bottomPaddingKey() = createPrefKeyForBooleanSettings(
        Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, findIndexOfDefaultSetting(isLandscape, FoldableUtils.isFolded), 2)

    private fun sidePaddingKey() = createPrefKeyForBooleanSettings(
        Settings.PREF_SIDE_PADDING_SCALE_PREFIX,
        findIndexOfDefaultSetting(isLandscape, Settings.getValues().mIsSplitKeyboardEnabled, FoldableUtils.isFolded), 3)

    companion object {
        // same ranges the appearance sliders use, so a drag can never leave a value the sliders cannot show
        private val HEIGHT_RANGE = 0.3f..1.5f
        private val BOTTOM_PADDING_RANGE = 0f..5f
        private val SIDE_PADDING_RANGE = 0f..3f
        private const val STEP = 0.005f
    }
}
