# WaveKey — Black-box QA Flows

Scope: end-to-end user-facing flows for the fork (dictation, AI fix, model packs, wizard, voice settings) plus the HeliBoard base paths that the fork changes. Grounded in `app/src/main/java/helium314/keyboard/{voice,correct,settings}`, `core/src/main/kotlin/com/vboard/core/{correct,model,text}`, `voice/src/main/kotlin/com/vboard/app/models`.

Cases marked **(unverified)** describe behaviour that could not be confirmed from the code within the review budget — treat the expected result as the thing to establish, not as documented truth.

Priorities: **P0** blocker (data loss, unusable keyboard, privacy leak), **P1** major (feature broken/misleading), **P2** minor (polish, cosmetics).

---

## First run and permissions

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-FIRSTRUN-1 | P0 | App freshly installed, keyboard not enabled | Launch app | Welcome wizard opens at step 1 ("enable"), with the intro panel and a 4-bar step progress indicator |
| QA-FIRSTRUN-2 | P0 | At wizard step 1 | Tap the step-1 action | System "Manage on-screen keyboards" settings opens; enabling WaveKey and returning advances the wizard to step 2 |
| QA-FIRSTRUN-3 | P0 | At wizard step 2 (enabled, not selected) | Tap the step-2 action, pick WaveKey in the input-method picker | Wizard polls IME state and auto-advances to the mic step without needing a manual refresh |
| QA-FIRSTRUN-4 | P1 | At wizard step 2 | Tap the secondary "later/skip" action | Wizard closes without crashing; reopening the app resumes at the correct step for current state |
| QA-FIRSTRUN-5 | P0 | At mic step, RECORD_AUDIO not granted | Tap the mic action, grant in the system dialog | Wizard advances to the final step; mic permission shows as granted |
| QA-FIRSTRUN-6 | P1 | At mic step | Tap "skip" on the mic step | Wizard jumps to the last step; dictation remains usable later only after granting via the in-strip error action |
| QA-FIRSTRUN-7 | P1 | At mic step | Deny the permission dialog once | Wizard does not hard-block; user can retry or skip |
| QA-FIRSTRUN-8 | P1 | At mic step | Deny twice / select "Don't ask again", then press mic in keyboard | Voice strip shows a permission error whose action opens app settings (OPEN_PERMISSION) rather than re-requesting silently |
| QA-FIRSTRUN-9 | P1 | At last wizard step | Tap the optional voice-model card | Voice models screen opens; declining and tapping finish closes the wizard |
| QA-FIRSTRUN-10 | P2 | Wizard open | Rotate device / resize to a wide window | Wide layout shows intro + steps side by side (0.6 weight); step position is preserved (rememberSaveable) |
| QA-FIRSTRUN-11 | P1 | Keyboard already enabled + selected + mic granted before first launch | Launch app | Wizard opens directly at the final step, not step 1 |
| QA-FIRSTRUN-12 | P1 | Wizard finished | Reinstall-free relaunch of the app | Main settings screen opens; wizard does not reappear **(unverified)** |
| QA-FIRSTRUN-13 | P1 | Mic denied, dictation pressed in another app | Tap the in-strip error action | `MicPermissionActivity` / app settings opens over the host app and returns cleanly to the field |

## Keyboard enable, select and typing

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-KBD-1 | P0 | WaveKey selected | Open any text field | Keyboard shows with the suggestion strip; mic and AI-fix keys render as spectrum tiles |
| QA-KBD-2 | P0 | Keyboard shown | Type a sentence, use backspace, space, enter | Characters commit correctly; suggestions update; no duplicated or dropped characters |
| QA-KBD-3 | P1 | Keyboard shown | Long-press keys for accents, switch to symbols and back | Base HeliBoard behaviour unaffected by the fork's strip changes |
| QA-KBD-4 | P1 | Keyboard shown on a wide screen/tablet | Observe strip layout | A gap appears at each side once the screen is wide enough; keys stay tappable |
| QA-KBD-5 | P1 | Toolbar customisation open (Settings > Toolbar) | Move AI_FIX key between expanded row and pinned strip | Key appears in the chosen location(s); all instances stay in the same visual state |
| QA-KBD-6 | P1 | AI_FIX pinned *and* in the expanded row | Press one instance | Both instances update state (icon, alpha, content description) together |
| QA-KBD-7 | P1 | Keyboard shown | Switch to another IME and back mid-sentence | Composing state is not left dangling; text is intact |
| QA-KBD-8 | P2 | Keyboard shown | Tap outside the keyboard (host app content) | `onTouchOutsideKeyboard` path runs; any active dictation is ended/cancelled cleanly |

