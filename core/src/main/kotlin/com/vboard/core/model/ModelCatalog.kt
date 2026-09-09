package com.vboard.core.model

/** The role a model pack plays in the VBoard pipeline. */
enum class ModelKind { STREAMING_ASR, FINAL_ASR, REFINER_LLM }

/**
 * One downloadable file belonging to a [ModelPack].
 *
 * @property relativePath path under the pack's install dir, e.g. "encoder.int8.onnx".
 * @property url download URL.
 * @property sha256 lowercase hex digest; empty string = skip verification (used until final
 *   hashes are pinned).
 * @property sizeBytes expected size of the file in bytes.
 * @property archive true when [url] points at an archive (e.g. tar.bz2); [relativePath] is then
 *   the archive filename and the app layer extracts it after install. The download/install engine
 *   itself is archive-agnostic: it just downloads and verifies the archive file.
 */
data class ModelFileSpec(
    val relativePath: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val archive: Boolean = false,
)

/**
 * @property required true only for packs without which VBoard cannot transcribe speech at all.
 *   This is deliberately the *smallest* set that makes dictation work, not "everything the
 *   pipeline can use": an optional pack is an upgrade the user opts into later, and setup must
 *   never be gated on one. [ModelReadiness] is the single place that answers "installed enough
 *   to dictate?" — do not re-derive it by counting packs.
 * @property languages BCP-47 tags this pack can handle, e.g. `setOf("en")`; an empty set means
 *   language-agnostic. Last and defaulted so existing positional constructions keep compiling,
 *   but declared explicitly by every shipping pack: which languages a model covers is a fact
 *   about the model, and inheriting it from a default is how a pack ends up silently claiming
 *   a language it was never trained on. [ModelPack.covers] is the one place that interprets
 *   this — do not re-derive coverage by comparing a locale string elsewhere.
 */
data class ModelPack(
    val id: String,
    val displayName: String,
    val kind: ModelKind,
    val version: Int,
    val files: List<ModelFileSpec>,
    val licenseNote: String,
    val required: Boolean,
    val languages: Set<String> = setOf("en"),
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }

    /**
     * Peak disk the install needs. An archive is extracted before it is deleted, so it
     * briefly coexists with its contents; 2.5x the compressed size covers a bz2 of
     * already-quantized ONNX weights (which compress poorly) plus the archive itself.
     */
    val installFootprintBytes: Long
        get() = files.sumOf { if (it.archive) it.sizeBytes * 5 / 2 else it.sizeBytes }

    /**
     * True when this pack can handle [language] — a BCP-47 tag ("fr", "en-GB"), or the
     * underscored form Android hands out when a `Locale` is stringified ("fr_CA").
     *
     * Matching is on a subtag boundary, not a bare prefix: a pack declaring "en" covers
     * "en-GB", and a pack declaring "zh-Hans" does not cover "zh-Hant" — the two are
     * different scripts and feeding one model the other's audio is exactly the failure this
     * whole field exists to prevent. A blank tag matches nothing but a language-agnostic
     * pack: an unknown language must not be assumed to be English.
     */
    fun covers(language: String): Boolean {
        if (languages.isEmpty()) return true
        val requested = languageTagForms(language)
        return languages.any { declared ->
            val tag = declared.replace('_', '-').lowercase()
            requested.any { it == tag || it.startsWith("$tag-") }
        }
    }
}

/**
 * The forms of [language] a declared tag is matched against: the whole normalized tag, plus
 * its primary subtag for callers that pass something a `-`/`_` split cannot fully normalize.
 */
private fun languageTagForms(language: String): List<String> {
    val full = language.trim().replace('_', '-').lowercase()
    if (full.isEmpty()) return emptyList()
    val primary = language.trim().substringBefore('-').substringBefore('_').lowercase()
    return if (primary == full) listOf(full) else listOf(full, primary)
}

/**
 * Static catalog of the model packs VBoard knows how to download.
 *
 * Empty sha256 means "skip verification"; hashes get pinned once a release
 * process snapshots the upstream artifacts. Sizes seed the progress UI and the
 * storage pre-check only - the installer asks the server for each file's real
 * length and never treats these numbers as a completion gate.
 */
object ModelCatalog {

    private const val SHERPA_RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

