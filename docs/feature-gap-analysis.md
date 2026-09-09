# Feature gap analysis — SuperVoiceBoard vs Gboard and Samsung Keyboard

Dated 2026-09-09. Audited against the tree at `/home/user/SuperVoiceBoard`: `PLAN.md`,
`README.md`, `TODO.md`, `REVIEW.md`, every screen under
`app/src/main/java/helium314/keyboard/settings/screens/`, `ToolbarUtils.kt`,
`app/src/main/java/helium314/keyboard/voice/`, `voice/`, `llm/` and
`core/src/main/kotlin/com/vboard/core/model/ModelCatalog.kt`.

Gboard and Samsung Keyboard feature sets are from web search plus prior knowledge;
`samsung.com` is blocked by this environment's egress proxy, so the Samsung column
leans on secondary sources and my own knowledge rather than Samsung's own settings
enumeration. Treat individual Samsung cells as directionally right, not authoritative.

Two work items are in flight elsewhere and are excluded from the gap table by
instruction: **interactive keyboard resize** and a **Gboard-style theme picker**.

---

## 1. Verdict

The typing half is in better shape than the fork's own README implies — inheriting
HeliBoard 4.1 bought us autocorrect with a tunable confidence threshold, bigram
prediction, contacts and app-name dictionaries, a personal dictionary, a spell-check
service, 100+ layouts with per-subtype secondary locales, an emoji palette with search
and skin tones, clipboard history with pinning and image clips, split/floating/one-handed
modes, background images, custom colour themes, backup/restore, and a toolbar and cursor
touchpad that Gboard does not have at all. Against that, the voice layer is genuinely
differentiated: on-device dictation with cleanup, provisional commit, hold-to-talk and a
local LLM refiner is something neither competitor ships, and no competitor can, because
both are built to send speech to a server. **But the product is not shippable to a
mainstream user today, and the reasons are not subtle.** Glide typing — the single most
used input method on Android — is present in code and dead at runtime, because
HeliBoard gates it on a proprietary Google `.so` the user has to sideload
(`JniUtils.sHaveGestureLib`, default `false`; `TODO.md` W0.3 records it was never
verified); an open Kotlin decoder that removes that gate is being built, opt-in for
its first release. Dictation is English-only and does nothing until the user has fetched
610 MB, so the one feature we exist for is invisible on first run and unavailable to
most of the planet. And there is no published build at all — no Play Store listing,
no F-Droid entry, CI artifacts only. Everything below that, we compete on. Those four
things, we do not compete at all.

---

## 2. Gap table

Only gaps are listed. Features where we are at parity or ahead are in §4 so the table
stays readable.

Priority: **P0** = a mainstream user abandons the keyboard without it. **P1** = they
notice within a week and it costs us retention. **P2** = they notice eventually, or it
costs us a segment. **P3** = nice to have, or a long-tail segment.
Effort: **S** ≤ 1 week, **M** ≤ 1 month, **L** > 1 month or needs a licence/asset we
do not have.

Network? answers what *our* implementation would need at runtime, not what the competitors do — "Download only" means a one-time model fetch in the `:ui` process, never a request from the keyboard itself.

