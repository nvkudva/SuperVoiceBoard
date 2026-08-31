// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/** A single focused EditText — the smallest thing that makes the IME appear. */
class TestInputActivity : Activity() {

    lateinit var field: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        field = EditText(this).apply {
            id = FIELD_ID
            hint = "qa input"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(field)
        })
        field.requestFocus()
    }

    val text: String get() = field.text.toString()

    companion object {
        const val FIELD_ID = 0x0decaf01
    }
}
