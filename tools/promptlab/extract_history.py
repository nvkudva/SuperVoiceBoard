"""Pull genuine user-typed prompts out of Claude Code transcripts.

Stays in the scratchpad on purpose: this is personal history and the repo is
public. Nothing here is written under the project tree.
"""
import glob, json, os, re, sys

SKIP = ("<system-reminder", "<command-name", "<local-command", "Caveat:",
        "[Request interrupted", "<user-prompt-submit-hook", "<ci-monitor-event",
        "<task-notification", "[Image:", "<persisted-output", "[SYSTEM NOTIFICATION",
        "# Environment", "The user sent a new message")

def prompts():
    for path in glob.glob(os.path.expanduser("~/.claude/projects/*/*.jsonl")):
        with open(path, errors="replace") as fh:
            for line in fh:
                try:
                    row = json.loads(line)
                except Exception:
                    continue
                if row.get("type") != "user":
                    continue
                msg = row.get("message") or {}
                content = msg.get("content")
                if not isinstance(content, str):
                    continue
                text = content.strip()
                if not text or text.startswith(SKIP) or text.startswith("/"):
                    continue
                yield text

seen, kept = set(), []
for p in prompts():
    flat = " ".join(p.split())
    # One sentence-ish utterance is what gets dictated; skip pasted logs.
    if not (10 <= len(flat) <= 300):
        continue
    if flat.count("```") or flat.startswith("http"):
        continue
    # Anything still carrying harness markup is transcript, not something a
    # person would say into a phone.
    if "<" in flat and ">" in flat and re.search(r"</?[a-z-]+>", flat):
        continue
    key = flat.lower()
    if key in seen:
        continue
    seen.add(key)
    kept.append(flat)

print(f"{len(kept)} unique prompts", file=sys.stderr)
json.dump(kept, open(sys.argv[1], "w"), indent=1)