## Dictation: start, stop, pause, minimize

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-DICT-1 | P0 | Mic granted, ASR pack installed | Tap the mic key | Mic opens on the press (not after model load); strip shows "Preparing" then "Listening" |
| QA-DICT-2 | P0 | Listening | Speak a sentence, tap Done | Text commits into the field; strip announces session ended and restores the toolbar as it found it |
| QA-DICT-3 | P0 | Listening | Tap Cancel | Nothing is committed; toolbar and strip return to the pre-dictation state |
| QA-DICT-4 | P1 | Listening | Press and hold the mic key, speak, release | Hold-to-talk path (`startHold`/`endHold`) finalizes on release |
| QA-DICT-5 | P1 | Listening | Stop speaking and wait past the configured silence timeout | Session finalizes automatically at the configured timeout |
| QA-DICT-6 | P1 | Listening, minimize key enabled in settings | Tap the minimize key | Keyboard minimizes, dictation keeps running, strip stays visible |
| QA-DICT-7 | P1 | Minimize key disabled (default) | Start dictation | No minimize key is shown; row rebalances without an empty slot |
| QA-DICT-8 | P1 | Listening | Speak continuously for several minutes | Screen stays on (screen lock acquired) and is released when the session ends |
| QA-DICT-9 | P1 | Google engine selected | Tap mic | Google speech path drives the same strip states (preparing/listening/finalizing) and commits one utterance |
| QA-DICT-10 | P1 | No ASR pack installed, on-device engine selected | Tap mic | Error with an OPEN_DOWNLOAD action that opens voice settings |
| QA-DICT-11 | P1 | Listening | Tap mic key again (toggle) | Session stops and finalizes rather than starting a second session |
| QA-DICT-12 | P2 | Listening | Speak at varying volume | Amplitude visualization on the strip tracks the voice; rail width responds |
| QA-DICT-13 | P1 | Dictation running | Rapidly Done → mic → Done | No crash, no doubled commits, commit index map stays consistent |

## Partials, commits and post-commit edits

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-PART-1 | P1 | Provisional commit ON (default) | Speak a long sentence | Partial text appears on the strip and provisional text lands in the field before the final pass |
| QA-PART-2 | P1 | Provisional commit OFF | Speak | Nothing enters the field until the utterance is final; partials show on the strip only |
| QA-PART-3 | P0 | Provisional text committed | Let the final pass replace it | `replaceUtterance` swaps the provisional text for the final without duplicating or eating adjacent typed text |
| QA-PART-4 | P0 | Text already in field before dictation | Dictate | `CommitPlanner` inserts with correct spacing/capitalisation relative to preceding text |
| QA-PART-5 | P1 | Utterance committed | Say the delete/undo spoken command (spoken commands ON) | Last utterance is removed from the field |
| QA-PART-6 | P2 | Spoken commands OFF | Speak a command phrase | Phrase is transcribed literally |
| QA-PART-7 | P1 | Dictated text committed | Type or delete manually | That utterance is no longer counted as model output for telemetry (`onUserEditedDictation`) |
| QA-PART-8 | P0 | Dictating, host app closes the field mid-utterance | Let the final pass return | Utterance is held (TTL 30 s) and replayed only into the *same* app's field; otherwise dropped |
| QA-PART-9 | P1 | Held utterance, switch to a different app within 30 s | Focus a field there | Nothing is replayed (OTHER_APP verdict) |
| QA-PART-10 | P1 | Held utterance | Return to the same app after >30 s | Nothing is replayed (EXPIRED) |
| QA-PART-11 | P0 | Held utterance | Return to the same app but into a password field | Nothing is replayed (FIELD_REFUSES) |
| QA-PART-12 | P1 | Refiner ON | Dictate | Strip shows "Cleaning"/"Translating" while the utterance is refined, then the refined text commits |

