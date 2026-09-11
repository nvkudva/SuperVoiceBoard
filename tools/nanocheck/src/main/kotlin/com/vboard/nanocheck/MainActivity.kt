package com.vboard.nanocheck

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val TAG = "NanoCheck"

class MainActivity : ComponentActivity() {

    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setPadding(32, 48, 32, 32); textSize = 14f }
        setContentView(ScrollView(this).apply { addView(out) })

        log("${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        log("Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
        log("")

        lifecycleScope.launch { check() }
    }

    private suspend fun check() {
        val model = try {
            Generation.getClient()
        } catch (t: Throwable) {
            log("getClient() failed: ${t.javaClass.simpleName}: ${t.message}")
            return
        }

        val status = try {
            model.checkStatus()
        } catch (t: Throwable) {
            log("checkStatus() failed: ${t.javaClass.simpleName}: ${t.message}")
            return
        }

        log("checkStatus() = $status")
        log(
            when (status) {
                0 -> "UNAVAILABLE — Gemini Nano is not supported here."
                1 -> "DOWNLOADABLE — supported, weights not on device yet."
                2 -> "DOWNLOADING — weights are being fetched."
                3 -> "AVAILABLE — ready to use right now."
                else -> "Unrecognised status code."
            }
        )

        log("")
        log("attempting download()...")
        try {
            model.download()
                .catch { log("download flow error: ${it.javaClass.simpleName}: ${it.message}") }
                .collect { log("  $it") }
            log("download() flow completed")
        } catch (t: Throwable) {
            log("download() threw: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        runOnUiThread { out.append(line + "\n") }
    }
}
