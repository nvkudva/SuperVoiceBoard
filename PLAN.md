# SuperVoiceBoard — plan of record

Architecture, decisions and constraints. **No task state lives here** — that is
[TODO.md](TODO.md).

Started 2026-08-31. Supersedes the VBoard V2 plan
(`~/dev/projects/VBoard/PLAN.md`), which is retired: VBoard's hand-rolled
`app/keyboard/` could never reach Gboard parity, and everything below assumes it
is thrown away rather than ported.

---

## 1. What this is

A Gboard clone — the *typing* half taken wholesale from a maintained AOSP
LatinIME fork — with VBoard's on-device voice intelligence grafted on as the
differentiator. We are not writing a keyboard. We are writing a voice layer and
mounting it inside one that already works.

**Base: [HeliBoard](https://github.com/HeliBorg/HeliBoard) 4.1** (9f5bb63,
2026-08-30). Same AOSP LatinIME ancestor Gboard itself was built from; ~33k LOC
Java (the LatinIME inheritance, incl. the JNI decoder) + ~32k Kotlin (the
modernization), compileSdk 36, minSdk 21.

### Why HeliBoard and not the alternatives

| Candidate | Verdict |
|---|---|
| **HeliBoard** | Chosen. Already implements the interaction we are chasing — see §2. Actively maintained (commits this week). Glide typing, emoji panel, clipboard history, themes, one-handed mode, 100+ layouts all present. |
| AOSP LatinIME direct | Rejected. Apache-2.0 and would let us stay proprietary, but it is the ~2015 drop: NDK decoder, Gradle, edge-to-edge and API-35 behaviour all need rebuilding. HeliBoard spent ~2,500 commits doing exactly that; redoing it buys a license, not a product. |
| FUTO Keyboard | Rejected as a base. Closest existing thing to our product (LatinIME + on-device voice), but Source First 1.1 — non-commercial, modification for personal use only. Legally unusable. Still worth *reading* for how they mounted voice. |
| FlorisBoard | Rejected. Still beta with no word suggestions or spell check, and its own design language — the opposite of a Gboard clone. |
| Keep VBoard's keyboard | Rejected. 3.6k LOC of `app/keyboard/` with no glide typing, no dictionaries, no full emoji palette. "Exact clone" is not reachable from there. |

---

## 2. The interaction we are cloning, and why the base already has it

The brief: *toolbar and suggestions blended in one space at the top of the
keyboard; mic on the right of that row; activating it swaps the row into voice
controls — back, minimize keyboard, done.*

HeliBoard's `res/layout/strip_container.xml` is a `FrameLayout` that already
swaps **three** views through that one row:

- `SuggestionStripView` — itself a single `LinearLayout` holding the toolbar
  expand key, a `toolbar_container`, and the suggestion words. Toolbar keys can
  be *pinned* so they persist onto the normal suggestion strip. This **is** the
  blended row, shipped and working.
- `emoji_tab_strip` — the row when the emoji panel is open.
- `clipboard_strip` — the row when the clipboard panel is open.

**The core architectural bet of this project: voice is the fourth mode of that
row.** We are not adding a bar. We are adding a `VoiceStripView` sibling and a
state transition, which is precisely the Gboard model and costs us the layout
work rather than the interaction design.

### Decision: no separate voice bar

VBoard's `VoiceBarView` is not ported. It existed because VBoard had no strip to
mount into. Mounting into `strip_container` instead is what makes the result
read as Gboard rather than as a keyboard with a voice accessory.

---

## 3. Constraints

### 3.1 GPL-3.0 — accepted, and it binds everything

HeliBoard is GPL-3.0-only (with Apache-2.0 and CC-BY-SA-4.0 for inherited
parts). SuperVoiceBoard ships as GPL-3.0. **The voice intelligence carried over
from VBoard is published with it** — `core/`, the ASR session logic, the
refiner. That was decided knowingly on 2026-08-31; it is not revisitable
per-file. Upstream `LICENSE*` files stay, attribution to HeliBoard and to AOSP
stays, and every source file we modify keeps its SPDX header.

### 3.2 Stay rebaseable on upstream

This fork lives or dies on being able to pull HeliBoard's fixes. Therefore:

- **Do not rename the `helium314.keyboard` package namespace.** Only
  `applicationId`, app label and icon change. A namespace rename touches every
  one of 65k lines and makes every future merge a conflict.
- New code goes in **new files and new Gradle modules**, not inside upstream
  files, wherever that is possible at all.
- Edits to upstream files are surgical and commented with why. No reformatting,
  no drive-by refactors, no style migrations. An upstream file we reformatted is
  an upstream file we can no longer merge.
- `upstream` remote is configured from day one and rebased on deliberately, not
  incidentally.

### 3.3 The permission departure

