# SuperVoiceBoard

An Android keyboard with on-device English dictation, transcript cleanup and text
rewriting, for people who do not want their speech leaving the phone.

It is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard) 4.1 (base commit
`9f5bb63`). Typing — layouts, glide typing, dictionaries, themes, clipboard — is
HeliBoard's, unchanged. This repo adds the voice layer.

## Requirements

- Android 5.0 (API 21) or newer to type and dictate. The LLM refiner needs Android 7.0
  (API 24) or newer and is skipped at runtime below that.
- A device microphone. Dictation adds `RECORD_AUDIO` to the permissions HeliBoard
  already requests.
- Internet on first run, for model download only. `INTERNET` is held by the `:ui`
  process; the keyboard process has no network component.
- Roughly 2 GB of free storage. The two required ASR packs download as 128 MB and
  482 MB archives and expand on install; the optional refiner is about 550 MB.
- To build: JDK 17 or newer (CI builds on 17 and 21), Android SDK 36. The Gradle 8.14
  wrapper and a 4 GB build heap are configured in the repo.
- No account, API key or paid service at any point.

## Run it

```bash
git clone https://github.com/nvkudva/SuperVoiceBoard.git
cd SuperVoiceBoard
./gradlew :app:assembleDebug
# ABI splits produce one APK per architecture; install the one matching your device
adb install app/build/outputs/apk/debug/<arm64-v8a or x86_64 APK>
```

"SuperVoiceBoard" then appears in Android's keyboard list. Enable it, switch to it, and
open its settings to download the voice models — the microphone key does nothing until
the two required packs are installed.

## Configuration

There is no runtime configuration. Two environment variables affect release builds only,
and only when a keystore exists at `~/.supervoiceboard/release.jks`. Without that file
the release build still succeeds and comes out unsigned.

| Variable | Required | What it is |
|---|---|---|
| `SVB_STORE_PASSWORD` | No | Keystore password for release signing |
| `SVB_KEY_PASSWORD` | No | Key password for the `supervoiceboard` alias |

## How it works

- `core/` is pure Kotlin JVM with no Android dependency. It holds the decisions:
  `DictationStateMachine`, `TranscriptCleaner`, `TextFixer`, `PackInstaller`. Roughly two
  thirds of the module by line count is tests.
- `voice/` is the Android half. `VoiceSessionController` executes the state machine's
  effects against `AudioCapture` and the sherpa-onnx recognizers — a streaming Zipformer
  for live text, a Parakeet TDT pass for the final transcript. It references no HeliBoard
  class, so it could be mounted in a different IME.
- `llm/` runs a Qwen2.5-0.5B model under MediaPipe in a separate `:llm` process behind
  `ILlmRefiner.aidl`, so a native OOM kills that process and not the keyboard.
- `app/` binds it to HeliBoard: `VoiceController` is the only class that writes to the
  `InputConnection`, and `AiFixKey` mounts the "AI fix" toolbar key.
- The three-process split (IME / `:ui` for anything networked / `:llm`) is asserted at
  build time by `app/src/test/java/com/supervoiceboard/ManifestProcessSplitTest.kt`,
  which fails if a network component ever leaves `:ui`.

Issues about the voice layer belong on this repo's tracker; issues about typing belong
upstream.

## Status

Working today: dictation into the suggestion strip, on-device cleanup, the AI fix key,
and model download and install from settings. CI runs `core` and app unit tests, a debug
assemble, Android lint, and an emulator UI QA suite.

Known gaps, from a code review of `voice/`, `llm/` and the voice code in `app/` dated
2026-09-07 (`REVIEW.md`):

- The refiner model is fetched from a mutable Hugging Face `main` ref with `sha256 = ""`,
  which the installer treats as "skip verification". TLS is the only integrity control on
  a file that MediaPipe then executes.
- Late refinement can delete characters the user typed after a commit —
  `VoiceController.replaceUtterance` does not check what it is about to remove.
- `voice/` and `llm/` have no unit tests; neither module declares a test dependency. They
  are 3.8k lines and hold all of the fork's concurrency.
- A timed-out `bindService` in `LlmRefinerClient` leaves the binding in place, pinning the
  `:llm` process and its model.
- No coroutine exception handler on the IME-process scopes: an uncaught throw in a
  finalize, refine or fix coroutine reaches the default handler and kills the keyboard.

Nothing about speed, accuracy or battery has been measured. Dictation is English only.
There is no screenshot of the voice strip in the repo and no published build — debug
APKs come from CI artifacts.

## License

GPL-3.0-only — see [LICENSE](LICENSE). The AOSP Keyboard base is Apache-2.0
([LICENSE-Apache-2.0](LICENSE-Apache-2.0)), the launcher icon is CC-BY-SA-4.0
([LICENSE-CC-BY-SA-4.0](LICENSE-CC-BY-SA-4.0)), and the default icon set is MIT
([LICENSE-MIT-fluent-icons](LICENSE-MIT-fluent-icons)).
