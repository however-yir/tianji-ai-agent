#!/usr/bin/env python3
"""Single developer entry point wrapping the AgentOps toolchain.

    python3 scripts/agentops.py evaluate
    python3 scripts/agentops.py replay
    python3 scripts/agentops.py manifest [--verify]
    python3 scripts/agentops.py compare baseline.json candidate.json
    python3 scripts/agentops.py candidates            # offline feedback candidate export (formatter)
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


def run(*args: str) -> int:
    return subprocess.call(args, cwd=ROOT)


def export_candidates(path: Path) -> int:
    """Formats a feedback export (JSON lines: messageId/rating/reasonCode/comment) into
    human-review candidate cases. Feedback NEVER enters the dataset automatically."""
    if not path.exists():
        print(f"feedback export not found: {path}")
        print("export path is produced by the deployment's feedback store; local demo keeps")
        print("feedback in memory (FeedbackService) - this command formats an exported dump.")
        return 1
    count = 0
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        entry = json.loads(line)
        if entry.get("rating") != "DOWN":
            continue
        out.append({
            "candidateId": f"cand-{count + 1}",
            "messageId": entry.get("messageId"),
            "runId": entry.get("runId"),
            "reasonCode": entry.get("reasonCode", "OTHER"),
            "comment": entry.get("comment", ""),
            "status": "REVIEW_PENDING",
            "note": "negative feedback is a candidate for human review, NOT an automatic dataset entry",
        })
        count += 1
    target = ROOT / "artifacts/feedback-candidates.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{count} candidate cases written: {target}")
    return 0


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 0
    cmd = sys.argv[1]
    if cmd == "evaluate":
        return run(sys.executable, "scripts/evaluation/evaluate_route_agent.py")
    if cmd == "replay":
        return run(sys.executable, "scripts/replay_agent_run.py")
    if cmd == "manifest":
        extra = ["--verify"] if "--verify" in sys.argv[2:] else []
        return run(sys.executable, "scripts/generate_agent_manifest.py", *extra)
    if cmd == "compare":
        if len(sys.argv) < 4:
            print("usage: agentops.py compare baseline.json candidate.json")
            return 1
        return run(sys.executable, "scripts/evaluation/compare_agent_release.py", sys.argv[2], sys.argv[3])
    if cmd == "candidates":
        path = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "artifacts/feedback-export.jsonl"
        return export_candidates(path)
    print(f"unknown command: {cmd}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
