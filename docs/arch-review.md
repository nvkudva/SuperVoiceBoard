# Architecture review — 2026-09-09

Three read-only agents, non-overlapping scopes: `:core`/`:voice`/`:llm`, the IME runtime
(`latin/` + `keyboard/`), and settings + build config. 36 findings, ranked within each group.
`(S|M|L)` is effort. `[upstream]` marks work that touches upstream code broadly and costs
future merge pain.

## 1. Correctness-adjacent leaks — do first

- [x] PERF `llm/src/main/kotlin/com/vboard/app/llm/LlmRefinerClient.kt:87` — `disconnect()` no-ops when `binder == null`, so after `onServiceDisconnected` the ServiceConnection stays registered and the framework silently rebinds the 0.5B `:llm` process forever → track bound state separately from the binder and always `unbindService` (S)
- [x] PERF `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:662` — `refineAsync` launches an untracked coroutine holding a `beginUse` claim that can `replaceUtterance` after the session was cancelled → track a `refineJob` and cancel it in `cancelSession`/`cancelSessionSilently`/`destroy` (S)
- [x] PERF `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:102` — `teardownScope` is never cancelled in `destroy()` and the three process-wide single-thread executors (785-801) are never shut down → cancel the scope after final release and share one executor pool (S)

## 2. Hot-path performance

- [x] PERF `app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java:2466` — `recordSentenceCandidates` allocates an ArrayList and copies every suggestion string on each word commit even when rescoring is off → guard on a cached enabled-flag, reuse one scratch list (S)
- [x] PERF `app/src/main/java/helium314/keyboard/latin/LatinIME.java:865` — `onSentenceComplete` reads SharedPreferences on the main thread per committed sentence → hoist the flag into `SettingsValues`, refresh on pref change (S)
- [ ] PERF `app/src/main/java/helium314/keyboard/latin/LatinIME.java:1630` — every keystroke makes two cross-module calls that are no-ops in the common case → gate them behind a volatile "something pending" flag (S)
- [x] PERF `core/src/main/kotlin/com/vboard/core/text/SpokenFormats.kt:47` — `Regex(...)` compiled inside `spokenAddresses`, `money`, `clockTimes` (92, 147, 160) on every utterance → hoist all patterns to private vals (S)
- [x] PERF `core/src/main/kotlin/com/vboard/core/suggest/SuggestionEngine.kt:99` — `predictNextWord` re-walks `lexicon.wordsWithPrefix("", PREDICTION_FILLER_COUNT)` per committed word for an invariant result → compute once, cache on the Lexicon (S)
- [ ] PERF `app/src/main/java/helium314/keyboard/keyboard/KeyboardResizeOverlayView.kt:157` — a resize drag writes a pref and calls `reloadKeyboard()` (layout parse + inflation + theme rebuild) every 50 ms → transform the wrapper view live, persist and reload once on ACTION_UP (M)
- [ ] PERF `app/src/main/java/helium314/keyboard/latin/LatinIME.java:948` — `onStartInputView` always calls `voiceController()`, so the first keyboard show builds VoiceController + VoiceRuntime on the main thread → construct on first mic/AI-fix use (M)
- [x] PERF `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:255` — every mic press re-runs `ensureExtracted` over all non-refiner packs before `ModelsReady` → memoize verified extraction per pack version in `ModelStore` (S)
- [x] PERF `voice/src/main/kotlin/com/vboard/app/models/ModelStore.kt:138` — `parakeetPaths`/`dictationReady` do uncached filesystem checks and a `findTransducer` scan on every call → cache resolved paths keyed by installed pack version, invalidate on install/extract (M)

## 3. Settings UI performance

- [ ] PERF `app/src/main/java/helium314/keyboard/settings/preferences/SwitchPreference.kt:56` — preferences rely on a global `prefChanged` counter (21 call sites) so one toggle recomposes every setting on the screen → a per-key `rememberPref(key, default)` backed by SharedPreferences, delete the counter (M)
- [x] PERF `app/src/main/java/helium314/keyboard/settings/screens/ThemePickerScreen.kt:100` — `prefs.all` (full copy of every entry) plus `getAvailableDefaultColors` run unremembered in the composable body → hoist into `remember(b?.value, isNight)` (S)
- [x] PERF `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt:85` — `SettingsContainer(this)` eagerly resolves title and description strings for every setting on every screen before the first frame → make them lazy, or build off the main thread / on first search (M)

