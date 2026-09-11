#!/usr/bin/env python3
"""Score a prompt against a corpus of real prompts that have no ground truth.

The scored cases in cases.json ask "did it apply the rule". This asks the
other half: turned loose on a real sentence, does the refiner leave it alone,
and does it ever answer the sentence instead of typing it? For dictated
instructions - which is what this corpus is - answering is the failure that
matters, because the user is talking to their phone, not to the model.

The corpus itself is never committed; pass a path outside the repo.
"""
import argparse, json, re, sys, difflib
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from run import ask, load_prompt

ANSWER_TELLS = re.compile(
    r"^(sure|certainly|here('s| is)|i'?ll |i can |okay|ok,|let me |to \w+ this|"
    r"you can |the answer|yes,|no,)", re.I)
ENTITY = re.compile(r"https?://\S+|[\w.+-]+@[\w.-]+\.\w+|\b\d[\d.,:/-]*\b")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("prompt_file")
    p.add_argument("corpus")
    p.add_argument("--model", default="qwen/qwen3-0.6b")
    p.add_argument("--limit", type=int, default=120)
    p.add_argument("--stride", type=int, default=1, help="take every Nth prompt")
    p.add_argument("--show", type=int, default=12, help="worst N to print")
    args = p.parse_args()

    system, shots = load_prompt(args.prompt_file)
    corpus = json.load(open(args.corpus))[:: args.stride][: args.limit]

    answered, truncated, corrupted, changed, failed = [], [], [], 0, 0
    ratios = []
    for text in corpus:
        try:
            got = ask(args.model, system, text, shots)
        except Exception:
            failed += 1
            continue
        if not got:
            failed += 1
            continue
        ratio = difflib.SequenceMatcher(None, text.lower(), got.lower()).ratio()
        ratios.append(ratio)
        if ratio < 0.98:
            changed += 1
        # Two different failures that both look like "very different output".
        # Answering is the model talking back; truncation is it eating the
        # sentence. The first needs prompt work, the second is what the
        # validator's length band exists to catch.
        if ANSWER_TELLS.match(got) and len(got) > len(text) * 0.8:
            answered.append((ratio, text, got))
        elif len(got) < len(text) * 0.6:
            truncated.append((ratio, text, got))
        lost = [e for e in ENTITY.findall(text) if e not in got]
        if lost:
            corrupted.append((lost, text, got))

    n = len(ratios)
    print(f"corpus: {len(corpus)} prompts, {n} scored, {failed} empty/errored")
    if n:
        print(f"  answered instead of typed : {len(answered):>4}  ({len(answered)/n:.0%})")
        print(f"  sentence truncated        : {len(truncated):>4}  ({len(truncated)/n:.0%})")
        print(f"  entity lost or rewritten  : {len(corrupted):>4}  ({len(corrupted)/n:.0%})")
        print(f"  text changed at all       : {changed:>4}  ({changed/n:.0%})")
        print(f"  mean similarity to input  : {sum(ratios)/n:.2f}")

    for label, rows in (("ANSWERED", answered), ("TRUNCATED", truncated),
                        ("ENTITY LOST", corrupted)):
        for row in rows[: args.show]:
            said, got = row[1], row[2]
            print(f"\n{label}\n  in : {said[:140]}\n  out: {got[:140]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
