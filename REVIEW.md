# Code review — SuperVoiceBoard

A HeliBoard fork that adds on-device dictation (sherpa-onnx streaming Zipformer plus a Parakeet final pass) and an optional out-of-process 0.5B LLM that refines dictated speech and powers an "AI fix" toolbar key.

Read in full: `voice/` and `llm/` (all sources), `app/src/main/java/helium314/keyboard/voice/`, the manifest and Gradle files. Read partially: `core/` (`ModelCatalog`, `PackInstaller` digest paths only). Not read: inherited HeliBoard code, `core/`'s text pipeline and its ~10k lines of tests, `app/src/androidTest`.

## Architecture

Four layers, and the boundaries are real rather than aspirational.

`core/` is a pure Kotlin JVM module with no Android dependency. It holds the decisions: `DictationStateMachine` (343 lines, the single source of session state), `TranscriptCleaner`, `TextFixer`, `PackInstaller`, `AudioPipeline`. Roughly two thirds of the module by line count is tests.

`voice/` is the Android half — `VoiceSessionController` executes the state machine's `Effect`s against `AudioCapture`, the sherpa recognizers and the refiner client. It references no HeliBoard class and no `View`; the keyboard reaches it through `VoiceSessionController.Host` (`VoiceSessionController.kt:74-93`) and `VoiceRuntime` (`voice/.../VoiceRuntime.kt:24`), which the hosting `Application` implements. That inversion is the best structural decision in the fork: the module could be mounted in a different IME with no edits.

`llm/` runs the MediaPipe model in a `:llm` process behind an AIDL interface (`ILlmRefiner.aidl`), so a native OOM in a 0.5B model kills that process and not the keyboard. `LlmRefinerClient` translates every remote failure back into the `null` / typed-`SmartFailure` shapes the in-process refiner used to return, so callers' existing fallbacks cover the new failure modes.

`app/` binds it: `VoiceController` (457 lines) is the only class that turns `Host` callbacks into `InputConnection` edits, `AiFixKey` mounts `AiFixController` on HeliBoard's toolbar-key mechanism, and `LatinIME.java` carries about a dozen lifecycle call sites (`LatinIME.java:797-914`). A three-process split — IME, `:ui` for anything networked, `:llm` — is asserted at build time by `app/src/test/java/com/supervoiceboard/ManifestProcessSplitTest.kt`, which reads the source manifest and fails if `LatinIME` ever acquires `android:process` or a network component leaves `:ui`. That test is doing genuine work.

State lives in three places. Session state is in the state machine, correctly. Engine state is in `VoiceEngines`, a process-wide `object` holding the native handles, a claim counter, an idle-release timer and a refiner binder (`VoiceSessionController.kt:830-1004`). Download state is in `ModelDownloadService`, another `object` with a `MutableStateFlow`, re-seeded from WorkManager after process death.

Where it will hurt:

- `VoiceSessionController.kt` is 1004 lines carrying five separable jobs: threading policy for four dispatchers, effect execution, the utterance-audio split, cleanup/refinement orchestration, and — appended in the same file — the `VoiceEngines` singleton. `VoiceEngines` is reached statically from `VoiceController`, `AiFixController` and the controller itself, so any change to the load/release protocol has to be verified at three call sites that share no type.
- The `:voice` module declares `namespace = "com.vboard.app"` so ported sources' `com.vboard.app.R` keeps resolving (`voice/build.gradle.kts:14-16`), while `:llm` uses `com.vboard.app.llm`. Two modules now own adjacent halves of one package name, and `llm/src/main/kotlin/com/vboard/app/voice/LlmRefiner.kt` sits in the `voice` package inside the `llm` module. Package no longer tells you which module a file is in.
- `AiFixController` (539 lines) mixes `InputConnection` reads, whole-field replacement, undo bookkeeping and button state. `editorialEdits()` and `revertEdit()` (`AiFixController.kt:290-310`) have no caller — the KDoc says so — so per-edit revert is carried but unexercised.
- `ModelStore`'s class KDoc still says packs live in the external media directory (`ModelStore.kt:26-45`), while `ModelRoots.choose` now prefers internal device-protected storage (`ModelRoots.kt:53-58`). `ModelRoots.kt:29-34` is an orphaned KDoc block immediately followed by a second KDoc for the same function. The next person to touch storage will read the wrong one.

## Code quality

Error handling is unusually deliberate: `CancellationException` is rethrown before generic catches (`VoiceSessionController.kt:262`, `ModelDownloadWorker.kt:97`), `AudioCapture` teardown is two-phase because releasing an `AudioRecord` under a blocked JNI read is a native use-after-free, and `VoiceEngines.load` builds both recognizers into locals and publishes only on full success so an OOM in the second constructor cannot orphan the first (`VoiceSessionController.kt:915-928`). `ModelStore.ensureExtractedLocked` stages, verifies, renames, then marks — and clears the installed marker on failure so the UI's "Download" action leads somewhere that can repair it (`ModelStore.kt:201-233`). The tar extractor has a canonical-path zip-slip guard (`ModelStore.kt:285`).

