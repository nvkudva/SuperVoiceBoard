# Architecture review — fork modules (core/, voice/, llm/, app/.../voice, app/.../correct)

## Context

Review of the fork's own code only: `:core` (pure JVM domain), `:voice` (IME-agnostic Android
layer), `:llm` (the `:llm` process and its AIDL), and the HeliBoard-side glue in
`app/src/main/java/helium314/keyboard/{voice,correct}/` plus its wiring into `LatinIME.java`
and `App.kt`. Upstream HeliBoard internals were not reviewed.

The module split is sound and deliberate: `:core` is platform-free and well covered by tests,
`:voice` genuinely knows nothing about HeliBoard (only `VoiceRuntime` / `VoiceSessionController.Host`),
and the refiner really is behind a process boundary. The findings below are about the seams —
the Application shared by three processes, the process-wide `VoiceEngines` singleton, scopes that
outlive the IME, and a second dictation state machine that bypasses the pure reducer.

Read within the stated 60-call budget; no static analysis or build was run.

## 1. `RescoreController` is launched on the application scope and holds the IME forever

**Severity:** high

**Evidence:** `app/src/main/java/helium314/keyboard/latin/LatinIME.java:906-912` (constructed with
`runtime.getAppScope()`, holding `this`), `app/src/main/java/helium314/keyboard/correct/RescoreController.kt:34-56`,
`LatinIME.java:717-721` (`onDestroy` tears down `mVoiceController` and `mAiFixKey`, never `mRescoreController`),
`app/src/main/java/helium314/keyboard/latin/App.kt:36-38` (the app scope lives as long as the process).

**Why it matters:** the controller captures the `LatinIME` instance and runs on a scope tied to the
`Application`. An IME instance is destroyed and recreated on theme/locale/config changes, so every
recreation leaks the previous service, its views and its input connection; and an in-flight rescore
can call back into a destroyed IME (`tail()` / the commit path both touch
`ime.currentInputConnection`) after the editor is gone.

**Recommendation:** give `LatinIME` its own `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`,
pass that to `RescoreController` (and to `AiFixKey`), and cancel it plus null out `mRescoreController`
in `LatinIME.onDestroy()`.

## 2. `AiFixKey`'s coroutine scope is never cancelled

**Severity:** high

**Evidence:** `app/src/main/java/helium314/keyboard/voice/AiFixKey.kt:38` (scope created per key),
`AiFixKey.kt:65` (`destroy()` only delegates to `controller.destroy()`),
`app/src/main/java/helium314/keyboard/correct/AiFixController.kt:203-208` (cancels `job`/`undoTimer`
and detaches, but does not cancel the scope it was handed).

**Why it matters:** the scope's `SupervisorJob` survives IME destruction while holding `AiFixKey`,
which holds `LatinIME`. Combined with finding 6 (an un-cancellable binder call inside the fix run),
a wedged model keeps a dead IME reachable for the life of the process.

**Recommendation:** either cancel `scope` in `AiFixKey.destroy()`, or (better, with finding 1) stop
creating a scope here and take the IME-owned scope as a constructor parameter.

## 3. `App.onCreate` runs the full keyboard bootstrap in the `:llm` and `:ui` processes

**Severity:** high

**Evidence:** `app/src/main/java/helium314/keyboard/latin/App.kt:54-81` — `Settings.init`,
`SubtypeSettings.init`, `SupportedEmojis.load`, `LayoutUtilsCustom.removeMissingLayouts`,
`checkVersionUpgrade(this)`, `transferOldPinnedClips(this)` run unconditionally; the manifest puts
`LlmRefinerService` in `:llm` (`app/src/main/AndroidManifest.xml:63-66`) and WorkManager's foreground
service in `:ui` (`AndroidManifest.xml:90-94`).

**Why it matters:** two of the three costs the process split exists to avoid come straight back —
binding the refiner starts a process that first loads emoji tables, subtype settings and layout
files before it can answer `refinerModelPath()`, which is exactly the latency the 2s bind timeout
(`llm/.../LlmRefinerClient.kt:150`) is measuring against. Worse, `checkVersionUpgrade` and
`transferOldPinnedClips` are one-shot prefs/file migrations now executing concurrently in up to three
processes on the first launch after an upgrade.

