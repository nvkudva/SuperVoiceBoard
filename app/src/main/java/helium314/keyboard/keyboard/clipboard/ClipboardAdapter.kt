// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.clipboard_entry_key, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setContent(getItem(position))
    }

    private fun getItem(position: Int) = clipboardHistoryManager?.getHistoryEntry(position)

    override fun getItemCount() = clipboardHistoryManager?.getHistorySize() ?: 0

    inner class ViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentTextView: TextView
        private val contentImageView: ImageView
        private val typeIconView: ImageView
        private val metaView: TextView
        private val meterView: View

        init {
            view.apply {
                setOnClickListener(this@ViewHolder)
                setOnTouchListener(this@ViewHolder)
                setOnLongClickListener(this@ViewHolder)
                setBackgroundResource(itemBackgroundId)
                isHapticFeedbackEnabled = false
            }
            Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
            // WaveKey: the pin is a tap target of its own now. Long-press on the
            // card still toggles it — that stays the power path — but the gesture
            // is no longer the only way to find the feature.
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                setOnClickListener {
                    clipboardHistoryManager?.toggleClipPinned(view.tag as Long)
                }
            }
            typeIconView = view.findViewById(R.id.clipboard_entry_type_icon)
            metaView = view.findViewById(R.id.clipboard_entry_meta)
            meterView = view.findViewById(R.id.clipboard_entry_meter)
            contentTextView = view.findViewById<TextView>(R.id.clipboard_entry_text_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            contentImageView = view.findViewById(R.id.clipboard_entry_image_content)
            clipboardLayoutParams.setItemProperties(view)
            val colors = Settings.getValues().mColors
            colors.setColor(typeIconView, ColorType.KEY_HINT_TEXT)
            metaView.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        }

        fun setContent(historyEntry: ClipboardHistoryEntry?) {
            if (historyEntry == null) return
            itemView.tag = historyEntry.id
            if (historyEntry.filename != null) {
                historyEntry.setImageAndDescription(contentImageView, contentTextView)
            } else {
                contentTextView.text = historyEntry.text?.take(1000) // truncate displayed text for performance reasons
            }
            contentImageView.visibility = if (historyEntry.filename != null) View.VISIBLE else View.GONE
            contentTextView.visibility = if (contentTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            val ctx = itemView.context
            val colors = Settings.getValues().mColors
            val kind = ClipKind.of(historyEntry)
            typeIconView.setImageResource(kind.icon)
            metaView.text = kind.meta(ctx, historyEntry)

            // Violet is the panel's word for permanent, and nothing else in it is
            // violet: a kept clip carries the accent on its pin and its edge.
            val accent = colors.get(ColorType.CLIPBOARD_PIN)
            pinnedIconView.setImageResource(
                if (historyEntry.isPinned) R.drawable.ic_clip_pin_filled else R.drawable.ic_clip_pin
            )
            pinnedIconView.setColorFilter(
                if (historyEntry.isPinned) accent else colors.get(ColorType.KEY_HINT_TEXT)
            )
            pinnedIconView.alpha = if (historyEntry.isPinned) 1f else 0.55f
            pinnedIconView.contentDescription = ctx.getString(
                if (historyEntry.isPinned) R.string.wk_clip_unpin else R.string.wk_clip_pin
            )

            // The meter drains toward the retention time. A pinned clip has none,
            // and that absence is what says the rest expire.
            val left = fractionLeft(historyEntry)
            if (historyEntry.isPinned || left == null) {
                meterView.visibility = View.INVISIBLE
            } else {
                meterView.visibility = View.VISIBLE
                meterView.background = MeterDrawable(left, accent)
            }
        }

        /** How much of the retention window this clip has left, or null if it never expires. */
        private fun fractionLeft(entry: ClipboardHistoryEntry): Float? {
            val minutes = Settings.getValues()?.mClipboardHistoryRetentionTime ?: return null
            if (minutes > 120) return null // "keep forever" in the settings
            val window = minutes * 60 * 1000f
            val spent = (System.currentTimeMillis() - entry.timeStamp).coerceAtLeast(0L)
            return ((window - spent) / window).coerceIn(0f, 1f)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                keyEventListener.onKeyDown(view.tag as Long)
            }
            return false
        }

        override fun onClick(view: View) {
            keyEventListener.onKeyUp(view.tag as Long)
        }

        override fun onLongClick(view: View): Boolean {
            clipboardHistoryManager?.toggleClipPinned(view.tag as Long)
            return true
        }
    }
}
