package com.vboard.core.model

/**
 * The single answer to "is enough installed to dictate?".
 *
 * The app used to ask "are all required packs present?" in four places, and because both
 * speech packs were marked required, that question also meant "has the user downloaded
 * 610 MB?" — which is why setup dead-ended. Dictation needs exactly one thing: a recognizer.
 * The refiner only rewrites text that already exists, so it is never a dependency and may
 * never influence the answers below.
 */
object ModelReadiness {

    /**
     * True when [installedPackIds] contains the on-device recognizer, i.e. the user can press
     * the mic and get words. Unaffected by the refiner by construction.
     */
    fun canDictate(
        installedPackIds: Set<String>,
        packs: List<ModelPack> = ModelCatalog.packs,
    ): Boolean = packs.any { it.kind == ModelKind.FINAL_ASR && it.id in installedPackIds }

    /** Required packs still missing — what setup would need before dictation works. */
    fun missingRequired(
        installedPackIds: Set<String>,
        packs: List<ModelPack> = ModelCatalog.packs,
    ): List<ModelPack> = packs.filter { it.required && it.id !in installedPackIds }

    /**
     * Optional packs not yet installed, i.e. upgrades still on offer. Present so the UI can
     * say "you can add this later" instead of "something is missing".
     */
    fun availableUpgrades(
        installedPackIds: Set<String>,
        packs: List<ModelPack> = ModelCatalog.packs,
    ): List<ModelPack> = packs.filter { !it.required && it.id !in installedPackIds }

    /**
     * Bytes still to fetch before dictation works. Zero once [canDictate] holds, so it is
     * safe to render as "X to go" without special-casing the finished state.
     */
    fun remainingRequiredBytes(
        installedPackIds: Set<String>,
        packs: List<ModelPack> = ModelCatalog.packs,
    ): Long = missingRequired(installedPackIds, packs).sumOf { it.totalBytes }
}