**Recommendation:** gate `onCreate` on the current process name — run only `DebugFlags.init` plus the
lazy `voiceRuntime` outside the main process, and keep the migrations and keyboard init in the main
process only.

## 4. A second, ad-hoc dictation state machine in `VoiceController` for the Google backend

**Severity:** high

**Evidence:** `core/src/main/kotlin/com/vboard/core/session/DictationStateMachine.kt:23-58` (the pure
reducer), `app/src/main/java/helium314/keyboard/voice/VoiceController.kt:54,61,70,96,99,102`
(`googleForSession`, `fallbackForSession`, `errorOnStrip`, `holdScoped`, `rawForSession`,
`minimizedForSession` — six booleans plus `isActive`), `VoiceController.kt:280,324-342,403,507-509`,
`VoiceController.kt:560-600` (the `GoogleVoiceSession.Host` callbacks drive the strip and commits
directly, never entering the reducer).

**Why it matters:** the local path's lifecycle is a tested reducer; the Google path re-implements the
same lifecycle in boolean flags on the UI-facing class, with none of the deferral rules the reducer
encodes (stop-during-finalize, focus-loss-then-commit). Every future dictation rule has to be written
twice, and only one copy is testable.

**Recommendation:** make `GoogleVoiceSession` feed `Event`s into a `DictationStateMachine` instance
(partials → `Partial`, final → `Endpoint`, errors → `AudioError`) so both backends share one reducer
and `VoiceController` keeps only `isActive` plus the backend choice.

## 5. `VoiceEngines` is a process-wide mutable singleton with a release that can be refused and never retried

**Severity:** high

**Evidence:** `voice/src/main/kotlin/com/vboard/app/voice/VoiceEngines` at
`voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:932-1090`, specifically
`releaseAll()`/`releaseRefiner()` at `:1020-1045` (return early when `claims.get() > 0`),
`scheduleIdleRelease()` at `:1078-1088` (fires once, does not reschedule on refusal), and the
`beginUse`/`endUse` callers spread across three modules (`VoiceSessionController.kt:383,646,765`,
`correct/AiFixController.kt:300`, `correct/RescoreController.kt:69`).

