// SPDX-License-Identifier: GPL-3.0-only
package com.supervoiceboard.qa

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Shared plumbing for the on-device QA suite. */
object Qa {

    val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** The app under test, i.e. the debug-suffixed application id on CI. */
    val appId: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    /** The instrumentation APK's own id — where screenshots can be written. */
    val testAppId: String
        get() = InstrumentationRegistry.getInstrumentation().context.packageName

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
    private const val SCREENSHOT_DIR = "/sdcard/Download/ui-qa"

    /**
     * Taken through the shell: the instrumentation process has no external
     * directory of its own on every image, and a screenshot that throws would
     * replace the real failure with its own.
     */
    fun screenshot(name: String) {
        runCatching {
            shell("mkdir -p $SCREENSHOT_DIR")
            shell("screencap -p $SCREENSHOT_DIR/$name.png")
        }
    }
}

/** Screenshots every failure, so a red CI run is diagnosable without a device. */
class ScreenshotOnFailure : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        Qa.screenshot("${description.className.substringAfterLast('.')}-${description.methodName}")
    }
}
