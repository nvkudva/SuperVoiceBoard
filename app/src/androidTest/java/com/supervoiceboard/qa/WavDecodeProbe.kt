// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vboard.app.voice.VoiceEngines
import com.vboard.app.voice.VoiceRuntimeHost
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WaveKey: feeds a known-good WAV straight into the loaded recogniser, with no
 * microphone in the path.
 *
 * "It listens and nothing is typed" has two possible causes — a microphone that
 * hands us silence, or an engine that returns nothing for real speech — and the
 * live keyboard cannot tell them apart. This can: the audio is a file, so a
 * blank result here is the engine's, and a non-blank one puts the fault back on
 * capture.
 *
 * Push the file first:
 *   adb push speech.wav /sdcard/Download/speech.wav
 * 16-bit PCM, mono, 16 kHz — what AudioCapture produces.
 */
@RunWith(AndroidJUnit4::class)
class WavDecodeProbe {

    @Test
    fun transcribes_a_wav_without_the_microphone() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = (ctx.applicationContext as VoiceRuntimeHost).voiceRuntime

        val load = VoiceEngines.load(runtime)
        Log.i(TAG, "probe: load=$load engine=${VoiceEngines.finalPass != null}")
        assertTrue("no engine loaded: $load", VoiceEngines.finalPass != null)

        // The app's own external files dir: readable without a storage
        // permission, and adb can write to it.
        val file = File(ctx.getExternalFilesDir(null), "speech.wav")
        assertTrue("push a 16kHz mono WAV to ${file.path} first", file.exists())
        val samples = readPcm16Wav(file)
        val peak = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        Log.i(TAG, "probe: samples=${samples.size} peak=$peak")

        VoiceEngines.beginUse()
        val text = try {
            VoiceEngines.finalPass?.transcribe(samples)
        } finally {
            VoiceEngines.endUse()
        }
        // The text is this test's own fixture, not a user's speech, so logging it
        // is safe and is the whole point of the probe.
        Log.i(TAG, "probe: decoded=\"${text.orEmpty()}\"")
        assertTrue("engine returned nothing for a WAV with peak $peak", !text.isNullOrBlank())
    }

    /** Minimal WAV reader: finds the data chunk, reads it as little-endian PCM16. */
    private fun readPcm16Wav(file: File): FloatArray {
        val bytes = file.readBytes()
        var offset = 12 // past "RIFF" size "WAVE"
        var dataStart = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                dataStart = offset + 8
                dataSize = size.coerceAtMost(bytes.size - dataStart)
                break
            }
            offset += 8 + size + (size and 1)
        }
        require(dataStart > 0) { "no data chunk in ${file.path}" }
        val shorts = ByteBuffer.wrap(bytes, dataStart, dataSize)
            .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
    }

    private companion object {
        const val TAG = "VBoardVoice"
    }
}