| Feature | Gboard | Samsung | Us | Pri | Eff | Network? | Why it matters | What can be done |
|---|---|---|---|---|---|---|---|---|
| Glide / swipe typing that works on install | Yes, default | Yes, default | Native path gated on a user-supplied proprietary lib; open Kotlin decoder being built, opt-in | P0 | L | No | Roughly half of Android users swipe. Ours silently does nothing, and the settings screen (`GestureTypingScreen.kt`) advertised seven tuning options for a feature that never runs. | Decided, A-sliced: an open Kotlin decoder written clean-room from the SHARK² paper (not a FlorisBoard port — the classifier is about a third of the work and carrying an Apache-2.0 header through a file we intend to rewrite is the worse trade), current locale only, one word decoded on `onEndBatchInput`, off the UI thread, memory capped at the top 60k words of one locale with templates dropped on locale or layout change. No new preference: `PREF_GESTURE_INPUT` is the single master switch, flipped to default `false`, and the gesture entry appears when `sHaveGestureLib \|\| GlideDecoder.isAvailable()`. Pass one lands opt-in whatever the accuracy turns out to be, because it cannot be measured in this repo; the bar for flipping the default later is top-1 exact match ≥ 85% over the 5,000 most frequent en-US words traced on a device and p95 decode under 100 ms. |
| Dictation in languages other than English | 100+ languages, dozens offline | Many, incl. offline packs | English only (`ModelCatalog.kt` ships two English packs) | P0 | L | Download only | The product's entire reason to exist is unavailable to any non-English speaker, while typing works for them in 100+ layouts. They will use Gboard for the one thing we are better at. | Extend `ModelCatalog.kt` with multilingual packs and pick the pack from the active subtype's locale. Start with the five largest install bases rather than all at once. |
| Voice usable immediately after install | Instant online; offline pack ~50 MB | Instant | Mic is inert until 610 MB downloads (~2 GB with the refiner) | P0 | M | Yes, until the pack lands | First-run experience is a mic key that does nothing. Needs either a small first-run model, or defaulting to the system recogniser until packs land, with the on-device pack as the upgrade. | Fall back to the system `SpeechRecognizer` — the `GoogleVoiceSession` path already exists — until an on-device pack finishes downloading, and ship one small pack as the first-run default. |
| A build a user can install | Play Store | Preinstalled | No published build; CI artifacts only (`README.md` Status) | P0 | M | n/a | There is currently no path from "wants this" to "has this". Includes store assets — the repo has no screenshot of the voice strip. | Sign a release, publish to GitHub Releases and F-Droid, and fill the existing `fastlane/` metadata. Capture the voice-strip and theme-picker screenshots the listing needs. |
| Correction quality from a neural/LLM model | On-device LLM autocorrect | One UI 8 "AI autocorrect" | 2015-era AOSP n-gram JNI decoder | P1 | L | No | Typing accuracy is the first thing anyone judges a keyboard on, and both competitors have re-based theirs on a model in the last two years. We have a 0.5B model in-process already; the question is whether it can be brought to bear on typing, not just dictation. | Run the in-process 0.5B refiner over the committed sentence on a debounce, behind a setting. Prove it on latency and battery before it becomes the default. |
| Inline translation while typing | Yes, 120+ languages | Yes, "translation input" | None | P1 | M | Download only | Daily tool for bilingual users, and the one competitor feature with a clean on-device story (ML Kit offline pairs, ~30 MB each) that fits our no-network-in-the-IME constraint rather than fighting it. | Add ML Kit on-device translation in the `:ui` process with per-pair downloads, surfaced as a toolbar key. No network reaches the IME process. |
| Tone / style rewriting | Partial (Magic Compose, outside the keyboard) | Yes, Writing Assist: tone, grammar, phrasing | AI fix corrects but does not restyle | P1 | S | No | The LLM, the process split, the toolbar key, the attribution UI and the undo bookkeeping all exist (`AiFixController.kt`). Adding "make this formal/casual/shorter" is prompt and menu work, and it is the cheapest way to match Samsung's headline AI feature. | Add a prompt menu to `AiFixController` — formal, casual, shorter — reusing its existing attribution UI and undo bookkeeping. Days of work, not weeks. |
| Field diagnostics for crashes | Google-scale telemetry | Samsung telemetry | None; `VoiceMetrics` is memory-only and off by default (R24) | P1 | S | No | We ship a keyboard with three processes, 3.8k lines of untested concurrency (`REVIEW.md`) and no way to learn that it died on a user's phone. A crash stack is not user content, so this does not breach §3.4. | Add opt-in crash reporting that writes a stack trace to a file and offers a share sheet, or bundle ACRA. Stacks only, never user content. |
| Stylus handwriting → text | Yes, plus editing gestures (strike to delete, circle to select) | Yes, S Pen handwriting mode | None | P2 | L | Download only | Table stakes on tablets and any S Pen device; irrelevant on a mid-range phone. Segment gap, not a mainstream one. | Defer. If a tablet push happens, ML Kit Digital Ink recognition in `:ui` is the cheapest route in. |
| GIF search | Yes | Yes | None | P2 | L | Yes | Heavily used in messaging, but every major messaging app has its own picker, and a Tenor integration means network, a third-party ToS and a content pipeline in `:ui` — expensive and off-strategy for a privacy-first keyboard. | Do not build. Point users at their messaging app's own picker; revisit only if a partner requires it. |
| Sentence-level prediction (Smart Compose) | Yes, LLM-backed | Predictive text | Bigram next-word from dictionaries only | P2 | M | No | Visible quality difference in long-form typing. Shares a foundation with the correction-quality row above; do them together or not at all. | Fold into the correction-quality work above — same model, same pass, one setting. Do not schedule separately. |
| Spoken punctuation, formatting and emoji commands | Yes ("comma", "new line", "smile emoji") | Partial | Spoken commands and auto-punctuation exist; coverage against Gboard's vocabulary is unverified | P2 | S | No | Dictation that cannot produce a newline or a question mark on command reads as a demo. Cheap to close because the command layer is already in `:core`. | Diff our `:core` command vocabulary against Gboard's published list, add the missing tokens, and cover each with a unit test in `:core`. |
| Automatic language detection while typing | Yes, across enabled languages | Yes | Requires configuring a secondary locale per subtype (`SubtypeScreen.kt:155`) | P2 | M | No | We have the capability and hide it behind configuration most users will never find. Half a fix is a discoverability fix. | Turn multilingual decoding on by default once two or more subtypes are enabled, instead of requiring a per-subtype secondary locale. |
| High-contrast / accessibility presets | Partial, via themes | Yes, explicit "High contrast keyboard" | Achievable by hand-editing custom colours; no preset | P2 | S | No | One preset in the appearance screen; the colour system already supports it. Low cost, real users. | Add a built-in high-contrast theme to the new theme picker. The colour system already carries everything it needs. |
| Stickers and sticker suggestions | Yes | Yes | None | P3 | L | Yes | Same network/content-pipeline cost as GIF, less usage. | Do not build. Same content pipeline as GIF, less usage. |
| Emoji Kitchen / AR emoji | Emoji Kitchen | AR Emoji stickers | None | P3 | L | Yes | Fun, proprietary, and not why anyone switches keyboards. | Do not build. Proprietary and not a switching reason. |
| Cross-device clipboard sync | Yes, via account | Yes, Samsung Cloud / Link to Windows | None — see §3 | P3 | L | Yes | Requires an account and a server. Structurally excluded by our own constraints. | Do not build. It needs an account and a server, which PLAN.md rules out. |
| In-keyboard web search | Yes | Regional | None | P3 | M | Yes | Requires network in the keyboard and is the feature most users disable. | Do not build. Network in the keyboard is the constraint we chose against. |
| Text scanning / OCR | No | Yes, "Scan text" | None | P3 | M | No | Samsung-only, camera permission, narrow use. | Do not build. Camera permission for a narrow, Samsung-only habit. |
| Settings and personal-dictionary sync | Yes, via account | Yes | Manual backup/restore file (`BackupRestorePreference.kt`) | P3 | L | No | Account-gated; our file-based backup covers the migration case, which is most of the value. | Keep the file-based backup and make it discoverable at first run. Account sync stays out. |
| Physical / Bluetooth keyboard support | Full | Full | Partial (`PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY` only) | P3 | M | No | Tablet and desktop-mode segment. | Map the common modifier shortcuts (select-all, copy, paste, word jump) for a hardware keyboard. Incremental and testable. |
| Non-English UI strings still branded HeliBoard | n/a | n/a | ~100 `values-*/strings.xml` untouched (R2) | P3 | S | No | Every non-English user sees a different product's name in settings. Deliberate at the time, but it is now a trust problem, not a merge-hygiene one. | Script the app-name string across the ~100 `values-*/strings.xml` files. One pass, mechanical, reviewable in a single diff. |

