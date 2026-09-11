/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.compat

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.utils.Log
import java.util.*

object EditorInfoCompatUtils {

    @JvmStatic
    fun imeActionName(imeOptions: Int): String {
        return when (val actionId = imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> "actionUnspecified"
            EditorInfo.IME_ACTION_NONE -> "actionNone"
            EditorInfo.IME_ACTION_GO -> "actionGo"
            EditorInfo.IME_ACTION_SEARCH -> "actionSearch"
            EditorInfo.IME_ACTION_SEND -> "actionSend"
            EditorInfo.IME_ACTION_NEXT -> "actionNext"
            EditorInfo.IME_ACTION_DONE -> "actionDone"
            EditorInfo.IME_ACTION_PREVIOUS -> "actionPrevious"
            else -> "actionUnknown($actionId)"
        }
    }

    fun debugLog(editorInfo: EditorInfo, tag: String) {
        // Kotlin's HexFormat compiles down to java.util.HexFormat, which is API
        // 34. A debug log is not worth a NoSuchMethodError on every phone older
        // than that, and "%#010X" says the same thing.
        fun hex(value: Int) = String.format("%#010X", value)
        Log.d(tag, "editorInfo: inputType: ${hex(editorInfo.inputType)}, imeOptions: ${hex(editorInfo.imeOptions)}")
        val allCaps = (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0
        val sentenceCaps = (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        val wordCaps = (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0
        Log.d(tag, ("All caps: $allCaps, sentence caps: $sentenceCaps, word caps: $wordCaps"))
    }

    @JvmStatic
    fun getHintLocales(editorInfo: EditorInfo?): List<Locale> {
        if (editorInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return listOf()
        }
        val localeList = editorInfo.hintLocales ?: return listOf()
        val locales = ArrayList<Locale>(localeList.size())
        for (i in 0 until localeList.size()) {
            locales.add(localeList.get(i))
        }
        return locales
    }
}
