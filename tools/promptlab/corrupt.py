#!/usr/bin/env python3
"""Turn clean sentences into what a bad mic and a small ASR model make of them.

Each corruption is recorded with the word it replaced, so the corrupted
sentence comes with its own ground truth: the refiner is judged on whether it
puts the original back. Written to the same case format run.py already reads.
"""
import argparse, json, random, re, sys

# Heard-alike pairs. An ASR model picks by sound, so these are the errors it
# actually makes - not the letter transpositions a typist makes.
HOMOPHONES = {
    "their": "there", "there": "their", "they're": "their",
    "to": "too", "too": "to", "two": "to",
    "your": "you're", "you're": "your",
    "its": "it's", "it's": "its",
    "know": "no", "no": "know", "now": "no",
    "right": "write", "write": "right",
    "then": "than", "than": "then",
    "here": "hear", "hear": "here",
    "one": "won", "for": "four", "four": "for",
    "new": "knew", "break": "brake", "week": "weak",
    "by": "buy", "buy": "by", "see": "sea",
    "our": "are", "are": "our", "of": "off", "off": "of",
}

# A small voice model hearing a technical word it was never trained on.
JARGON = {
    "kotlin": "cotlin", "gradle": "graddle", "json": "jason",
    "github": "git hub", "regex": "rejex", "async": "a sync",
    "api": "a p i", "ui": "you why", "npm": "n p m",
    "cache": "cash", "query": "queery", "schema": "skima",
    "commit": "comit", "repo": "ripo", "debug": "de bug",
    "python": "pythonn", "widget": "wijet", "layout": "lay out",
    "database": "data base", "runtime": "run time",
}

SPLIT = {"into": "in to", "website": "web site", "anymore": "any more",
         "something": "some thing", "everything": "every thing",
         "cannot": "can not", "setup": "set up", "login": "log in"}

FILLERS = ["um", "uh", "er", "like", "you know", "so", "i mean"]
SMALL_WORDS = {"the", "a", "an", "is", "to", "of", "and", "it"}

ONES = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
TEENS = ["ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
         "seventeen", "eighteen", "nineteen"]
TENS = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]