    /**
     * Hugging Face revision the refiner download is pinned to.
     *
     * This MUST become an immutable commit SHA before the refiner is ever marked
     * [ModelPack.required] or shipped with a digest: `main` is a mutable branch ref, so the
     * bytes behind the URL can change under us between releases and nothing would notice.
     * It is still `main` here only because this build environment cannot reach
     * huggingface.co to read the current commit — inventing a 40-hex SHA would 404 every
     * user's download, which is strictly worse than a mutable ref.
     *
     * The release pipeline fills this in together with the file's [ModelFileSpec.sha256].
     * `ModelCatalogTest` fails the build if an unpinned pack is ever marked required, so the
     * gap can never reach a user on the critical path.
     */
    private const val REFINER_REVISION = "main"

    private const val REFINER_BASE =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve"

    val packs: List<ModelPack> = listOf(
        ModelPack(
            id = "parakeet-tdt-0.6b-v2",
            // "(English only)", not "(English)": this is the row title on the models screen,
            // and it is the last thing a user reads before spending 482 MB. The v2 weights are
            // English-only, so the title has to say so before the download, not after it.
            displayName = "High-accuracy transcription (English only)",
            kind = ModelKind.FINAL_ASR,
            version = 1,
            files = listOf(
                ModelFileSpec(
                    relativePath = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
                    url = "$SHERPA_RELEASE_BASE/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
                    sha256 = "157c157bc51155e03e37d2466522a3a737dd9c72bb25f36eb18912964161e1ad",
                    sizeBytes = 482_468_385L, // measured from the release asset
                    archive = true,
                ),
            ),
            licenseNote = "sherpa-onnx NeMo Parakeet TDT 0.6B v2, CC-BY-4.0",
            // The only on-device recognizer now. The streaming Zipformer that used to carry
            // dictation was removed: its live text was wrong often enough that watching it
            // was worse than waiting, and the system recognizer covers anyone who wants
            // live words. Parakeet transcribes the utterance once, on stop.
            required = true,
            // v2 is English-only. The multilingual v3 archive would be a second entry here
            // with a wider set, which is the whole reason coverage is data and not an
            // `if (language == "en")` somewhere in the voice layer.
            languages = setOf("en"),
        ),
        ModelPack(
            // Qwen is used as the default refiner because litert-community hosts
            // it ungated (a Gemma .task requires a Hugging Face license
            // acceptance + auth token, which a keyboard can't ask for mid-setup).
            id = "qwen25-05b-refiner",
            displayName = "Smart cleanup (on-device LLM)",
            kind = ModelKind.REFINER_LLM,
            version = 1,
            files = listOf(
                ModelFileSpec(
                    relativePath = "qwen2.5-0.5b-instruct-q8.task",
                    url = "$REFINER_BASE/$REFINER_REVISION/" +
                        "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                    // Intentionally empty: the digest must be filled in from the release
                    // pipeline, which is the only place that can fetch the artifact and hash
                    // it. Empty means "skip verification" (see ModelFileSpec); a fabricated
                    // hash would fail every install instead of none. Pair it with a pinned
                    // REFINER_REVISION in the same change.
                    sha256 = "",
                    sizeBytes = 547_000_000L, // estimate; the installer uses the server's length
                ),
            ),
            licenseNote = "Qwen2.5, Apache-2.0 (LiteRT community build)",
            required = false,
            // The refiner's instruction prompt is written in English, so refining French
            // dictation would produce English-flavoured edits to text nobody asked it to
            // touch. Declaring the coverage makes "should this run?" a lookup rather than a
            // second hardcoded locale check next to the prompt.
            languages = setOf("en"),
        ),
    )

    fun byId(id: String): ModelPack? = packs.firstOrNull { it.id == id }

    fun byKind(kind: ModelKind): List<ModelPack> = packs.filter { it.kind == kind }

    /**
     * On-device recognizers that cover [language] — empty when there is no on-device model for
     * it, which today is every language but English.
     *
     * This is the single answer to "can we transcribe this language on the device?". Callers
     * pass it to [ModelReadiness.canDictate] as the `packs` argument (see
     * `ModelStore.dictationReadyFor`) so readiness stays a pure "do I have these packs?"
     * question and the locale policy lives here, in one place, as data.
     *
     * Takes a [String] rather than a `java.util.Locale` deliberately: `:core` is a plain JVM
     * module and stays free of platform types. Callers hand over `subtype.locale().language`
     * or a full tag; both work.
     */
    fun asrPacksFor(language: String): List<ModelPack> =
        byKind(ModelKind.FINAL_ASR).filter { it.covers(language) }

    /** Packs setup genuinely cannot finish without. See [ModelReadiness]. */
    val requiredPacks: List<ModelPack> get() = packs.filter { it.required }

    /** Opt-in upgrades: never gate setup, never appear in the "you need" figure. */
    val optionalPacks: List<ModelPack> get() = packs.filterNot { it.required }
}
