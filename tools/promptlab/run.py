#!/usr/bin/env python3
"""Score a dictation-cleanup prompt against LM Studio, one row per case.

The model is the same Qwen3-0.6B the keyboard ships, so a rule that a prompt
cannot get right here will not start working on the phone. What this cannot
tell you is latency or quantization behaviour: LM Studio runs GGUF through
llama.cpp, the phone runs mixed-int4 through LiteRT-LM.
"""
import argparse, json, re, sys, urllib.request

ENDPOINT = "http://127.0.0.1:1234/v1/chat/completions"


def load_prompt(path):
    """A prompt file is the system text, optionally followed by "---" and
    example turns as alternating `say:` / `type:` lines. Turns are sent as real
    user/assistant messages rather than pasted into the system text, which is
    what stops a 0.6B from answering with an example verbatim."""
    raw = open(path).read()
    head, _, tail = raw.partition("\n---\n")
    shots = []
    for line in tail.splitlines():
        if line.startswith("say:"):
            shots.append({"role": "user", "content": line[4:].strip() + " /no_think"})
        elif line.startswith("type:"):
            shots.append({"role": "assistant", "content": line[5:].strip()})
    return head.strip(), shots


def ask(model, system, text, shots=(), temperature=0.0):
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            *shots,
            # LM Studio ignores chat_template_kwargs for this model and routes
            # reasoning into `reasoning_content`, leaving `content` empty. The
            # /no_think soft switch is what actually silences it, and it matches
            # what ThinkingConfig(enableThinking = false) does on the phone.
            {"role": "user", "content": text + " /no_think"},
        ],
        "temperature": temperature,
        "max_tokens": 256,
        # Qwen3 thinks by default; the phone turns it off, so this must too.
    }
    req = urllib.request.Request(
        ENDPOINT, json.dumps(body).encode(), {"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=180) as r:
        out = json.load(r)["choices"][0]["message"]["content"]
    return re.sub(r"<think>.*?</think>", "", out, flags=re.S).strip()


def score(case, got):
    low = got.lower()
    missing = [m for m in case["must"] if m.lower() not in low]
    leaked = [m for m in case["must_not"] if m.lower() in low]
    return missing, leaked


def main():
    p = argparse.ArgumentParser()
    p.add_argument("prompt_file")
    p.add_argument("--model", default="qwen/qwen3-0.6b")
    p.add_argument("--cases", default="tools/promptlab/cases.json")
    p.add_argument("--only", help="run one case id")
    args = p.parse_args()

    system, shots = load_prompt(args.prompt_file)
    cases = json.load(open(args.cases))
    if args.only:
        cases = [c for c in cases if c["id"] == args.only]

    passed = 0
    by_rule = {}
    for c in cases:
        try:
            got = ask(args.model, system, c["in"], shots)
        except Exception as e:
            print(f"FAIL {c['id']:<14} {type(e).__name__}: {e}")
            continue
        missing, leaked = score(c, got)
        ok = not missing and not leaked
        passed += ok
        for kind in c.get("rule", "?").split("+"):
            hit, tot = by_rule.get(kind, (0, 0))
            by_rule[kind] = (hit + ok, tot + 1)
        flat = got.replace("\n", " ⏎ ")
        print(f"{'PASS' if ok else 'FAIL'} {c['id']:<14} {flat[:88]}")
        if not ok:
            why = []
            if missing:
                why.append("missing " + ", ".join(repr(m) for m in missing))
            if leaked:
                why.append("leaked " + ", ".join(repr(m) for m in leaked))
            print(f"     {c['rule']:<14} {'; '.join(why)}")
    if len(by_rule) > 1:
        print("\nby corruption:")
        for kind, (hit, tot) in sorted(by_rule.items(), key=lambda kv: -kv[1][1]):
            print(f"  {kind:<26} {hit:>3}/{tot:<3} {hit / tot:.0%}")
    shot_note = f", {len(shots) // 2} example turns" if shots else ""
    print(f"\n{passed}/{len(cases)} passed — {args.prompt_file}{shot_note}")
    return 0 if passed == len(cases) else 1


if __name__ == "__main__":
    sys.exit(main())
