# promptlab

Scores a dictation-cleanup prompt against the model the keyboard ships
(Qwen3-0.6B), through LM Studio on a desktop, so a prompt change is a number
rather than an opinion.

    lms load qwen/qwen3-0.6b
    python3 tools/promptlab/run.py tools/promptlab/prompt_h.txt

`run.py` scores 14 hand-written cases, one per rule: did the rule fire, and
did anything leak. `corpus_run.py` asks the other half of the question against
real sentences with no ground truth — does the refiner leave them alone, does
it ever answer them, does it eat half the sentence.

A prompt file is the system text, optionally followed by `---` and alternating
`say:` / `type:` example turns. The turns are sent as real user/assistant
messages: pasted into the system text instead, a 0.6B starts answering with an
example verbatim.

## The corpus is not in this repo, and must not be

`extract_history.py` builds one from your own Claude Code transcripts:

    python3 tools/promptlab/extract_history.py /tmp/corpus.json
    python3 tools/promptlab/corpus_run.py tools/promptlab/prompt_h.txt /tmp/corpus.json

Write it outside the working tree. Prompt history is personal, and it contains
whatever you have ever pasted into a prompt — API keys included.

## What the numbers looked like

Rule cases: the rules alone answered 6 of 14; the rules plus example turns
answer 9. Adding more rules made every case worse, including unrelated ones.

Real sentences: answering back is rare (1-2%). Truncation is the failure that
matters — the refiner eating half a sentence — and example turns cut it from
18% to 10%.

## Where the rules go relative to the model

Measured on 75 utterances from real history with their addresses spoken aloud:

| pipeline | score |
|---|---|
| rules alone | 48/75 |
| rules, model, validator | 42/75 |
| rules, model, rules, validator | 42/75 |
| model, then rules | 20/75 |
| model alone | 17/75 |

Rules after the model instead of before is the worst arrangement that still
has rules in it. The rules match spoken forms - "dot", "slash", "at" - and a
model handed those destroys them before the rules ever see them, leaving
nothing to match. Rules after the model *as well* scores identically to rules
before it, because by then there is no spoken form left to convert.

So the rules run first, and `refine()` stands down on an utterance that
already carries a written address.
