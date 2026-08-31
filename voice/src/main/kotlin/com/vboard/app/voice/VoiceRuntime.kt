// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.voice

import android.content.Context
import android.content.SharedPreferences
import com.vboard.app.llm.RefinerModelHost
import com.vboard.app.models.AndroidFetcher
import com.vboard.app.models.ModelStore
import com.vboard.app.settings.SettingsRepository
import com.vboard.core.model.PackInstaller
import com.vboard.core.text.TranscriptCleaner
import kotlinx.coroutines.CoroutineScope

/**
 * What the voice layer needs from the hosting application.
 *
 * VBoard handed its `Application` subclass straight to the session controller,
 * the download worker and the refiner client. SuperVoiceBoard cannot: the
 * hosting Application is HeliBoard's, an upstream class this fork does not own
 * (PLAN.md §3.2). This is the same set of members under an interface the
 * keyboard's Application implements, so nothing in :voice knows which keyboard
 * it is mounted in.
 */
interface VoiceRuntime : RefinerModelHost {
    val appScope: CoroutineScope
    val settings: SettingsRepository
    val modelStore: ModelStore
    val packInstaller: PackInstaller
    val cleaner: TranscriptCleaner

    override fun refinerModelPath(): String? = modelStore.refinerModelPath(packInstaller)
}

/**
 * Implemented by the `Application` class so that a `Context` alone — all a
 * WorkManager worker or a Service is given — can reach the runtime.
 */
interface VoiceRuntimeHost {
    val voiceRuntime: VoiceRuntime
}

/** The runtime behind [context], or null in a process that never built one. */
fun voiceRuntimeOrNull(context: Context): VoiceRuntime? =
    (context.applicationContext as? VoiceRuntimeHost)?.voiceRuntime

/**
 * The default runtime: the pieces VBoard's Application built, minus everything
 * the keyboard half of it owned (suggestion engine, learned-word history — that
 * is HeliBoard's native decoder here).
 */
class DefaultVoiceRuntime(
    context: Context,
    /** The keyboard's own preference file; voice settings live in it (W2.5). */
    prefs: SharedPreferences,
    override val appScope: CoroutineScope,
) : VoiceRuntime {
    private val appContext = context.applicationContext

    override val settings: SettingsRepository = SettingsRepository(prefs)
    override val modelStore: ModelStore = ModelStore(appContext)
    override val packInstaller: PackInstaller = PackInstaller(
        rootDir = modelStore.rootDir.toPath(),
        fetcher = AndroidFetcher(),
        // The volume the packs actually land on, which is not necessarily the
        // one filesDir is on.
        freeBytes = { modelStore.rootDir.usableSpace },
    )
    override val cleaner: TranscriptCleaner = TranscriptCleaner()
}
