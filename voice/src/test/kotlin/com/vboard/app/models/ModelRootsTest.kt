// SPDX-License-Identifier: GPL-3.0-only
package com.vboard.app.models

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The root rules against real directories, which is what [ModelRoots] was split
 * out of [ModelStore] to allow.
 *
 * What is being defended here is a symptom seen on a device: the keyboard
 * reporting "voice models aren't downloaded yet" for models that were sitting
 * on the phone, because a root was chosen that the early-boot process could not
 * really read, or because a half-finished copy looked like an installed pack.
 */
class ModelRootsTest {

    private fun pack(root: File, name: String, bytes: Int = 8): File {
        val dir = File(root, name).apply { mkdirs() }
        File(dir, "model.onnx").writeBytes(ByteArray(bytes))
        return dir
    }

    // ------------------------------------------------------------- hasPacks

    @Test
    fun `an empty root has no packs`(@TempDir root: File) {
        assertFalse(ModelRoots.hasPacks(root))
    }

    @Test
    fun `a directory that is not there has no packs`(@TempDir tmp: File) {
        assertFalse(ModelRoots.hasPacks(File(tmp, "never-created")))
    }

    @Test
    fun `a loose file is not a pack`(@TempDir root: File) {
        File(root, "parakeet.tar.bz2").writeBytes(ByteArray(4))
        assertFalse(ModelRoots.hasPacks(root))
    }

    @Test
    fun `a staging directory is not a pack`(@TempDir root: File) {
        File(root, "${ModelRoots.STAGING_PREFIX}123-parakeet").mkdirs()
        assertFalse(
            ModelRoots.hasPacks(root),
            "a copy in flight must not look installed, or the next process " +
                "points the mic at models that have not arrived",
        )
    }

    @Test
    fun `an installed pack counts`(@TempDir root: File) {
        pack(root, "parakeet")
        assertTrue(ModelRoots.hasPacks(root))
    }

    // --------------------------------------------------------------- choose

    @Test
    fun `no external storage means internal`(@TempDir internal: File) {
        assertEquals(internal, ModelRoots.choose(null, internal))
    }

    @Test
    fun `internal wins whenever it holds packs`(@TempDir internal: File, @TempDir external: File) {
        pack(internal, "parakeet")
        pack(external, "parakeet")
        assertEquals(internal, ModelRoots.choose(external, internal))
    }

    @Test
    fun `an older build's external packs keep being used`(@TempDir internal: File, @TempDir external: File) {
        pack(external, "parakeet")
        assertEquals(external, ModelRoots.choose(external, internal))
    }

    @Test
    fun `an empty external root is not chosen`(@TempDir internal: File, @TempDir external: File) {
        assertEquals(internal, ModelRoots.choose(external, internal))
    }

    @Test
    fun `an external root that cannot be listed is not chosen`(@TempDir internal: File, @TempDir tmp: File) {
        // A path that exists as a file is the cheapest stand-in for a volume the
        // process can see but not read: isDirectory is false, listFiles is null.
        val external = File(tmp, "media").apply { writeBytes(ByteArray(1)) }
        assertFalse(ModelRoots.isUsable(external))
        assertEquals(internal, ModelRoots.choose(external, internal))
    }

    // ------------------------------------------------------------ copyPacks

    @Test
    fun `packs are copied and counted`(@TempDir from: File, @TempDir to: File) {
        pack(from, "parakeet")
        pack(from, "qwen")

        val moved = ModelRoots.copyPacks(from, to, pid = 7) { _, _ -> fail("no pack should fail") }

        assertEquals(2, moved)
        assertTrue(File(to, "parakeet/model.onnx").isFile)
        assertTrue(File(to, "qwen/model.onnx").isFile)
    }

    @Test
    fun `the originals are left alone`(@TempDir from: File, @TempDir to: File) {
        pack(from, "parakeet")

        ModelRoots.copyPacks(from, to, pid = 7) { _, _ -> }

        assertTrue(
            File(from, "parakeet/model.onnx").isFile,
            "the caller is still reading these — a migration that deletes as it " +
                "goes takes the models out from under a live session",
        )
    }

    @Test
    fun `a pack already there is skipped`(@TempDir from: File, @TempDir to: File) {
        pack(from, "parakeet", bytes = 8)
        pack(to, "parakeet", bytes = 64)

        val moved = ModelRoots.copyPacks(from, to, pid = 7) { _, _ -> }

        assertEquals(0, moved)
        assertEquals(64, File(to, "parakeet/model.onnx").length().toInt())
    }

    @Test
    fun `staging directories are not copied`(@TempDir from: File, @TempDir to: File) {
        File(from, "${ModelRoots.STAGING_PREFIX}9-parakeet").mkdirs()

        assertEquals(0, ModelRoots.copyPacks(from, to, pid = 7) { _, _ -> })
        assertFalse(ModelRoots.hasPacks(to))
    }

    @Test
    fun `nothing is left staged after a run`(@TempDir from: File, @TempDir to: File) {
        pack(from, "parakeet")

        ModelRoots.copyPacks(from, to, pid = 7) { _, _ -> }

        val staged = to.listFiles()?.filter { it.name.startsWith(ModelRoots.STAGING_PREFIX) }
        assertTrue(staged.isNullOrEmpty(), "staging left behind: $staged")
    }

    @Test
    fun `one failed pack does not stop the others`(@TempDir from: File, @TempDir to: File) {
        pack(from, "parakeet")
        pack(from, "qwen")
        // The destination cannot hold "qwen": a file of that name is in the way,
        // so its staging rename fails while the other pack still lands.
        File(to, "qwen").writeBytes(ByteArray(1))

        val failed = mutableListOf<String>()
        val moved = ModelRoots.copyPacks(from, to, pid = 7) { name, _ -> failed.add(name) }

        assertEquals(1, moved)
        assertTrue(File(to, "parakeet/model.onnx").isFile)
        assertEquals(listOf<String>(), failed - setOf("qwen"))
    }

    // --------------------------------------------------------------- sizeOf

    @Test
    fun `sizeOf adds up every file under the root`(@TempDir root: File) {
        pack(root, "parakeet", bytes = 100)
        pack(root, "qwen", bytes = 250)

        assertEquals(350L, ModelRoots.sizeOf(root))
    }

    @Test
    fun `sizeOf an empty root is zero`(@TempDir root: File) {
        assertEquals(0L, ModelRoots.sizeOf(root))
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)
}