## AI fix key (toggle: on → off+undo offer → undo)

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-FIX-1 | P0 | Text field with a messy sentence, AI_FIX visible | Press the key once | Continuous fix turns ON, a fix runs immediately; key shows the RUNNING border animation |
| QA-FIX-2 | P0 | Continuous fix ON | Keep typing words | A new pass fires only at word boundaries, coalesced by ~200 ms; cursor is never moved mid-word |
| QA-FIX-3 | P1 | Continuous fix ON, field emptied | Delete all text | Run stays on and waits; no pass on empty text |
| QA-FIX-4 | P0 | Continuous fix ON, at least one fix landed | Press the key a second time | Run stops; key switches to the undo glyph and an undo is offered for the last fix (even if the original window had lapsed) |
| QA-FIX-5 | P0 | Undo offered | Press the key a third time | The last fix is reverted to the original text and selection |
| QA-FIX-6 | P1 | Undo offered | Wait past the undo window (+slack) | Key returns to the idle fix glyph; pressing it starts a new run instead of undoing |
| QA-FIX-7 | P0 | Undo offered | Type or delete a character | Undo is dropped immediately (armed undo cleared on user edit); key reverts to idle |
| QA-FIX-8 | P1 | Fix in flight (RUNNING) | Press the key again | Second press is a no-op, not a second run |
| QA-FIX-9 | P1 | Fix in flight | Move focus to another field before it returns | Result is discarded; the new field is not rewritten |
| QA-FIX-10 | P1 | Single-word substitution fix lands | Observe | A ghost swap animation shows before→after for that one word; wholesale rewordings show no ghost |
| QA-FIX-11 | P1 | Fix landed | Long-press the AI_FIX key | Attribution lists the editorial edits (substitutions/rewordings), not mechanical casing/spacing fixes |
| QA-FIX-12 | P0 | Password field focused | Press AI_FIX | Refusal message (password field); no text read, no rewrite; key disabled/greyed at 0.4 alpha |
| QA-FIX-13 | P0 | Email or URL field | Press AI_FIX | Address-field refusal; field untouched |
| QA-FIX-14 | P0 | Numeric field | Press AI_FIX | Numeric-field refusal; field untouched |
| QA-FIX-15 | P1 | Empty field | Press AI_FIX | Empty-field refusal |
| QA-FIX-16 | P1 | Field with text longer than the chunker's max field chars | Press AI_FIX | "Too long" refusal rather than a truncated rewrite |
| QA-FIX-17 | P1 | Refiner model not installed | Press AI_FIX | Deterministic (no-model) fix still runs; no crash, no silent no-op |
| QA-FIX-18 | P1 | 32-bit ARM device | Open voice settings and press AI_FIX | LLM refine switches are hidden (`refinerAbiSupported` false); fix runs deterministically |
| QA-FIX-19 | P1 | Continuous fix ON | Leave the field / close the keyboard | Run is abandoned, undo dropped, key returns to idle on next focus |
| QA-FIX-20 | P1 | Fix applied while IME was composing a word | Type immediately after | Composing state was finished before the rewrite; the next keystroke does not resurrect the old word |