The gap is coroutine failure. There is no `CoroutineExceptionHandler` anywhere in `voice/`, `llm/` or `app/.../voice/`, and there are 11 `launch` blocks on scopes built as `SupervisorJob() + Dispatchers.Main.immediate` (`VoiceSessionController.kt:95`, `AiFixKey.kt:37`). `SupervisorJob` isolates siblings but does not swallow exceptions — an uncaught throw inside `beginFinalize`'s launch (`VoiceSessionController.kt:563`), `refineAsync` (`:690`) or `AiFixController.performFix` (`:177`) reaches the thread's default handler and kills the IME process while the user is typing. That is the exact failure the `:llm` process split exists to prevent, left open on the local path.

Types are sound. Sealed `AudioCapture.Read` distinguishes a clean stop from a dead microphone (`AudioCapture.kt:40-55`), errors are enums rather than strings, and `FieldKind` gates dictation and AI fix consistently. No secrets in the tree; no analytics endpoint — `VoiceMetrics` is explicitly memory-only and off by default (`VoiceController.kt:80-98`). Logging discipline is good: several comments note that a line carries counts or a pack id and never user text, and the code matches.

Test coverage is lopsided. `core/` is thoroughly tested, including golden-corpus and property tests, and `app/src/androidTest` has QA flows. `voice/` and `llm/` — 3.8k lines, and the only concurrency in the fork — have zero unit tests of any kind. Neither module's `build.gradle.kts` declares a test dependency.

Dependency hygiene: versions are pinned literally (no version catalog, consistent with the HeliBoard base), `jitpack.io` is added to `allprojects` repositories for sherpa-onnx (`build.gradle.kts:21`), and MediaPipe's minSdk 24 is force-merged with `tools:overrideLibrary` plus runtime guards at both entry points (`llm/src/main/AndroidManifest.xml:14`, `LlmRefinerService.kt:92`, `LlmRefinerClient.kt:159`) rather than raising the keyboard's floor. Licences are declared per fork file and the added modules are GPL-3.0-only, matching the base.

## Risks

**Unverified model download.** The refiner pack ships `sha256 = ""` against a Hugging Face `main` branch ref (`core/.../ModelCatalog.kt:76-142`); `PackInstaller` treats an empty digest as "skip verification" (`PackInstaller.kt:236`). The result is a ~500 MB file fetched over HTTPS from a mutable ref, with no integrity pinning, that is then loaded and executed by MediaPipe. TLS is the only control. The catalog's own comment says this must be fixed before the pack is required, and a test enforces that coupling — but the pack is downloadable and executable today.

**Field corruption by late refinement.** `VoiceController.replaceUtterance` (`:333-346`) deletes `previous.length` characters before the cursor without checking that those characters still are `previous`. Refinement is asynchronous and lands seconds after the commit; if the user typed in the meantime, their characters are deleted and the refined text is written over them. `AiFixController.applyResult` does exactly the right check before rewriting (`:250`), so the pattern is known in the codebase and simply missing here.

**Two paths pin large native memory until process death.** `VoiceEngines.releaseAll()` and `releaseRefiner()` refuse while `claims > 0` and never reschedule (`VoiceSessionController.kt:938-959`), so a single leaked or racing claim keeps roughly 1.2 GB pinned in the keyboard process for the life of the process. And `LatinIME.onTrimMemory` (`LatinIME.java:2028-2037`) trims layout caches only — nothing releases the recognizers under memory pressure; the 10-minute idle timer is the sole release path.

**Leaked service binding.** `LlmRefinerClient.connect()` calls `bindService`, and if the connection does not arrive within 2 s it returns null while leaving the binding in place (`:117-127`). `disconnect()` returns early when `binder == null` (`:86-90`), so that binding is never released and the `:llm` process — with the model in it — stays alive. The same field, `pending`, is not `@Volatile` and is written from both the main-thread `ServiceConnection` callback and the caller's dispatcher (`:46-62`).

**No timeout on the Google backend.** `GoogleVoiceSession.stopAndFinalize()` sets `stopping` and calls `stopListening()` (`:73-78`). If `SpeechRecognizer` never delivers `onResults` or `onError`, `isRunning` stays true and the strip stays in "Finalizing" indefinitely; only a manual cancel clears it.