HeliBoard's identity is that it requests **no INTERNET and no RECORD_AUDIO**.
We must break both. This is the single largest deviation from the base and is
stated up front rather than discovered by a user:

- `RECORD_AUDIO` — the IME process. Unavoidable; it is the product.
- `INTERNET` — **the `:ui` process only**, for model download. The IME process
  must never hold it. Enforced by manifest process attributes and audited.

### 3.4 Inherited from VBoard, non-negotiable

- **Never log user content.** No transcript, no clipboard text, no typed text in
  any log, crash report or metric, in any build type.
- The `:llm` refiner runs **out of the keyboard process**. A 0.5B model OOM must
  not take down the user's ability to type in every app on the device. VBoard
  landed this (Wave 0.5) and the reasoning is unchanged.
- Typing-only memory budget ≤60MB in the IME process.

---

## 4. What comes across from VBoard, and what does not

| VBoard | LOC | Disposition |
|---|---|---|
| `core/` (text, session, suggest, correct, clipboard, model, keyboard) | 15.9k | **Ported whole.** Pure Kotlin, zero Android deps, only coroutines, no `app` imports — it drops in as a `:core` Gradle module unchanged, tests and all. This is the asset the whole project exists to keep. |
| `app/voice/` (AsrEngines, VoiceSessionController) | 1.9k | Ported into a new `:voice` module. Android-dependent but IME-agnostic. |
| `app/models/` (download, storage, lifecycle) | 1.0k | Ported into `:voice`. |
| `app/correct/` (AiFixController) | 0.6k | Ported; re-mounted on a pinned toolbar key. |
| `app/llm/` + `ILlmRefiner.aidl` | 0.3k | Ported as the `:llm` module/process, boundary intact. |
| `app/settings/`, `app/onboarding/` | 2.0k | **Partially.** HeliBoard has its own settings and setup wizard; we add voice/model screens into it rather than carrying VBoard's. |
| `app/keyboard/` | 3.6k | **Deleted.** Replaced by HeliBoard's keyboard. |
| `app/ime/VBoardImeService.kt` | 1.2k | **Deleted.** Replaced by `LatinIME.java`; the voice hooks are re-attached to it. |

Net: ~19.7k LOC of intelligence kept, ~4.8k LOC of keyboard discarded.

---

## 5. Open questions

- **Where does `:core` sit relative to the native decoder?** HeliBoard's
  suggestions come from a C++ JNI decoder (`jni/src/suggest`). Our
  `core/suggest/SuggestionEngine` was written for a keyboard with no such thing.
  These have to be composed, not merged — the native decoder stays authoritative
  for typing; ours contributes for dictated text. Settled in W4, not before.
- **Does Gboard keep the keyboard visible during dictation?** It does, with the
  row swapped and a mic indicator; verify against a real device before building
  W3 rather than from memory.
- **Two-model confidence (VBoard W1.1) is unproven** and stays unbuilt here
  until the alignment/normalization foundation exists. It is carried into
  TODO.md at the bottom, not the top.

---

## Revisions

_(append below; do not edit decisions above in place)_

### R1 — 2026-08-31: git history is kept, not squashed (amends W0.1)

TODO W0.1 called for dropping HeliBoard's history to a single squashed base
commit. We kept the full history instead and renamed the `origin` remote to
`upstream`. Reason: §3.2 makes rebaseability on upstream the fork's survival
condition, and a squashed base makes every `git rebase upstream/main` re-derive
merge bases it could otherwise read directly. The base SHA anchor W0.1 wanted is
still recorded — `9f5bb63` — in the fork's first commit message and in README.
Nothing about the fork's licensing or attribution changes.

### R2 — 2026-08-31: rebrand scope (W0.2)

`applicationId` is `com.supervoiceboard.app`; the `helium314.keyboard` namespace
is untouched, per §3.2. Also changed, because they are global identifiers that
would otherwise collide with an installed HeliBoard: the two content-provider
authorities (`clip_provider.xml`, `gesture_data.xml`). The launcher icon keeps
HeliBoard's adaptive-icon structure and gradient geometry with new colors and a
new foreground mark, so it stays a derivative work under CC-BY-SA-4.0 and the
attribution stays. Only the *base* `values/strings.xml` was rebranded; the ~100
translated `values-*/strings.xml` still say "HeliBoard Spell Checker" in their
own languages and are deliberately left for upstream to carry.

### R3 — 2026-08-31: `:core` keeps the `com.vboard.core` package (W1.1)

The module is ported verbatim, package names included. Renaming to
`com.supervoiceboard.core` would touch all 78 files and every test on the way
in, which is exactly the diff W1.1 exists to avoid — the point of the gate is
that the tests pass *unchanged*. The Gradle module is `:core` and its build file
is rewritten (SuperVoiceBoard has no version catalog; versions are literal).
A rename, if wanted, is a separate mechanical commit after the module is wired.

