# Store listing copy

The fork's listing text lives here, not in `fastlane/`. Nothing in this file is
published anywhere yet — no store track has been started. It exists so the copy is
written down and reviewed before it is used, and so the warning below is impossible
to miss.

## Do not edit `fastlane/`

All 28 locale directories under `fastlane/metadata/android/` are upstream
HeliBoard's and stay untouched. Rewriting `en-US` alone creates a permanent rebase
conflict against upstream, for a store submission nobody has started.

**The upstream copy is false for this fork.** Line 2 of
`fastlane/metadata/android/en-US/full_description.txt` reads:

> Does not use internet permission, and thus is 100% offline.

SuperVoiceBoard holds `INTERNET`. It is scoped to the `:ui` process and used only to
download models, and the keyboard process itself has no network component — but the
permission is there, and the sentence as written is a false claim. It must never be
published as-is, in `en-US` or in any of the 27 translations that repeat it.

Anyone starting a store submission replaces that sentence first. The honest version
is in the draft below.

## Screenshots

The repo has two, both from the UI QA suite, and neither belongs in a store listing:

- `docs/screenshots/suggestion-row-with-mic.png` — the mic key on the suggestion strip.
- `docs/screenshots/voice-row-no-model.png` — the voice row's **error state**, because
  the CI emulator has no speech model installed.

A listing needs a screenshot of dictation actually running, which needs a model on
the emulator. See `docs/release-process.md`.

## Draft — title

    SuperVoiceBoard

## Draft — short description

    Keyboard with on-device dictation and AI text cleanup

## Draft — full description

    SuperVoiceBoard is an Android keyboard with English dictation that runs on
    your phone. Your speech is not uploaded, transcribed in a datacentre, or
    retained by anyone: the recogniser and the text cleanup both run locally.

    It is a fork of HeliBoard, itself based on AOSP / OpenBoard. Typing — layouts,
    glide typing, dictionaries, themes, clipboard — is HeliBoard's, unchanged.
    This fork adds the voice layer.

    Voice:
    • Dictation into any text field, from a mic key on the suggestion strip.
    • On-device transcript cleanup: punctuation, casing, filler words.
    • An "AI fix" key that rewrites the text you point it at, also on-device.
    • Falls back to your system speech recogniser while the model downloads.

    Network use: SuperVoiceBoard requests the INTERNET permission and uses it for
    one thing — downloading the speech and language models, once, when you ask it
    to from Settings. The keyboard process itself has no network component; that
    split is enforced at build time. Nothing you type or say is sent anywhere.
    There is no account, no API key, and no paid service at any point.

    Requirements: an arm64 device. Dictation needs a 482 MB model download, and
    roughly 1.0 GB of storage if you also install the optional text refiner. The
    refiner needs Android 7.0 or newer and is skipped below that; typing and
    dictation work from Android 5.0.

    Dictation is English only.

## Before any of this is submitted

Blocked on work that is not started, and listed here so a submission does not go out
ahead of it:

- The refiner model is fetched from a mutable Hugging Face `main` ref with
  `sha256 = ""`, which the installer treats as "skip verification". A store's data
  safety form cannot be answered honestly while that is true.
- No privacy policy and no data-safety declaration exist.
- Nothing about speed, accuracy or battery has been measured, so the draft above
  claims none of it. Keep it that way.
