// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowActivityThread

/**
 * PLAN.md §3.3: `:llm` and `:ui` share this Application but none of the
 * keyboard. The bootstrap is what pulls in emoji tables, subtypes and layouts
 * and runs the one-shot prefs/file migrations — running it in three processes
 * at once is a migration race, so the gate is worth a test of its own.
 *
 * The gate reads the process name from the framework, so the test moves the
 * framework's idea of it rather than the code's: `app` is only published at the
 * very end of the bootstrap, which makes it an honest witness to whether the
 * bootstrap ran.
 */
@RunWith(RobolectricTestRunner::class)
class AppProcessGateTest {

    private lateinit var app: App
    private lateinit var packageName: String

    /** The `processName` the framework hands back to `Context.getProcessName()`. */
    private fun setProcessName(name: String) {
        val thread = ShadowActivityThread.currentActivityThread()
        val bound = thread.javaClass.getDeclaredField("mBoundApplication")
            .apply { isAccessible = true }.get(thread)
        bound.javaClass.getDeclaredField("processName")
            .apply { isAccessible = true }.set(bound, name)
    }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        packageName = app.packageName
    }

    @After fun tearDown() {
        setProcessName(packageName)
        App.getApp()
    }

    @Test fun `the framework agrees the process name is the one the gate reads`() {
        setProcessName("$packageName:ui")
        assertEquals("$packageName:ui", android.app.Application.getProcessName())
    }

    @Test fun `the bootstrap runs in the main process`() {
        App.getApp() // drain whatever the test harness's own startup left
        setProcessName(packageName)

        app.onCreate()

        assertNotNull("the keyboard bootstrap did not run in the main process", App.getApp())
    }

    @Test fun `the bootstrap is skipped in the ui process`() {
        App.getApp()
        setProcessName("$packageName:ui")

        app.onCreate()

        assertNull("the keyboard bootstrap ran outside the main process", App.getApp())
    }

    @Test fun `the bootstrap is skipped in the llm process`() {
        App.getApp()
        setProcessName("$packageName:llm")

        app.onCreate()

        assertNull("the keyboard bootstrap ran outside the main process", App.getApp())
    }
}
