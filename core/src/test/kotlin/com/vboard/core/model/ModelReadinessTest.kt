package com.vboard.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelReadinessTest {

    private val parakeet = ModelCatalog.byId("parakeet-tdt-0.6b-v2")!!
    private val refiner = ModelCatalog.byId("qwen3-06b-refiner")!!

    // ------------------------------------------------ "enough to dictate"

    @Test
    fun `nothing installed means the user cannot dictate`() {
        assertFalse(ModelReadiness.canDictate(emptySet()))
    }

    @Test
    fun `the speech pack alone is enough to dictate`() {
        assertTrue(ModelReadiness.canDictate(setOf(parakeet.id)))
    }

    @Test
    fun `the optional packs neither grant nor withhold the ability to dictate`() {
        // Present without the speech pack: still no dictation.
        assertFalse(ModelReadiness.canDictate(setOf(refiner.id)))

        // Added on top of the speech pack: no change either way.
        assertTrue(ModelReadiness.canDictate(setOf(parakeet.id, refiner.id)))
        assertTrue(ModelReadiness.canDictate(ModelCatalog.packs.map { it.id }.toSet()))
    }

    @Test
    fun `an unknown installed id cannot fake readiness`() {
        assertFalse(ModelReadiness.canDictate(setOf("some-other-pack")))
    }

    // ------------------------------------------------------ required set

    @Test
    fun `the speech pack is required and only the refiner is optional`() {
        assertEquals(listOf(parakeet.id), ModelCatalog.requiredPacks.map { it.id })
        assertEquals(listOf(refiner.id), ModelCatalog.optionalPacks.map { it.id })
    }

    @Test
    fun `anything dictation cannot run without must be required`() {
        // One-directional on purpose. A pack dictation technically survives without may
        // still be required for quality - the accuracy pass is exactly that case. What
        // must never happen is the reverse: setup letting someone finish without a pack
        // the mic genuinely cannot work without.
        val installedIds = ModelCatalog.packs.map { it.id }.toSet()
        for (pack in ModelCatalog.packs) {
            if (!ModelReadiness.canDictate(installedIds - pack.id)) {
                assertTrue(pack.required, "${pack.id} is load-bearing for dictation but not required")
            }
        }
        // And the refiner, which only rewrites already-committed text, is never required.
        assertFalse(refiner.required)
    }

    @Test
    fun `missingRequired names what setup still has to fetch`() {
        assertEquals(
            listOf(parakeet.id),
            ModelReadiness.missingRequired(emptySet()).map { it.id },
        )
        // The refiner never appears here, installed or not.
        assertEquals(
            emptyList(),
            ModelReadiness.missingRequired(setOf(parakeet.id)).map { it.id },
        )
        assertEquals(
            emptyList(),
            ModelReadiness.missingRequired(setOf(parakeet.id, refiner.id)).map { it.id },
        )
    }

    @Test
    fun `remaining required bytes covers the speech pack, then zero`() {
        assertEquals(parakeet.totalBytes, ModelReadiness.remainingRequiredBytes(emptySet()))
        assertEquals(0L, ModelReadiness.remainingRequiredBytes(setOf(parakeet.id)))
        // Installing the optional refiner does not reduce it, because it never counted.
        assertEquals(parakeet.totalBytes, ModelReadiness.remainingRequiredBytes(setOf(refiner.id)))
    }

    @Test
    fun `upgrades on offer shrink as optional packs are installed`() {
        // Only the refiner is an upgrade now; the accuracy pass is part of the product.
        assertEquals(
            listOf(refiner.id),
            ModelReadiness.availableUpgrades(setOf(parakeet.id)).map { it.id },
        )
        assertEquals(
            emptyList(),
            ModelReadiness.availableUpgrades(ModelCatalog.packs.map { it.id }.toSet()).map { it.id },
        )
    }

    // --------------------------------------------- catalog-independence

    @Test
    fun `readiness follows the pack list it is given, not the shipped catalog`() {
        val other = parakeet.copy(id = "some-other-speech-model")
        assertTrue(ModelReadiness.canDictate(setOf(other.id), packs = listOf(other)))
        assertFalse(ModelReadiness.canDictate(setOf(parakeet.id), packs = listOf(other)))
    }
}