## 4. Dead code and simplification

- [x] SIMPLIFY `core/src/main/kotlin/com/vboard/core/session/DictationStateMachine.kt:187` — `Event.Partial` has no producer, so `Listening.partial` is always blank, the guard at `VoiceSessionController.kt:198` never fires and `Effect.BeginFinalize`, `provisionalCommit` and `FinalTranscriptPolicy`'s partial fallback are unreachable → feed partials or delete the partial-carrying state and finalize unconditionally on stop (M)
- [ ] SIMPLIFY `core/src/main/kotlin/com/vboard/core/session/AudioPipeline.kt:65` — the decode-queue half (Channel, admission control, drop counters, `drainDroppedSamples`) exists only to feed `decodeLoop`, now a no-op drain at `VoiceSessionController.kt:442` → collapse to the utterance ring buffer, delete `Event.AudioOverrun`, `Effect.NoteAudioOverrun`, `streamDispatcher` (M)
- [x] SIMPLIFY `core/src/main/kotlin/com/vboard/core/session/DictationStateMachine.kt:128` — `Effect.Haptic` is built on 8 transitions and executed as `Unit` because views emit their own haptics → delete `Effect.Haptic` and `HapticKind` (S)
- [ ] SIMPLIFY `app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt:142` — `ToolbarKey.VOICE` is retained but filtered out by `hiddenToolbarKeys` in four places while keeping a keycode mapping and three icons → delete the enum member and the filter set (S)
- [x] SIMPLIFY `app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt:545` — the hold gesture fires `onMicHoldStart` twice per press (350 ms, 1200 ms) and the local `raw` flag is written but never read → one callback carrying the mode, drop the dead variable (S)
- [x] SIMPLIFY `app/src/main/res/values/strings_glide.xml:6` — `glide_decoder_unavailable`, `glide_native_active`, `glide_first_swipe_hint`, `glide_first_swipe_hint_action`, `theme_page_name` are unreferenced and being translated into 117 locales → delete (S)
- [x] SIMPLIFY `app/src/main/java/helium314/keyboard/settings/SearchScreen.kt:125` — a commented-out `LazyColumn` alternative keeps three imports alive → delete the block and imports, keep one comment line (S)
- [x] SIMPLIFY `app/src/main/java/helium314/keyboard/latin/LatinIME.java:850` — an orphaned javadoc block sits above `onSentenceComplete`'s own javadoc → move it onto `showCorrectionGhost` (S)

## 5. Organisation

- [ ] ORG `voice/src/main/kotlin/com/vboard/app/voice/VoiceSessionController.kt:69` — 970-line god object owning capture lifecycle, watchdog, effect execution, cleanup, refinement and the process-wide `VoiceEngines` singleton → split into session reducer host, audio driver, engine cache (L)
- [ ] ORG `app/src/main/java/helium314/keyboard/latin/LatinIME.java:141` — LatinIME owns VoiceController, VoiceStripView, GhostSwapView, RescoreController and AiFixKey directly, ~180 fork lines inside upstream's second-largest class → one `VoiceIntegration` facade in `:voice`, ~10 upstream lines left (M) [upstream]
- [ ] ORG `app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java:105` — a public mutable `SentenceCandidates` field embedded in upstream's hottest class ties core types to InputLogic's lifecycle → a nullable listener the fork registers, buffer stays in `:core` (M) [upstream]
- [x] ORG `app/src/main/java/helium314/keyboard/latin/LatinIME.java:157` — a `static WeakReference<LatinIME>` exists only so settings can reach voice metrics → keep the aggregate in VoiceRuntime, have settings read the runtime (S)
- [ ] ORG `voice/src/main/kotlin/com/vboard/app/correct/AiFixController.kt:52` — typed-text AI-fix orchestration (InputConnection rewrites, undo timers, snackbars) sits in the ASR module and shares nothing with dictation → move to the IME correction layer (M)
- [ ] ORG `app/src/main/java/helium314/keyboard/settings/SettingsNavHost.kt:74` — adding one screen needs edits in four places, including a 14-callback `MainSettingsScreen` signature → pass the NavController or a single `navigate: (String) -> Unit`, register screens from one descriptor list (M)
- [x] ORG `app/src/main/res/values/strings.xml:357` — 82 fork-only `voice_*`, `settings_screen_voice*`, `privacy_breaking_*` strings sit in upstream `strings.xml`, against the stated mergeability policy → move to `strings_voice.xml` / `strings_privacy.xml` (M)
- [x] ORG `app/src/main/AndroidManifest.xml:117` — `SettingsActivity`/`SettingsActivity2` carry no `android:process=":ui"`, so Compose + navigation + material3 load into the IME process; only `SystemForegroundService` is pinned → pin the settings activities and download entry points, or correct the comment and `ManifestProcessSplitTest` (M)
- [x] ORG `app/src/main/java/helium314/keyboard/accessibility/KeyCodeDescriptionMapper.kt:32` — AI_FIX, AI_FIX_ATTRIBUTION, TOGGLE_ASR_ENGINE and TOGGLE_RESIZE_MODE have no spoken description, so TalkBack reads raw codes → add four `put(...)` entries (S)