**Why it matters:** the comment says the alternative is holding ~1.2GB of native memory until the
process dies — which is exactly what happens whenever the idle timer fires while any claim is
outstanding (including a claim leaked by finding 6's un-cancellable binder call). The claim counter
is also an untyped global: a single unbalanced `endUse` anywhere frees a recognizer under a running
decode, which the code itself describes as a segfault.

**Recommendation:** in `releaseAll()`/`releaseRefiner()`, re-arm the idle timer instead of returning
(`scheduleIdleRelease()` in the refusal branch), and replace the raw `beginUse`/`endUse` pairs with a
single `suspend fun <T> withEngines(block: ...)` so claims cannot be leaked by an early return or a
cancellation.

## 6. Binder calls are blocking and un-cancellable, so caller timeouts leak threads and claims

**Severity:** high

**Evidence:** `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:102-115`
(`withContext(Dispatchers.IO) { block(service) }` around a synchronous AIDL call),
`llm/src/main/aidl/com/vboard/app/llm/ILlmRefiner.aidl` (no `oneway`, no callback),
`llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerService.kt:35-70` (`synchronized(engineLock)` +
`runBlocking` on the binder thread), `app/src/main/java/helium314/keyboard/correct/AiFixController.kt:296-315`
(`withTimeoutOrNull(TOTAL_BUDGET_MS)` wrapping that call, with `VoiceEngines.endUse()` in `finally`).

**Why it matters:** cancelling the coroutine does not interrupt the binder transaction. The `Dispatchers.IO`
thread stays parked for as long as the remote generate runs, `endUse()` runs while the call is still in
flight (so a release can disconnect mid-transaction), and repeated timeouts progressively consume the IO
pool. On the service side, `runBlocking` under a lock holds a binder thread, so concurrent callers
(dictation refine + AI fix + rescore all exist) queue on a pool of 16 and can exhaust it.

**Recommendation:** make the AIDL call `oneway` with a result callback interface, or at minimum add a
`cancel()` to `ILlmRefiner` and call it from the coroutine's `invokeOnCompletion` so a timeout actually
stops the remote work and releases the claim.

## 7. `LlmRefinerClient.pending` is unsynchronized across the binder callback and the connect path

**Severity:** medium

**Evidence:** `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:55` (`private var pending`,
not `@Volatile`, unlike `binder` and `bound`), written at `:121,135` under `connectLock` and at
`:61,69-70` from `ServiceConnection` callbacks on the main thread.

**Why it matters:** `onServiceConnected` runs on the main thread while `connect()` runs on a caller
thread holding only `connectLock`; the callback does not take that lock. A connect that times out at
`:134` clears `pending` while a late `onServiceConnected` may still complete it, and there is no
happens-before edge between the two writes. Related: when the bind times out, `bound` stays true and
the connection stays registered, so the framework keeps the `:llm` process alive on a device already
in trouble.

**Recommendation:** hold `connectLock` (or a plain `synchronized` block) in both `ServiceConnection`
callbacks and make `pending` `@Volatile`; on the `BIND_TIMEOUT_MS` path, call `disconnect()` before
returning null.

## 8. Pack installation state is per-process with no invalidation across the process boundary

**Severity:** medium

**Evidence:** `app/src/main/java/helium314/keyboard/latin/App.kt:36-41` (a `DefaultVoiceRuntime` is
built independently in each process), `voice/src/main/kotlin/com/vboard/app/voice/VoiceRuntime.kt:76-89`
(each builds its own `ModelStore` + `PackInstaller`), `voice/src/main/kotlin/com/vboard/app/models/ModelDownloadWorker.kt:43-48`
(the worker runs in `:ui` and mutates that state), `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerService.kt:93-98`
(`:llm` resolves the model path through its own `Application`).

**Why it matters:** three copies of the same state with the filesystem as the only channel between
them, and no signal when a download completes. `refinerClientOrNull` (`LlmRefinerClient.kt:176-183`)
decides "no refiner" from the keyboard process's view of the pack directory; nothing tells the
keyboard to re-evaluate after `:ui` finishes installing, so the feature stays invisible until
something else re-reads the store. Any future in-memory caching in `PackInstaller` turns this from a
staleness bug into a divergence bug.

**Recommendation:** have the download worker broadcast a pack-state change (a local broadcast or a
`ContentProvider` notify) and have `VoiceController`/`AiFixKey` re-run their readiness check on it.

## 9. `LatinIME` reaches past `:voice` straight into `:core`

**Severity:** medium

**Evidence:** `app/src/main/java/helium314/keyboard/latin/LatinIME.java:881-912` (`com.vboard.core.correct.SentenceCandidates`,
`WordSlot`, and the rescore entry point live in the IME class), `LatinIME.java:623-625` (the IME reads
`RescoreController.PREF_RESCORE_SENTENCES` from prefs itself).

**Why it matters:** the stated layering is that `helium314.keyboard.{voice,correct}` is the only place
that knows both worlds and that `LatinIME`'s own edits stay at the level of lifecycle calls
(`VoiceController.kt:3-5`). Domain types and a fork preference key in `LatinIME.java` enlarge the
upstream-merge surface for no gain, and the sentence-candidate buffer is IME state living in the
hardest class to merge.

**Recommendation:** move `mSentenceCandidates`, the preference read and `onSentenceComplete`'s body
into `RescoreController` (or a small `SentenceRescoreGate` in `helium314.keyboard.correct`), leaving
`LatinIME` with one forwarding call.

## 10. The app-side controllers are bound to the concrete `LatinIME` and cannot be tested

**Severity:** medium

**Evidence:** `app/src/main/java/helium314/keyboard/voice/VoiceController.kt:40-45`
(`private val ime: LatinIME`), `app/src/main/java/helium314/keyboard/correct/RescoreController.kt:34-37`,
`app/src/main/java/helium314/keyboard/voice/AiFixKey.kt:32-37` — contrast
`AiFixController` (`correct/AiFixController.kt:53-74`), which takes a `Host` interface and is testable.

**Why it matters:** `VoiceController` is the largest fork class in `app/` (662 lines) and holds the
entire backend-selection and error-recovery policy (finding 4), yet constructing it requires a live
IME. The `:voice` module was carefully given a `Host` seam; the app side then discarded it.

**Recommendation:** extract the handful of members these classes actually use from `LatinIME`
(`currentInputConnection`, `showCorrectionGhost`, strip visibility, `prefs`) into an `ImeSurface`
interface implemented by `LatinIME`, and take that instead of the concrete class.

## 11. Failures cross the process boundary as `Bundle` string keys rather than a typed result

**Severity:** low

**Evidence:** `llm/src/main/aidl/com/vboard/app/llm/ILlmRefiner.aidl` (`Bundle correct(...)`),
`llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerService.kt:58-70,101-102`,
`llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:81-89` (re-parses the enum by
`SmartFailure.entries.firstOrNull { it.name == name }`).

**Why it matters:** the enum name is now a wire format with no compile-time link between the two
sides — renaming a `SmartFailure` constant silently degrades every failure to `SmartFailure.ERROR`,
and `refine()`'s `null` collapses "no pack", "timed out" and "process died" into one answer the UI
cannot distinguish.

**Recommendation:** make the result a `Parcelable` `SmartOutput` (or pass an ordinal plus a version
int) so the failure taxonomy is checked at compile time on both sides.

## 12. `preload()`'s return value is discarded, so warm-up failures are invisible

**Severity:** low

**Evidence:** `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:74-76`
(`override suspend fun preload() { call { it.preload() } }` — result dropped, and the interface at
`:157` declares `suspend fun preload()` returning `Unit`), `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerService.kt:42-48`.

**Why it matters:** a preload that fails because the pack is missing, the engine threw, or the process
could not be bound is indistinguishable from one that succeeded, so the first real call pays the full
init cost and then fails — the exact latency the preload exists to avoid, with no signal available to
downgrade the UI in advance.

**Recommendation:** propagate the boolean through `RemoteRefiner.preload(): Boolean` and let the
caller mark the refiner unavailable for the session on `false`.

## Plan for #4 (not executed)

Folding the Google path into `DictationStateMachine` is a behaviour-changing rewrite of the
largest class in `app/`, so it is left as a plan rather than an unreviewed diff.

1. **Pin current behaviour first.** Before any move, characterise the Google path in tests: a
   `GoogleVoiceSession.Host` fake driving `VoiceController` through partial → final → commit,
   plus the four awkward cases the booleans encode (stop during finalize, focus loss before the
   final, an error after a partial, minimise while listening). These tests must pass unchanged
   after the move — they are the only evidence the fold is behaviour-preserving.
2. **Make the reducer backend-agnostic.** Check every `Event` in
   `core/.../session/DictationStateMachine.kt` against what the Google backend can actually
   report. The gap is likely to be "final arrived without an endpoint we asked for" and
   "recognizer restarted itself"; add the missing events to the reducer with tests, in `:core`,
   as a standalone commit that changes no app code.
3. **Introduce a seam, not a rewrite.** Give `VoiceController` one private
   `DictationStateMachine` instance and a single `dispatch(Event)` funnel that renders its
   output state to the strip and the input connection. Initially the local path uses it and the
   Google path still uses the booleans — no behaviour change, reviewable on its own.
4. **Move the Google callbacks over one at a time**, in this order: partials (lowest risk),
   errors, final/commit (highest risk, because the deferral rules live there). Each step deletes
   exactly one boolean and is a separate commit, with the step-1 tests green in between.
5. **Delete the residue.** When `googleForSession`, `fallbackForSession`, `errorOnStrip`,
   `holdScoped`, `rawForSession` and `minimizedForSession` are all gone, `VoiceController` keeps
   `isActive` plus the backend choice, and backend selection becomes a constructor argument —
   which also unblocks finding 10 (a `Host` seam instead of the concrete `LatinIME`).

Risk if done in one pass: the deferral rules are exactly where the user notices a regression
(text lost on stop, text committed into the wrong field), and they are invisible in a diff.
The staging above exists so each commit can be reverted alone.
