package com.vboard.app.models

import java.io.File
import java.io.IOException

/**
 * The rules for *which* directory the model packs live in, and how they get
 * moved between two of them — with no Android in sight, so they can be tested
 * against real directories instead of an emulated storage volume.
 *
 * [ModelStore] owns the Android half: finding the external media directory,
 * deciding whether it is mounted, and when to run the migration.
 */
internal object ModelRoots {

    /** Dot-prefixed so [hasPacks] cannot mistake an in-flight copy for a pack. */
    const val STAGING_PREFIX = ".staging-"

    /**
     * A root "has packs" when a pack directory has been installed under it.
     * Dot-prefixed entries do not count: a migration in flight leaves staging
     * directories behind, and treating one as a pack would point the next
     * process at a root whose models have not arrived yet.
     */
    fun hasPacks(root: File): Boolean =
        root.isDirectory &&
            root.listFiles()?.any { it.isDirectory && !it.name.startsWith(".") } == true

    /**
     * [external] when it is usable — but never mid-flight. A process that
     * already has packs on internal storage keeps reading them from there until
     * the copy has landed, so the mic never reports "not installed" for models
     * that are on the device.
     */
    /**
     * Internal storage wins, and external is only read when models are already
     * there (SuperVoiceBoard).
     *
     * VBoard preferred `Android/media`, so an uninstall would not take a
     * gigabyte of models with it. That is the wrong trade for a keyboard: the
     * IME is `directBootAware` and its process is started by the system before
     * the device is unlocked, and a process started that early does not get a
     * usable view of external storage — it can list the models directory and
     * still not see a file another process wrote into it. The symptom is the
     * keyboard reporting "voice models aren't downloaded yet" for models that
     * are demonstrably on the device, which is what this fork hit in testing.
     *
     * Device-protected internal storage (this app sets
     * `defaultToDeviceProtectedStorage`) is readable by every process of the app
     * at every point in the boot, which is the property the mic needs. Models
     * already installed externally by an older build keep being used.
     */
    fun choose(external: File?, internal: File): File {
        if (external == null) return internal
        if (hasPacks(internal)) return internal
        if (isUsable(external) && hasPacks(external)) return external
        return internal
    }

    /** True when the app can actually list and read [root], not merely see it. */
    fun isUsable(root: File): Boolean =
        root.isDirectory && root.canRead() && root.listFiles() != null

    /**
     * Copies pack directories from [from] to [to], skipping any that are already
     * there, and returns how many arrived.
     *
     * Pack by pack, each through its own staging directory renamed into place.
     * That granularity is what makes an interrupted migration useful: the packs
     * that made it are usable immediately and the rest are picked up next time,
     * instead of a gigabyte of work being thrown away. Nothing is deleted here —
     * the caller is still reading the originals.
     */
    fun copyPacks(from: File, to: File, pid: Int, onError: (String, Throwable) -> Unit): Int {
        var moved = 0
        for (packDir in from.listFiles()?.filter { it.isDirectory }.orEmpty()) {
            if (packDir.name.startsWith(".")) continue
            val target = File(to, packDir.name)
            if (target.exists()) continue
            val staging = File(to, "$STAGING_PREFIX$pid-${packDir.name}")
            staging.deleteRecursively()
            try {
                if (!packDir.copyRecursively(staging, overwrite = true)) {
                    throw IOException("copy did not complete")
                }
                if (!staging.renameTo(target)) throw IOException("cannot activate the copy")
                moved++
            } catch (e: Throwable) {
                onError(packDir.name, e)
                staging.deleteRecursively()
            }
        }
        return moved
    }

    /** Bytes that [copyPacks] would have to write to move everything under [root]. */
    fun sizeOf(root: File): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