**Counts: 4 P0, 4 P1, 7 P2, 8 P3.**

### Not in this table: quality blockers

`REVIEW.md` (2026-09-07) already lists two P0 defects — an unverified ~500 MB executable
model behind a mutable Hugging Face ref, and `VoiceController.replaceUtterance` deleting
characters the user typed after a commit — plus a keyboard-killing uncaught-coroutine
path. They are not feature gaps and are not restated here, but they gate the same
launch the P0 rows above do. Nothing about speed, accuracy, battery or memory under
real dictation has been measured, which is its own launch blocker.

---

## 3. Deliberate non-goals

These are decisions in `PLAN.md`, not gaps. Anyone re-raising them should argue with
the decision, not with the absence.

- **Network in the keyboard process.** §3.3 and R8: `INTERNET` is held by `:ui` only,
  asserted at build time by `ManifestProcessSplitTest`. This is what rules out live GIF
  search, stickers, cloud translation and in-keyboard web search as ordinary features —
  any of them has to be built through `:ui` or not at all.
- **Any account, API key or paid service** (`README.md`). This is what rules out
  cross-device clipboard sync and settings/dictionary sync, not an oversight.
- **Transmitted telemetry.** R24: opt-in metrics are memory-only, no endpoint, no file.
  Deliberate — shipping collection before a privacy policy and consent flow was judged
  the wrong half to build first. (The P1 crash-diagnostics row above is a narrower ask:
  stacks, not content.)
