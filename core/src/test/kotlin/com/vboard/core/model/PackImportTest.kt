package com.vboard.core.model

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Importing a model file the user already has, instead of downloading it.
 *
 * The rule that matters: an import is a *source*, not a second install pipeline.
 * Imported bytes land in the same staging directory a download writes to, are
 * verified against the same digest, and are finished by the same [install]. What
 * these tests pin is that a wrong file is refused loudly rather than staged and
 * discovered later, when the user can no longer tell what happened.
 */
class PackImportTest {

    @TempDir
    lateinit var root: Path

    private val fetcher = FakeFetcher()

    private fun installer() = PackInstaller(root, fetcher, freeBytes = { Long.MAX_VALUE })

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun body(size: Int, seed: Int = 1): ByteArray =
        ByteArray(size) { i -> ((i * 31 + seed * 17) and 0xff).toByte() }

    private fun pack(files: List<ModelFileSpec>) = ModelPack(
        id = "test-pack",
        displayName = "Test pack",
        kind = ModelKind.STREAMING_ASR,
        version = 1,
        files = files,
        licenseNote = "test",
        required = true,
    )

    private fun spec(rel: String, content: ByteArray, sha: String = sha256Hex(content)) =
        ModelFileSpec(
            relativePath = rel,
            url = "https://models.test/$rel",
            sha256 = sha,
            sizeBytes = content.size.toLong(),
        )

    private fun stagingDir(pack: ModelPack): Path =
        root.resolve(pack.id).resolve("staging-v${pack.version}")

    @Test
    fun `an imported file is staged and finishes the install without a network`() = runTest {
        val content = body(512)
        val pack = pack(listOf(spec("model.onnx", content)))
        val installer = installer()

        assertEquals(
            PackInstaller.ImportResult.STAGED,
            installer.importFile(pack, "model.onnx") { ByteArrayInputStream(content) },
        )
        // The fetcher would throw if anything asked it for bytes: the point of an
        // import is that the network is not involved.
        assertEquals(PackState.Installed, installer.install(pack))
        assertTrue(
            content.contentEquals(
                Files.readAllBytes(installer.installedDir(pack)!!.resolve("model.onnx")),
            ),
        )
    }

    @Test
    fun `the wrong bytes are refused and nothing is left staged`() = runTest {
        val expected = body(512)
        val wrong = body(512, seed = 9)
        val pack = pack(listOf(spec("model.onnx", expected)))
        val installer = installer()

        assertEquals(
            PackInstaller.ImportResult.DIGEST_MISMATCH,
            installer.importFile(pack, "model.onnx") { ByteArrayInputStream(wrong) },
        )
        assertEquals(PackState.NotInstalled, installer.stateOf(pack))
        assertFalse(Files.exists(stagingDir(pack).resolve("model.onnx")))
        // No leftover temp either: a half-file that survives would be resumed as
        // if it were a download.
        val leftovers = Files.list(stagingDir(pack)).use { it.toList() }
        assertTrue(leftovers.isEmpty(), "staging still holds $leftovers")
    }

    @Test
    fun `a file the pack does not expect is refused by name`() = runTest {
        val pack = pack(listOf(spec("model.onnx", body(64))))
        assertEquals(
            PackInstaller.ImportResult.UNKNOWN_FILE,
            installer().importFile(pack, "something-else.tar.bz2") { ByteArrayInputStream(body(64)) },
        )
    }

    @Test
    fun `an unpinned file is accepted, exactly as the downloader accepts it`() = runTest {
        val content = body(128)
        val pack = pack(listOf(spec("model.onnx", content, sha = "")))
        assertEquals(
            PackInstaller.ImportResult.STAGED,
            installer().importFile(pack, "model.onnx") { ByteArrayInputStream(content) },
        )
    }

    @Test
    fun `importing into an installed pack is a no-op`() = runTest {
        val content = body(256)
        val pack = pack(listOf(spec("model.onnx", content)))
        val installer = installer()
        installer.importFile(pack, "model.onnx") { ByteArrayInputStream(content) }
        installer.install(pack)

        assertEquals(
            PackInstaller.ImportResult.ALREADY_INSTALLED,
            installer.importFile(pack, "model.onnx") { ByteArrayInputStream(content) },
        )
    }

    @Test
    fun `a multi-file pack can be imported one file at a time`() = runTest {
        val first = body(100, seed = 1)
        val second = body(200, seed = 2)
        val pack = pack(listOf(spec("a.onnx", first), spec("b.onnx", second)))
        val installer = installer()

        assertEquals(
            PackInstaller.ImportResult.STAGED,
            installer.importFile(pack, "a.onnx") { ByteArrayInputStream(first) },
        )
        assertEquals(
            PackInstaller.ImportResult.STAGED,
            installer.importFile(pack, "b.onnx") { ByteArrayInputStream(second) },
        )
        assertEquals(PackState.Installed, installer.install(pack))
    }
}