## Model packs: download, import, delete, switch

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-PACK-1 | P0 | On Wi-Fi, no ASR pack | Voice models → Download | Download enqueues, row shows progress %, then Verifying, then installed |
| QA-PACK-2 | P0 | On cellular/metered | Tap Download | A confirmation dialog naming the real byte size appears first; nothing is enqueued until confirmed |
| QA-PACK-3 | P1 | Metered dialog shown | Confirm | Download starts with metered explicitly allowed |
| QA-PACK-4 | P1 | Metered dialog shown | Dismiss | No download is enqueued or queued for later |
| QA-PACK-5 | P1 | Download running | Tap Cancel | Download stops, row returns to not-installed, partial files do not count as installed |
| QA-PACK-6 | P1 | Download queued but not started | Tap Cancel | Row shows queued then clears |
| QA-PACK-7 | P0 | Pack installed | Tap Remove/Delete | Confirmation; after deletion dictation using that pack reports the model missing rather than crashing |
| QA-PACK-8 | P1 | Downloaded pack file on disk | Import via the file picker with the correct file | "Import ok"; the install/verify stage runs to completion and the pack becomes usable |
| QA-PACK-9 | P1 | Import with a file whose digest does not match | Import | "Wrong file" error; nothing installed |
| QA-PACK-10 | P1 | Import an unrelated file | Import | "Unknown file" error |
| QA-PACK-11 | P1 | Pack already installed | Import the same pack | "Already installed"; no duplicate |
| QA-PACK-12 | P1 | Import interrupted / unreadable URI | Import | IO error message, no partial install |
| QA-PACK-13 | P0 | Device nearly full | Start a download larger than free space | INSUFFICIENT_STORAGE failure message; no corrupt half-pack left behind |
| QA-PACK-14 | P0 | Airplane mode / no network | Tap Download | Download does not silently hang; policy reports no usable network **(unverified — exact copy)** |
| QA-PACK-15 | P1 | Download running | Leave the settings screen and return | Progress is restored from the live state flow / scheduled work, not restarted |
| QA-PACK-16 | P1 | Download running | Kill and reopen the app | Work continues or resumes via WorkManager; row reflects reality |
| QA-PACK-17 | P1 | Two packs (ASR + refiner) | Start both | Both rows track independently; neither cancels the other |
| QA-PACK-18 | P1 | Multiple ASR packs installed | Switch the selected ASR pack | Next dictation uses the newly selected pack |
| QA-PACK-19 | P1 | Pack currently in use by a live dictation | Attempt delete | Deletion is blocked or deferred with an "in use" line; dictation is not killed mid-utterance **(unverified)** |
| QA-PACK-20 | P2 | Models screen | Read each row | Size, state and "in use" line are accurate and localized |

## Engine choice and voice settings permutations

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-SET-1 | P1 | Voice settings open | Select "Google" engine | On-device engine is deselected (mutually exclusive radio); next dictation uses Google |
| QA-SET-2 | P1 | Google engine, device has no offline speech pack | Dictate | Row indicates the recognizer goes to Google (network) rather than staying local |
| QA-SET-3 | P0 | Voice settings | Turn Raw transcript ON | All cleanup switches below dim as unavailable and have no effect on transcripts |
| QA-SET-4 | P1 | Raw OFF | Toggle Auto-capitalize / Remove fillers / Aggressive fillers / Self-corrections / Auto-punctuate / Spoken commands one at a time | Each change is observable in the next dictated utterance |
| QA-SET-5 | P1 | Voice settings | Change Silence timeout | Next dictation finalizes after the new timeout |
| QA-SET-6 | P1 | Voice settings | Toggle Provisional commit | Matches QA-PART-1/2 behaviour |
| QA-SET-7 | P1 | Voice settings | Toggle "Show minimize key" | Minimize key appears/disappears on the next dictation strip |
| QA-SET-8 | P1 | refinerAbiSupported true | Toggle LLM refine ON | Refiner runs on dictated text; "Cleaning" state appears |
| QA-SET-9 | P1 | LLM refine ON | Toggle Refinement journal ON, dictate, use journal copy | Journal records before/after; copy puts it on the clipboard with a toast |
| QA-SET-10 | P1 | Voice settings | Toggle Telemetry | Metrics collection follows the switch; default is OFF |
| QA-SET-11 | P1 | Settings search | Search for a voice setting key | The setting resolves and navigates to the voice screen even though it draws a custom layout |
| QA-SET-12 | P2 | Settings | Change theme / dark mode | Voice screen, models screen and wizard render correctly in both themes |
| QA-SET-13 | P1 | Any voice setting changed while the keyboard is open in another app | Return to the field | Keyboard picks up the new value without needing a restart **(unverified)** |

## Field kinds that disable features

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-FIELD-1 | P0 | Password field | Observe strip; attempt dictation and AI fix | Voice-hostile field: fix refuses; dictation into a password field is not offered/replayed **(unverified for live dictation — only replay refusal is confirmed in code)** |
| QA-FIELD-2 | P0 | Numeric field | Dictate / press AI fix | Fix refuses (numeric); dictated digits, if allowed, are not rewritten |
| QA-FIELD-3 | P1 | Email field | Press AI fix | Address-field refusal |
| QA-FIELD-4 | P1 | URI field | Press AI fix | Address-field refusal |
| QA-FIELD-5 | P1 | Search field / IME action fields | Dictate then press the action key | Text commits before the action fires; no lost utterance |
| QA-FIELD-6 | P1 | Multi-line text area | Dictate several utterances | Joining respects existing newlines and spacing |
| QA-FIELD-7 | P1 | Field with `noPersonalizedLearning` / incognito | Dictate | No learning/telemetry recorded for that field **(unverified)** |

