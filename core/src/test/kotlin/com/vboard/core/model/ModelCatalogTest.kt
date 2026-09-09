package com.vboard.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModelCatalogTest {

    @Test
    fun `catalog contains exactly the two expected packs`() {
        assertEquals(
            listOf("parakeet-tdt-0.6b-v2", "qwen25-05b-refiner"),
            ModelCatalog.packs.map { it.id },
        )
    }

    @Test
    fun `byId returns matching pack and null for unknown id`() {
        val parakeet = ModelCatalog.byId("parakeet-tdt-0.6b-v2")
        assertSame(ModelCatalog.packs[0], parakeet)
        assertEquals("High-accuracy transcription (English only)", parakeet?.displayName)
        assertNull(ModelCatalog.byId("does-not-exist"))
    }

    @Test
    fun `byKind maps each kind to its pack`() {
        assertTrue(ModelCatalog.byKind(ModelKind.STREAMING_ASR).isEmpty())
        assertEquals(listOf("parakeet-tdt-0.6b-v2"), ModelCatalog.byKind(ModelKind.FINAL_ASR).map { it.id })
        assertEquals(listOf("qwen25-05b-refiner"), ModelCatalog.byKind(ModelKind.REFINER_LLM).map { it.id })
    }

    @Test
    fun `required and archive flags match spec`() {
        val parakeet = ModelCatalog.byId("parakeet-tdt-0.6b-v2")!!
        val refiner = ModelCatalog.byId("qwen25-05b-refiner")!!

        // Parakeet is the only on-device recognizer, so it is what the mic needs.
        // Only the refiner - which rewrites already-committed text - is opt-in.
        assertNull(ModelCatalog.byId("zipformer-en-streaming"))
        assertTrue(parakeet.required)
        assertFalse(refiner.required)

        assertTrue(parakeet.files.single().archive)
        assertFalse(refiner.files.single().archive)

        assertEquals(ModelKind.FINAL_ASR, parakeet.kind)
        assertEquals("High-accuracy transcription (English only)", parakeet.displayName)
        assertEquals("Qwen2.5, Apache-2.0 (LiteRT community build)", refiner.licenseNote)
    }

    @Test
    fun `every shipping pack declares its language coverage explicitly`() {
        // Both packs are English-only: the v2 weights, and the refiner whose instruction
        // prompt is English prose. Asserted rather than left to the constructor default so
        // that a pack added later has to state what it covers instead of inheriting "en".
        for (pack in ModelCatalog.packs) {
            assertEquals(setOf("en"), pack.languages, "${pack.id} languages")
        }
    }

    @Test
    fun `asrPacksFor covers english variants and nothing else`() {
        val parakeet = ModelCatalog.byId("parakeet-tdt-0.6b-v2")!!
        assertEquals(listOf(parakeet), ModelCatalog.asrPacksFor("en"))
        assertEquals(listOf(parakeet), ModelCatalog.asrPacksFor("en-GB"))
        // Android hands out underscored tags when a Locale is stringified.
        assertEquals(listOf(parakeet), ModelCatalog.asrPacksFor("en_US"))
        assertEquals(listOf(parakeet), ModelCatalog.asrPacksFor("EN"))

        // The refiner declares "en" too, but it is not a recognizer and must never be
        // offered as one - asrPacksFor filters by kind before it filters by language.
        assertTrue(ModelCatalog.asrPacksFor("en").none { it.kind == ModelKind.REFINER_LLM })

        // No on-device model for these, which is the state this whole feature turns on.
        assertTrue(ModelCatalog.asrPacksFor("fr").isEmpty())
        assertTrue(ModelCatalog.asrPacksFor("fr-CA").isEmpty())
        // "eng" is a different tag, not a longer spelling of "en".
        assertTrue(ModelCatalog.asrPacksFor("eng").isEmpty())
        // An unknown language is not English.
        assertTrue(ModelCatalog.asrPacksFor("").isEmpty())
        assertTrue(ModelCatalog.asrPacksFor("   ").isEmpty())
    }

    @Test
    fun `covers matches on a subtag boundary and treats an empty set as language-agnostic`() {
        val zhHans = languagePack(setOf("zh-Hans"))
        assertTrue(zhHans.covers("zh-Hans"))
        assertTrue(zhHans.covers("zh-hans-CN"))
        // Different script, not a narrower variant: matching these would feed a model
        // audio it cannot read.
        assertFalse(zhHans.covers("zh-Hant"))
        assertFalse(zhHans.covers("zh"))

        val multilingual = languagePack(setOf("en", "fr", "de"))
        assertTrue(multilingual.covers("fr-CA"))
        assertFalse(multilingual.covers("es"))

        val agnostic = languagePack(emptySet())
        assertTrue(agnostic.covers("ja"))
        assertTrue(agnostic.covers(""))
    }

    @Test
    fun `readiness filtered by language answers false where no pack covers the language`() {
        // The seam: ModelReadiness stays a pure "do I have these packs?" question and
        // becomes locale-aware only through the list it is handed.
        val installed = setOf("parakeet-tdt-0.6b-v2")
        assertTrue(ModelReadiness.canDictate(installed, ModelCatalog.asrPacksFor("en-GB")))
        assertFalse(ModelReadiness.canDictate(installed, ModelCatalog.asrPacksFor("fr")))
    }

    private fun languagePack(languages: Set<String>) = ModelPack(
        id = "test-pack",
        displayName = "Test pack",
        kind = ModelKind.FINAL_ASR,
        version = 1,
        files = listOf(ModelFileSpec("m.onnx", "https://models.test/m.onnx", "", 1L)),
        licenseNote = "test",
        required = true,
        languages = languages,
    )

    @Test
    fun `pinned hashes are well formed and sizes sum into totalBytes`() {
        for (pack in ModelCatalog.packs) {
            assertEquals(1, pack.version)
            assertTrue(pack.files.isNotEmpty())
            for (file in pack.files) {
                // Empty means "skip verification"; anything else must be a real digest,
                // because a malformed hash would fail every install rather than none.
                if (file.sha256.isNotEmpty()) {
                    assertEquals(64, file.sha256.length, "${file.relativePath} sha256 length")
                    assertTrue(
                        file.sha256.all { it in "0123456789abcdef" },
                        "${file.relativePath} sha256 must be lowercase hex",
                    )
                }
                assertTrue(file.sizeBytes > 0)
            }
            assertEquals(pack.files.sumOf { it.sizeBytes }, pack.totalBytes)
        }
        // The speech pack is pinned to a digest measured from the upstream asset, so a
        // corrupted-but-complete download can no longer install. The refiner stays
        // unpinned until its host can be hashed from the release pipeline.
        assertTrue(
            ModelCatalog.byId("parakeet-tdt-0.6b-v2")!!.files.all { it.sha256.isNotEmpty() },
            "the speech pack must ship a pinned digest",
        )
        // Sizes measured from the upstream release assets; the installer re-checks with
        // the server, so drift here only affects progress and the storage pre-check.
        assertEquals(482_468_385L, ModelCatalog.byId("parakeet-tdt-0.6b-v2")!!.totalBytes)
        assertEquals(547_000_000L, ModelCatalog.byId("qwen25-05b-refiner")!!.totalBytes)
    }

    @Test
    fun `a pack fetched from a mutable ref or without a digest can never be required`() {
        // The refiner is pulled from a Hugging Face branch ref with no pinned digest, so its
        // bytes can change under us and nothing verifies them. That is tolerable for an
        // opt-in extra and not for anything on the setup critical path, so the two
        // properties are wired together here rather than left to a code review.
        for (pack in ModelCatalog.packs) {
            val unverified = pack.files.any { it.sha256.isEmpty() }
            val mutableRef = pack.files.any { it.url.contains("/resolve/main/") }
            if (unverified || mutableRef) {
                assertFalse(
                    pack.required,
                    "${pack.id} must be optional until its URL is pinned to an immutable " +
                        "revision and its sha256 is filled in from the release pipeline",
                )
            }
        }
    }

    @Test
    fun `every download url is https`() {
        for (pack in ModelCatalog.packs) {
            for (file in pack.files) {
                assertTrue(file.url.startsWith("https://"), "${file.relativePath} must use https")
            }
        }
    }

    @Test
    fun `archive packs budget extra disk for extraction the plain file pack does not`() {
        val parakeet = ModelCatalog.byId("parakeet-tdt-0.6b-v2")!!
        assertEquals(parakeet.totalBytes * 5 / 2, parakeet.installFootprintBytes)

        // The LLM .task is downloaded as-is, so its footprint is just its size.
        val refiner = ModelCatalog.byId("qwen25-05b-refiner")!!
        assertEquals(refiner.totalBytes, refiner.installFootprintBytes)
    }

    @Test
    fun `downloading fraction is bytesDone over bytesTotal and safe at zero total`() {
        assertEquals(0.25, PackState.Downloading(25, 100).fraction)
        assertEquals(0.0, PackState.Downloading(0, 100).fraction)
        assertEquals(1.0, PackState.Downloading(100, 100).fraction)
        assertEquals(0.0, PackState.Downloading(0, 0).fraction)
    }
}