def spell_number(n):
    if n < 10:
        return ONES[n]
    if n < 20:
        return TEENS[n - 10]
    if n < 100:
        return TENS[n // 10] + ("" if n % 10 == 0 else " " + ONES[n % 10])
    return " ".join(ONES[int(d)] for d in str(n))


# A bare host needs a real-looking TLD and a letter start, or "gemini 2.5"
# and "v1.5" arrive as web addresses and the test measures the wrong thing.
TLDS = ("com|org|net|io|dev|ai|co|uk|in|app|sh|me|gg|xyz|edu|gov")
URL_RE = re.compile(
    r"https?://[^\s]+"
    r"|(?<![\w.])(?:www\.)?[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:" + TLDS + r")(?:/[^\s,]*)?\b",
    re.I)
EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
PATH_RE = re.compile(r"(?<![\w.])/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)+")
PHONE_RE = re.compile(r"(?<!\d)\d{4,10}(?!\d)")

DIGIT_WORD = ["zero", "one", "two", "three", "four", "five", "six", "seven",
              "eight", "nine"]


def say_entity(token):
    """What a recognizer hands over when someone dictates an address aloud."""
    said = token
    said = said.replace("https://", "https colon slash slash ")
    said = said.replace("http://", "http colon slash slash ")
    said = said.replace("@", " at ").replace(".", " dot ").replace("/", " slash ")
    said = said.replace("_", " underscore ").replace("-", " dash ")
    return " ".join(said.split())


def speak_entities(text):
    """Returns (spoken, [(kind, expected, wrong)]) for addresses and numbers."""
    marks = []

    def sub(pattern, kind):
        def repl(m):
            said = say_entity(m.group(0))
            if said == m.group(0):
                return m.group(0)
            marks.append((kind, m.group(0), said))
            return said
        return pattern.sub(repl, text)

    for pattern, kind in ((EMAIL_RE, "email"), (URL_RE, "url"), (PATH_RE, "path")):
        text = sub(pattern, kind)

    def digits(m):
        said = " ".join(DIGIT_WORD[int(d)] for d in m.group(0))
        marks.append(("digit-run", m.group(0), said))
        return said

    text = PHONE_RE.sub(digits, text)
    return text, marks


def words(text):
    return re.findall(r"\w+|\W+", text)


def corrupt(text, rng):
    """Returns (corrupted, [(kind, expected, wrong)])."""
    toks = words(text)
    marks = []

    def swap(table, kind, limit=2):
        hits = 0
        for i, tok in enumerate(toks):
            if hits >= limit:
                break
            low = tok.lower()
            if low in table and rng.random() < 0.7:
                toks[i] = table[low]
                marks.append((kind, tok, table[low]))
                hits += 1

    swap(JARGON, "jargon")
    swap(HOMOPHONES, "homophone")
    swap(SPLIT, "word-split", limit=1)

    # Digits become spoken words, which is what the recognizer hands over.
    for i, tok in enumerate(toks):
        if tok.isdigit() and len(tok) <= 3 and rng.random() < 0.9:
            spoken = spell_number(int(tok))
            toks[i] = spoken
            marks.append(("number", tok, spoken))

    out = "".join(toks)

    # A dropped small word: the mic clipped the start of a syllable.
    parts = out.split()
    if len(parts) > 6:
        drops = [i for i, w in enumerate(parts) if w.lower() in SMALL_WORDS]
        if drops and rng.random() < 0.5:
            i = rng.choice(drops)
            marks.append(("dropped-word", parts[i], ""))
            parts.pop(i)

    # A stutter: the recognizer emitted the same token twice.
    if len(parts) > 4 and rng.random() < 0.6:
        i = rng.randrange(len(parts) - 1)
        parts.insert(i, parts[i])
        marks.append(("stutter", parts[i], parts[i] + " " + parts[i]))

    # Fillers, which a recognizer transcribes faithfully because they are sound.
    if rng.random() < 0.7:
        f = rng.choice(FILLERS)
        parts.insert(rng.randrange(len(parts) + 1) if parts else 0, f)
        marks.append(("filler", "", f))

    # Spoken self-correction.
    if len(parts) > 5 and rng.random() < 0.25:
        i = rng.randrange(2, len(parts))
        parts.insert(i, "no wait")
        marks.append(("self-correction", "", "no wait"))

    # Recognizers do not capitalise or punctuate.
    flat = " ".join(parts).lower().rstrip(".!?")
    return flat, marks


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("corpus")
    ap.add_argument("out")
    ap.add_argument("--limit", type=int, default=120)
    ap.add_argument("--stride", type=int, default=7)
    ap.add_argument("--seed", type=int, default=11)
    ap.add_argument("--entities", action="store_true",
                    help="only speak addresses and numbers aloud; no mishearing")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    corpus = json.load(open(args.corpus))[:: args.stride]
    cases, kinds = [], {}
    for n, clean in enumerate(corpus):
        if len(cases) >= args.limit:
            break
        if args.entities:
            dirty, marks = speak_entities(clean)
            dirty = dirty.lower().rstrip(".!?")
        else:
            dirty, marks = corrupt(clean, rng)
        # Only keep sentences a corruption actually landed on, and only
        # corruptions with something to restore.
        checkable = [m for m in marks if m[1] and m[0] != "dropped-word"]
        if args.entities:
            checkable = [m for m in marks if m[0] in
                         ("email", "url", "path", "digit-run")]
        if not checkable:
            continue
        cases.append({
            "id": f"c{n:04d}",
            "rule": "+".join(sorted({m[0] for m in checkable})),
            "in": dirty,
            "clean": clean,
            # A stutter has nothing to restore - the word is already there.
            # What proves it was fixed is the doubled form being gone.
            "must": sorted({m[1] for m in checkable if m[0] != "stutter"}),
            "must_not": sorted({m[2] for m in checkable if m[2]}),
            "marks": [{"kind": k, "expected": e, "wrong": w} for k, e, w in checkable],
        })
        for k, _, _ in checkable:
            kinds[k] = kinds.get(k, 0) + 1

    json.dump(cases, open(args.out, "w"), indent=1)
    print(f"{len(cases)} corrupted cases", file=sys.stderr)
    for k, v in sorted(kinds.items(), key=lambda kv: -kv[1]):
        print(f"  {k:<16} {v}", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main())