### R4 — 2026-08-31: W1.2 and W1.3 arrived already fixed; closed with tests

VBoard's TODO listed both `ClipClassifier` defects as open, but the source we
ported already carries the fixes: `DIGIT_RUN_PATTERN`'s separator class is
`[\p{Zs}\p{Pd}]` and the card rule reads the invisible-stripped text. What was
missing was coverage, so the fix could silently regress. Regression tests now
pin both shapes — NBSP/narrow-NBSP/thin-space/en-dash/em-dash grouping, a card
carrying ZWSP/ZWNJ/BOM/soft-hyphen, and cards and OTPs written in Arabic-Indic
digits.

Writing them found one real gap: U+2212 MINUS SIGN is category Sm, not Pd, so a
card grouped with it evaded Luhn. It is now listed explicitly in the separator
class. This is the only change to ported `:core` logic besides W1.4.

### R5 — 2026-08-31: VB-QA-05 idempotency closed (W1.4)

`clean("scratch that scratch that")` used to render the text "Scratch that";
cleaning that output fired SCRATCH_THAT, and on the VB-124 double-cleanup
fallback path that deletes an utterance the user already committed. The
utterance command is now re-detected after the repetition-collapse stage, gated
on `repetitionsCollapsed > 0` so that a stage-3 spoken-punctuation conversion
("scratch that period") can never turn dictated words into a command. The
`@Disabled` test in `CleanupPropertyTest` is enabled and `QaRegressionPinTest`'s
pin is inverted to assert the fixed behaviour. `:core` now has 795 tests, 0
failures, 0 ignored.

### R6 — 2026-08-31: `:voice` uses the namespace `com.vboard.app` (W2.1)

The ported sources reference `com.vboard.app.R` for their strings. Giving the
`:voice` library that namespace makes those references resolve with no edit, so
the diff against VBoard stays readable. `:llm` is `com.vboard.app.llm`.

### R7 — 2026-08-31: voice settings live in HeliBoard's preferences (W2.5)

VBoard kept settings in a DataStore of its own, and its snapshot carried the
keyboard's settings too — theme, haptics, key preview, autocorrect mode, number
row, clipboard history. All of those are HeliBoard's here, so the ported
`SettingsRepository` was cut down to what the voice layer alone decides and
re-pointed at HeliBoard's SharedPreferences. One store, one screen, and the
dictation path keeps a synchronous read.

### R8 — 2026-08-31: what "INTERNET only in `:ui`" can actually mean (W2.3)

Android grants permissions to an application, not a process: there is no way to
declare INTERNET for `:ui` alone, and any claim otherwise would be theatre. What
is enforceable is that every component that can reach the network runs in `:ui`
— the download service and WorkManager's foreground service, with WorkManager's
androidx.startup initializer removed so the keyboard process never hosts the
downloader — and that the IME declares no `android:process` at all.
`ManifestProcessSplitTest` asserts exactly that, and the manifest carries the
`supervoiceboard:internet-scoped` marker the CI audit greps for.

HeliBoard's own settings activity stays in the main process. Moving it to `:ui`
would put its SharedPreferences writes in a different process from the IME's
reads, which SharedPreferences does not support; it touches no network, so it
does not need to move.

### R9 — 2026-08-31: MediaPipe's minSdk is overridden, not adopted (W2.4)

`tasks-genai` declares minSdk 24 and HeliBoard supports 21. Raising the floor
would drop API 21-23 users for an optional feature, so `:llm` force-merges the
library and both `LlmRefinerService.engineOrNull` and `refinerClientOrNull`
return null below API 24. On those devices the refiner simply does not exist.

### R10 — 2026-08-31: `VoiceRuntime` replaces `VBoardApp` (W2.1)

VBoard handed its `Application` subclass to the session controller, the download
worker and the refiner client. The hosting Application here is HeliBoard's — an
upstream class this fork does not own — so those five references now go through
`VoiceRuntime` / `VoiceRuntimeHost` (and `RefinerModelHost` for the `:llm`
process, which must not see the keyboard's half at all). `App.kt` implements
them; that is the entire footprint of the voice layer in an upstream file.

`VoiceBarView` was not ported, per §2, so `ErrorActionKind` moved out of it and
into `VoiceErrorAction` in `:voice`: which error happened and what the recovery
is are session facts, not view state.

### R11 — 2026-08-31: HeliBoard's Robolectric tests need Java 21

`:app`'s inherited tests (InputTest, ParserTest, SpellCheckerTest, …) fail
before they run on Java 17: Robolectric refuses to sandbox an SDK 36 target
below Java 21. CI now uses Java 21 so they actually execute. This machine has
only JDK 17 (AGP 8.13 rejects the JDK 25 in Android Studio), so `:app`'s
Robolectric tests are unverified locally and are verified in CI instead;
`:core`'s 795 tests and the plain-JUnit manifest audit run fine on 17.