## Interruptions and lifecycle

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-INT-1 | P0 | Dictating | Receive an incoming call | Recording stops; no partial garbage committed; after the call the keyboard recovers and can start a new session |
| QA-INT-2 | P0 | Dictating | Switch to another app (recents/home) | `onFinishInputView` ends the session; screen lock released; any finished-but-unwritten utterance is held for replay in the same app only |
| QA-INT-3 | P0 | Dictating | Rotate the device | Strip is rebuilt in the new orientation, dictation either survives or ends cleanly without committing duplicates |
| QA-INT-4 | P1 | Dictating | Toggle system dark mode | Keyboard re-themes without crashing; session state is preserved or ended cleanly |
| QA-INT-5 | P0 | Refiner running in `:llm` | Force-stop the `:llm` process (adb kill) mid-refine | Keyboard does not crash; the utterance falls back to unrefined text or reports an error |
| QA-INT-6 | P1 | Refiner idle | Leave the keyboard closed for the idle-release period | Engines are released (`scheduleIdleRelease`); reopening warms up again without a visible stall |
| QA-INT-7 | P1 | Keyboard open | Another app steals audio focus (music/voice assistant) | Dictation handles the conflict without hanging in "Listening" **(unverified)** |
| QA-INT-8 | P1 | Dictating | Lock the screen | Session ends, wake lock released, no recording continues in the background |
| QA-INT-9 | P1 | Low memory | Let the IME process be killed and re-created | Next field focus rebuilds the strip and keys correctly; no stale undo or stale autofix run |
| QA-INT-10 | P1 | Continuous fix ON | Receive a call / switch apps | Run is abandoned on `onFinishInputView`; returning does not silently keep rewriting |
| QA-INT-11 | P1 | Download running | Reboot the device | Work resumes or the row shows a resumable/failed state, not a permanent "downloading" |

## Accessibility (TalkBack)

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-A11Y-1 | P0 | TalkBack on | Focus the mic and AI_FIX keys | Both announce a meaningful content description; AI_FIX description changes with state (idle/running/undo/disabled) |
| QA-A11Y-2 | P1 | TalkBack on | Start dictation | "Voice started" is announced; ending announces "Voice stopped" |
| QA-A11Y-3 | P1 | TalkBack on | Let a fix land | The fix message reaches the screen reader via `announceForAccessibility` (no visual toast) |
| QA-A11Y-4 | P1 | TalkBack on | Focus the Done / Cancel / Minimize keys | Each has a distinct label and is activatable by double-tap |
| QA-A11Y-5 | P1 | TalkBack on | Navigate the wizard | Step cards, primary and secondary actions are reachable in order and labelled |
| QA-A11Y-6 | P1 | TalkBack on | Navigate the voice models screen | Pack name, state and Download/Cancel/Remove buttons are distinguishable per row |
| QA-A11Y-7 | P2 | Large font / display size max | Open wizard, voice settings, models screen | No clipped text or unreachable buttons |
| QA-A11Y-8 | P2 | TalkBack on, AI_FIX disabled in a password field | Focus the key | It reports as disabled rather than silently doing nothing |

## Offline, storage and degraded states

| ID | Pri | Preconditions | Steps | Expected |
|---|---|---|---|---|
| QA-DEG-1 | P0 | Airplane mode, on-device pack installed | Dictate | Full dictation works with no network |
| QA-DEG-2 | P1 | Airplane mode, Google engine selected, no offline platform pack | Dictate | A clear network error with a dismiss action, not a silent stall |
| QA-DEG-3 | P1 | Storage nearly full | Dictate with refiner ON | Refiner failure degrades to unrefined text rather than losing the utterance |
| QA-DEG-4 | P1 | Pack files deleted out from under the app | Dictate | Readiness check fails cleanly and points to the download screen |
| QA-DEG-5 | P2 | Corrupted pack (truncated file) | Dictate | Verification/load failure is reported; the pack is not left marked installed **(unverified)** |
