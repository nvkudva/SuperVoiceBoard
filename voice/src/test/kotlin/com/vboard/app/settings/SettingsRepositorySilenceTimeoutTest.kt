// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.settings

import android.content.SharedPreferences
import com.vboard.core.session.SilenceTimeout
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

private class FakePrefs(private val values: Map<String, Any?>) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private fun timeoutFor(stored: Any?): SilenceTimeout =
    SettingsRepository(
        FakePrefs(if (stored == null) emptyMap() else mapOf(SettingsRepository.Keys.SILENCE_TIMEOUT to stored)),
    ).snapshot().silenceTimeout

class SettingsRepositorySilenceTimeoutTest {

    @Test
    fun `every enum name round-trips through the stored value`() {
        for (entry in SilenceTimeout.entries) {
            assertEquals(entry, timeoutFor(entry.name), "stored ${entry.name}")
        }
    }

    @Test
    fun `the never case maps to OFF and carries no deadline`() {
        val never = timeoutFor(SilenceTimeout.OFF.name)
        assertEquals(SilenceTimeout.OFF, never)
        assertEquals(null, never.millis)
    }

    @Test
    fun `the finite timeouts keep their documented durations`() {
        assertEquals(5_000L, timeoutFor("S5").millis)
        assertEquals(8_000L, timeoutFor("S8").millis)
        assertEquals(15_000L, timeoutFor("S15").millis)
    }

    @Test
    fun `an unset value falls back to the default`() {
        assertEquals(SilenceTimeout.DEFAULT, timeoutFor(null))
        assertEquals(SilenceTimeout.S8, timeoutFor(null))
    }

    @Test
    fun `a legacy or unknown stored value falls back to the default`() {
        for (raw in listOf("S30", "30", "", "s8", "OFF_", "NEVER")) {
            assertEquals(SilenceTimeout.DEFAULT, timeoutFor(raw), "stored '$raw'")
        }
    }
}