## 6. Build

- [x] BUILD `app/build.gradle.kts:80` — `isShrinkResources = false` on `release` and `nouserlib` while 117 locale dirs and the whole Compose resource set ship in every ABI split → enable resource shrinking and `localeFilters` (S)
- [x] BUILD `gradle.properties:2` — no `org.gradle.parallel`, `org.gradle.caching` or `kotlin.incremental` across a 5-module build producing 5 ABI splits per variant → add them (S)
- [x] BUILD `app/build.gradle.kts:90` — `debug` runs R8 plus a per-variant proguard rewrite, and five build types each fan out to 5 ABI outputs → drop `runTests`/`nouserlib` if CI does not use them, keep minification off for local debug (S)
- [x] BUILD `build.gradle.kts:12` — no version catalog; compileSdk 36, minSdk 21, JVM 17, Kotlin 2.3.20, coroutines 1.10.2 repeated across four modules → `gradle/libs.versions.toml` plus a convention plugin (M)

## Not done, and why

- `LatinIME.java:1630` per-keystroke calls — already null-guarded; a "pending" flag would duplicate state across modules to save two virtual calls.
- `ToolbarUtils.kt:142` ToolbarKey.VOICE — not dead: SuggestionStripView resolves the strip's mic icon through `ToolbarKey.VOICE.name`.
- `KeyboardResizeOverlayView.kt:157` resize reloads — already throttled to 50 ms with the lift always reloading; the proposed live view transform would distort key geometry and hit-testing.
- `AudioPipeline.kt:65` decode queue — the queue is what tells the pipeline how much audio has been consumed, and it is the seat a returning streaming recognizer needs. Deleting it now means writing it again.
- `DictationStateMachine.kt:187` partial state — kept for the same reason. The unreachable finalize it caused is fixed instead; the "provisional commit" setting stays a no-op until something feeds partials.
- `SwitchPreference.kt:56` prefChanged counter — upstream plumbing across 21 call sites, no user-visible win, and a large merge liability.
- `SettingsNavHost.kt:74` screen registration — upstream navigation; the win is authoring ergonomics for a screen added a few times a year.
- `AndroidManifest.xml:117` settings in `:ui` — would split the app across a SharedPreferences instance that is not multi-process safe. The manifest now says so. WorkManager already runs its workers in `:ui`, so the network split the review doubted is intact.
- `AiFixController.kt:52` module move — it shares VoiceEngines with dictation, so "shares nothing" does not hold; the move would drag its resources across modules for no user-visible gain.
- `LatinIME.java:141` VoiceIntegration facade and `InputLogic.java:105` listener — the two that would genuinely reduce the upstream diff, and the two with no test coverage to catch a mistake. They want a device and a session of their own.
- `VoiceSessionController.kt:69` split (L) — same: worth doing, not worth doing blind.
