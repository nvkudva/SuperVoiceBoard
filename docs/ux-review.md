# UX / UI review — 2026-09-09

One design pass over the toolbar, the keyboard surface, the settings screens and the setup
wizard, against the WaveKey palette (ground #0A0D22, spectrum coral #FB7185 → violet #A855F7
→ cyan #22D3EE, white for structure). Mocks for three keyboard directions:
https://claude.ai/code/artifact/8343f0a4-9b0a-4bd4-877c-124fc7686a54

Recommended direction: **Spectrum Rail** — it fixes touch targets, mic continuity and meter
legibility inside the existing single-row architecture, so it costs no vertical space and no
layout rework. Action Dock adds ~50 px of chrome; Edge-lit Focus makes dictation modal.

## Touch targets and layout

- [ ] `app/src/main/res/values/config.xml:54` — the strip is 40 dp tall with 36 dp edge keys, so every toolbar, mic and voice control is under the 48 dp minimum → 48 dp strip, 48 dp touch slop on edge keys (M)
- [ ] `app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt:152` — 11 default toolbar keys × 36 dp overflows a 390 dp phone inside a scroll view with no affordance → cut the defaults to ~7 and add an edge fade or an overflow chevron (S)
- [ ] `app/src/main/res/layout/keyboard_resize_overlay.xml:13` — 28 dp drag handles, and no readout of the size being dragged → 48 dp hit area plus a live percentage (M)

## State that is invisible

- [ ] `app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt:60` — `else -> true` leaves AI_FIX, RESIZE and ASR_ENGINE permanently drawn "activated", so no toggle has an off state → derive `isActivated` from each feature's real state (S)
- [ ] `app/src/main/java/helium314/keyboard/voice/AiFixKey.kt:81` — RUNNING and UNDO look identical to IDLE (only the content description changes), so a slow fix reads as a dead key and the undo window is invisible → distinct progress and undo affordances (M)
- [ ] `app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt:178` — the mic never changes appearance when armed and disappears when the row swaps to the voice strip, so the target the user pressed moves → keep the mic in place with an explicit listening state (M)
- [ ] `app/src/main/java/helium314/keyboard/voice/VoiceStripView.kt:149` — the level meter is one centre-growing bar in toolbar grey, indistinguishable from a progress bar → multi-bar trace in the spectrum accent (M)
- [ ] `app/src/main/java/helium314/keyboard/voice/VoiceStripView.kt:79` — on error the back arrow silently becomes "grant permission" or "open download" while keeping its icon and description → swap icon and label with the action (S)
- [ ] `app/src/main/java/helium314/keyboard/voice/VoiceStripView.kt:121` — Done and Minimize are hidden while preparing and finalizing, so during a slow model load the only way out is Back, which cancels → keep them mounted and disabled (S)
- [ ] `app/src/main/res/layout/keyboard_resize_overlay.xml:11` — a hardcoded `#40000000` scrim and `#FFFFFFFF` label ignore the theme, so resize mode is near-invisible on light themes → source both from Colors/ColorType (S)

## Settings and first run

- [ ] `app/src/main/java/helium314/keyboard/settings/screens/VoiceScreen.kt:48` — the dictation-engine choice has no settings entry at all; it exists only as a toolbar toggle whose sole feedback is a toast → add an engine preference showing the active engine and its fallback (M)
- [ ] `app/src/main/java/helium314/keyboard/settings/screens/VoiceScreen.kt:48` — 13 flat switches with no status header: nothing says whether models are installed or the mic is granted before you scroll → lead with a state card (models, engine, permission) (M)
- [ ] `app/src/main/java/helium314/keyboard/settings/WelcomeWizard.kt:87` — the wizard ends at the microphone step and never offers the model download, so the first dictation is an unannounced cold download → add a model-fetch step before finish (M)