- **Logging user content, ever** (§3.4). No transcript, clipboard or typed text in any
  log, crash report or metric, in any build type.
- **A separate voice bar** (§2). Voice is the fourth mode of `strip_container`, alongside
  suggestions, emoji and clipboard. VBoard's `VoiceBarView` was not ported.
- **Two-model confidence and disagreement marking.** R27 cancelled W7.1 outright; the
  committed text is the accurate model's answer, and the effort went into latency
  instead. `TranscriptAlignment` was deleted, not parked.
- **Shortening the 0.8 s / 2.4 s endpoint thresholds.** R27: it would cut people off
  mid-sentence, and hold-to-talk already removes the wait for users who care.
- **Cloud dictation as a default.** Google voice typing exists but sits behind the
  "privacy breaking" screen (`PrivacyBreakingScreen.kt`), alongside the Google password
  manager, as an explicit opt-in.
- **Renaming the `helium314.keyboard` namespace** (§3.2), and any reformatting of
  upstream files. Rebaseability on HeliBoard is the fork's survival condition.
- **Writing our own keyboard.** §1 and §4: VBoard's `app/keyboard/` and its IME service
  were deleted, not ported. We are a voice layer mounted in a keyboard that works.

---

## 4. Where we are at parity or ahead

Recorded so nobody re-litigates it as a gap.

**At parity:** autocorrect with confidence tuning, next-word prediction, personal and
contacts dictionaries, spell-check service, offensive-word blocking, auto-capitalisation
and autospace rules, 100+ layouts, number row and localised number row, emoji palette
with recents/skin tone/search/inline search and typed emoji suggestions, clipboard
history with pinning, retention and image clips via `commitContent`, split / floating /
one-handed modes, day-night themes with custom colours and background images, keyboard
height and padding scaling, sound and vibration control, incognito mode, TalkBack
support across the key grid and the voice row, setup wizard, backup and restore.

**Ahead:** on-device dictation with transcript cleanup, spoken-format handling, draft
rescue and provisional commit; a local LLM refiner in its own process with an AI fix key
pinned by default and change attribution; a fully customisable toolbar with undo/redo,
select-word and cursor keys (Gboard has no equivalent); a cursor touchpad with
sensitivity and edge-scroll; space-bar swipe and delete-swipe gestures; per-app subtype
memory; user-importable layouts and dictionaries; and a keyboard process that holds no
network permission at all.
