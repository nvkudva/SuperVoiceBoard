// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/** Shared plumbing for the on-device QA suite. */
object Qa {

    val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** The app under test, i.e. the debug-suffixed application id on CI. */
    val appId: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    val imeId: String get() = "$appId/helium314.keyboard.latin.LatinIME"

    fun shell(command: String): String = device.executeShellCommand(command)

    /**
     * The settings screen opens on the welcome wizard unless this IME is both
     * enabled and current, so every non-wizard test has to get past this first.
     */
    fun makeThisImeCurrent() {
        shell("ime enable $imeId")
        shell("ime set $imeId")
        shell("pm grant $appId android.permission.RECORD_AUDIO")
        device.waitForIdle()
    }

    fun makeThisImeNotCurrent() {
        shell("ime reset")
        shell("ime disable $imeId")
        device.waitForIdle()
    }

    /** Where the workflow pulls failures from. */
    private val screenshotDir: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "ui-qa",
        ).also { it.mkdirs() }

    fun screenshot(name: String) {
        val png = File(screenshotDir, "$name.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .takeScreenshot()
            ?.use(Bitmap.CompressFormat.PNG, png)
    }

    private fun Bitmap.use(format: Bitmap.CompressFormat, target: File) {
        target.outputStream().use { compress(format, 100, it) }
        recycle()
    }
}

/** Screenshots every failure, so a red CI run is diagnosable without a device. */
class ScreenshotOnFailure : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        Qa.screenshot("${description.className.substringAfterLast('.')}-${description.methodName}")
    }
}
