// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.os.Build
import androidx.work.Configuration
import com.vboard.app.llm.RefinerModelHost
import com.vboard.app.voice.DefaultVoiceRuntime
import com.vboard.app.voice.VoiceRuntime
import com.vboard.app.voice.VoiceRuntimeHost
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// SuperVoiceBoard: the Application also hosts the voice runtime and configures
// WorkManager, so model downloads run in the `:ui` process rather than the
// keyboard's (PLAN.md §3.3). Everything above and below this is upstream's.
class App : Application(), VoiceRuntimeHost, RefinerModelHost, Configuration.Provider {

    /**
     * Built lazily and in every process: `:llm` needs the model path, `:ui`
     * needs the store and the downloader, and the keyboard needs the session.
     * None of them needs the others' half.
     */
    override val voiceRuntime: VoiceRuntime by lazy {
        DefaultVoiceRuntime(this, prefs(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    /** The `:llm` process asks its own Application for the model path. */
    override fun refinerModelPath(): String? = voiceRuntime.refinerModelPath()

    /**
     * WorkManager is initialized on demand (see the manifest, which removes its
     * androidx.startup initializer) and its workers are pinned to `:ui`: a
     * several-hundred-megabyte model download must not share a memory budget
     * with the thing the user is typing into.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setDefaultProcessName("$packageName:ui")
            .build()

    override fun onCreate() {
        super.onCreate()
        DebugFlags.init(this)
        FoldableUtils.init(this)
        Settings.init(this)
        SubtypeSettings.init(this)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch { // do some uncritical work in background for faster startup
            SupportedEmojis.load(this@App)
            LayoutUtilsCustom.removeMissingLayouts(this@App)
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            Log.i(
                "startup", "Starting ${applicationInfo.processName} version ${packageInfo.versionName} (${
                    packageInfo.versionCode
                }) on Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            )
        }

        RichInputMethodManager.init(this)
        checkVersionUpgrade(this)
        if (BuildConfig.DEBUG) // do this on every debug apk start because we may work on adding a new toolbar key
            upgradeToolbarPrefs(prefs())
        transferOldPinnedClips(this) // todo: remove in a few months, maybe end 2026
        app = this
        Defaults.initDynamicDefaults(this)
    }

    companion object {
        // used so JniUtils can access application once
        private var app: App? = null
        fun getApp(): App? {
            val application = app
            app = null
            return application
        }
    }
}
