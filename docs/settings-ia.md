# Settings information architecture — 2026-09-09

Two passes: a full inventory (17 screens, 178 preference rows, 45 flagged) and an interaction
review of the three worst flows. Mocks of the three reorganisation directions:
https://claude.ai/code/artifact/e49f873d-b85b-40e8-abc3-f44488fc7b3e

## What the inventory found

- **11 rows at the root**, three of which are buckets with no meaning to a user: *Preferences*,
  *Advanced*, *Secondary layouts*. Everything that did not fit went into them.
- **Four rows labelled "Switch to main keyboard after…"** on one screen (Advanced), differing
  only in which mode they return from.
- **Five settings exist twice**: popup key order, hint source, diacritic popups, localized
  number row and secondary layouts are all both global and per-language, with no indication on
  either screen that the other exists.
- **Auto-capitalization is in two places** with different names: *Auto-capitalization* under
  Text correction, *Capitalize dictated text* under Voice.
- **Two dead settings**: *Dictate in place* (`voice_inline_dictation`) and *Google Password
  Manager fill* — both written by the UI and read by nothing.
- **Four screens sit three or more taps deep**: personal dictionary words (4), per-language
  settings (3), debug (3), colour editing (3).

## Proposed hierarchy

Six entries instead of eleven. Every row carries its current value as a summary; anything
conditional nests visually under the switch that gates it, rather than appearing and vanishing.

- [x] **Languages** — the enabled languages, and per language: layout, multilingual typing,
      number row. The four per-language overrides move under an *Overrides* group that stays
      collapsed until one differs from the global value.
- [x] **Typing** — merges *Preferences*, *Text correction* and *Gesture typing*:
      Keys & feedback · Corrections · Suggestions · Swipe. Autocorrect confidence, autospace and
      the suggestion switches stop being three separate screens' worth of scrolling.
- [x] **Voice** — dictation, with *Voice models* promoted from a row to a status card at the top
      (installed / needed / downloading) since it is the gate on the whole feature.
- [x] **Look & feel** — splits today's 20-row Appearance into **Style** (theme, key style, icons,
      borders, background) and **Size & spacing** (the six scales, split), each with a live
      keyboard docked at the top.
- [x] **Toolbar & keys** — the toolbar editor below, plus the key-behaviour settings that today
      sit under Advanced (long-press delay, spacebar swipes, delete swipe).
- [x] **Privacy & data** — incognito, personalized suggestions, backup and restore, and the
      *Privacy-breaking features* screen unchanged in content but reachable in one tap.

About stays, with the debug ladder behind it as today. *Advanced* disappears as a destination:
each section gets its own collapsed **Advanced** group, so a setting sits next to what it affects.

## Naming

- [x] "Preferences" → **Typing & feedback**; "Secondary layouts" → **Symbol & number layouts**
- [x] Four × "Switch to main keyboard after…" → "After symbols and a space", "After the numpad",
      "After an emoji", "After pasting"
- [x] "More auto-correction" → **Correct more words**; "Always use middle suggestion" → **Keep the
      best word in the middle**; "Next-word suggestions" → **Suggest the next word**
- [x] "Floating preview" / "Dynamic floating preview" → **Show the word while you swipe** /
      **Update it as you swipe**; "Phrase gesture" → **Swipe through the space bar for two words**
- [x] "Show TLD popup keys" → **Show .com on the period key**; "Select hint source" → **Which popup
      letter to show**; "Split distance" → **Gap between the halves**
- [x] "Remove discourse fillers" → **Remove "you know" and "I mean"**; "Save log" → **Save a
      diagnostic log**; "Override Emoji version" → **Emoji set version**
- [x] Delete *Dictate in place* and *Google Password Manager fill*, or wire them to something

## The three flows worth rebuilding

### Toolbar keys — today: 5 taps, a 36-row dialog and a long-press drag
- [x] Replace the three near-identical reorder dialogs with **one screen showing the real strip**,
      rendered at keyboard width in the live theme, and a drawer of the off keys under it.
- [x] Drag from drawer into strip to add, within the strip to reorder, out to remove.
- [ ] Toolbar mode becomes four tiles, each a mock of that mode's strip, not a list dialog.
- [ ] Long-press a key opens its own sheet — name, spoken description, tap and long-press codes —
      folding away the separate "Customize toolbar key codes" dialog.
- [ ] Group the fork's own keys first (AI fix, dictation engine, resize) with one line each on what
      they do; show the mic as a locked chip that says it always lives in the strip.

### Theme and colour — today: 7 taps across two screens and two dialogs, with no keyboard in sight
- [x] Dock a **live keyboard** at the top of the colour editor and let the user tap a part of it —
      a key, the space bar, the strip, the voice bar — to edit that colour directly.
- [ ] The colour picker becomes a half-height sheet so the keyboard stays visible while dragging,
      committing live with an Undo rather than an OK button.
- [ ] Replace the Main/More/All overflow menu with an inline "10 shown · 38 advanced" expander.
- [ ] "+" offers **Start from current**, **From an image**, **Blank**, and a WaveKey preset.
- [ ] One picker with a Day | Night toggle, instead of two rows that open the same screen.

### Size and spacing — today: 4 taps into a dialog of up to 8 unlabelled sliders
- [ ] One orientation chip row (Portrait · Landscape · Folded · Split) sets the context for every
      control, replacing six dialogs of per-orientation sliders.
- [ ] Direct manipulation: drag the keyboard's own edges for height and padding. The resize key
      already does this — settings should open the same tool rather than describe it in percentages.
- [x] Sliders stay, under Advanced, now labelled with their current value.

## Open questions for the PM

- Do pinned keys stay a separate list, or become a star on a key inside one editor?
- Do themes ship as light/dark pairs, or stay two independently chosen day and night themes?
- Is resize mode the primary geometry editor, and can it be launched from settings with no IME running?

## Status, 2026-09-10

Implemented and verified on an API 35 emulator:

- Six doors with live summaries, and the two hub screens behind them
- Fifteen renames; the dead *Dictate in place* setting removed
- Key behaviour moved to Toolbar & keys; incognito and backup to Privacy & advanced
- Look & feel's categories: Theme, Size & spacing, Text & fonts
- The per-language override note on the Subtype screen
- One toolbar key editor with the strip drawn, in place of three modal lists
- One theme picker with a Day | Night switch
- The colour editor with the keyboard pinned above it, tappable: tap the keys, the space bar,
  the action key, the strip or the board to open that colour
- The colour picker as a bottom sheet, so the keyboard stays visible while a hue is dragged
- Voice models as a status card that says whether dictation can run and what a download costs
- Every size row showing its value, and the size dialog marking the orientation you are in

Not done, with the reason:

- **Size as a drag canvas.** The keyboard already has this — the resize key drags its own edges —
  and settings cannot launch it without a running IME. Duplicating it in Compose would mean a
  second geometry editor to keep in step with the first. The open question for the PM stands:
  should resize mode become the primary editor, launchable from settings?
- **Drag-and-drop in the toolbar editor.** Tapping selects a key and moves it one place at a time,
  which covers reordering without a drag implementation inside a scrolling column.
- **Global settings saying they can be overridden per language.** The per-language screen now says
  it overrides; the global screens still do not mention it.