**Prompt injection into the correction model.** User text is interpolated straight into a Qwen chat template with no escaping of `<|im_start|>` / `<|im_end|>` (`LlmRefiner.kt:104-130`). A crafted paste can close the user turn and issue its own instructions. Blast radius is limited — the model is local, the output is validated, and the caller keeps rules-only text on rejection — but the "AI fix" key runs on arbitrary field content, including text the user did not write.

## Action items

| Priority | Item | File | Why |
|---|---|---|---|
| P0 | Pin the refiner to an immutable Hugging Face commit SHA and a real sha256 before the pack can be downloaded, not merely before it is `required` | `core/src/main/kotlin/com/vboard/core/model/ModelCatalog.kt:76` | A ~500 MB executable model is installed today with no integrity check behind a mutable branch ref |
| P0 | Verify the text about to be deleted matches the recorded commit before `deleteSurroundingText` | `app/src/main/java/helium314/keyboard/voice/VoiceController.kt:333` | Late refinement silently deletes characters the user typed after the commit |
| P1 | Install a `CoroutineExceptionHandler` on both IME-process scopes, or wrap each `launch` body in a catch | `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:95`, `app/src/main/java/helium314/keyboard/voice/AiFixKey.kt:37` | An uncaught throw in a finalize, refine or fix coroutine kills the keyboard process mid-typing |
| P1 | Release the binding when `connect()` times out, and make `disconnect()` unbind regardless of `binder` | `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:107` | A timed-out bind pins the `:llm` process and its model with no way to release it |
| P1 | Make `pending` `@Volatile` and guard it against completing a superseded connect attempt | `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:46` | Written from the binder callback thread and read from the caller's dispatcher without synchronization |
| P1 | Retry or defer a refused release instead of returning; re-arm the idle timer when `claims > 0` | `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:947` | One stuck claim pins ~1.2 GB of native memory until the process dies |
| P1 | Call `VoiceEngines.releaseAll()` from `onTrimMemory` at `TRIM_MEMORY_RUNNING_CRITICAL`/`COMPLETE` | `app/src/main/java/helium314/keyboard/latin/LatinIME.java:2028` | The largest allocation in the process is invisible to the only memory-pressure signal Android gives an IME |
| P1 | Add a watchdog that ends the Google session if no callback arrives after `stopListening()` | `app/src/main/java/helium314/keyboard/voice/GoogleVoiceSession.kt:73` | A silent recognizer leaves the strip stuck in "Finalizing" with the session marked running |
| P1 | Add JVM unit tests for `LlmRefinerClient` connect/disconnect/failure paths and for `VoiceSessionController`'s stop/finalize ordering | `voice/build.gradle.kts`, `llm/build.gradle.kts` | Neither module declares a test dependency; the fork's only concurrency is entirely unexercised |
| P2 | Escape or strip `<|im_start|>` / `<|im_end|>` from user text before templating | `llm/src/main/kotlin/com/vboard/app/voice/LlmRefiner.kt:104` | Field content reaches the prompt verbatim and can close the user turn |
| P2 | Cap bytes written per file against the server-reported length | `voice/src/main/kotlin/com/vboard/app/models/AndroidFetcher.kt:40` | The read loop writes whatever the server streams, with no ceiling tied to the expected size |
| P2 | Make `findTransducer` deterministic — sort candidates, or fail when a role matches more than one file | `voice/src/main/kotlin/com/vboard/app/models/ModelStore.kt:256` | `firstOrNull` over a `walkTopDown` picks by filesystem order; an archive with two encoders loads a different model per device |
| P2 | Fix the storage KDoc: the class doc claims external media, the code prefers internal device-protected storage | `voice/src/main/kotlin/com/vboard/app/models/ModelStore.kt:26` | The two documents contradict each other and `ModelRoots.kt:53` |
| P2 | Delete the duplicated/orphaned KDoc block above `choose` | `voice/src/main/kotlin/com/vboard/app/models/ModelRoots.kt:29` | Two doc comments for one function, the first describing behaviour the function no longer has |
| P2 | Either wire `editorialEdits()`/`revertEdit()` to a surface or remove them | `voice/src/main/kotlin/com/vboard/app/correct/AiFixController.kt:290` | Tested, documented, and reachable by nothing |
| P2 | Split `VoiceEngines` out of `VoiceSessionController.kt` into its own file | `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:830` | A process-wide singleton with three external call sites is hidden at the bottom of a 1000-line file |
| P2 | Add Gradle dependency verification (or vendor sherpa-onnx) for the JitPack coordinate | `build.gradle.kts:21` | JitPack builds artifacts from source on demand; nothing in the repo pins what was built |
| P2 | Give the final ASR decode a genuinely abandonable thread so `FINALIZE_WATCHDOG_MS` can bound it | `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:793` | The timeout is documented as unable to stop the blocking JNI call; a wedged decode serializes every later finalize behind it |
