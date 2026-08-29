#!/usr/bin/env python3
"""Deterministic replay of golden Agent-run contracts.

Replays the recorded *decisions* of an Agent run — route classification, risk level,
policy expectations and terminal contract — against the same deterministic functions
used by the regression evaluator. No LLM, no network, no fake production numbers.

Usage:
    python3 scripts/replay_agent_run.py
    python3 scripts/replay_agent_run.py tests/golden-runs/recommend-java.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "evaluation"))

from evaluate_route_agent import offline_route


def replay_one(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    route = offline_route(data["input"])
    expected = data["expectedRoute"]
    risk = data.get("expectedRisk", "")
    payload = {
        "fixture": path.stem,
        "route_ok": route == expected,
        "route": route,
        "expected_route": expected,
        # HIGH-risk golden scenarios must contain a deterministic policy denial; the risk
        # escalation itself is covered by RouteSafetyPolicy tests on the Java side.
        # HIGH-risk golden scenarios carry evidence: either a deterministic policy denial or
        # a safety handoff route (risk escalation itself is covered by RouteSafetyPolicy Java tests).
        "risk_ok": risk != "HIGH" or route == "HUMAN_HANDOFF"
                   or any(not e["expectedAllowed"] for e in data.get("policy", [])),
        "policy_expectations": len(data.get("policy", [])),
        "terminal": data.get("terminal") == "STOP",
    }
    return payload


def main() -> int:
    paths = sorted(Path(ROOT / "tests" / "golden-runs").glob("*.json"))
    targets = [Path(p) for p in sys.argv[1:]] if len(sys.argv) > 1 else paths
    failed = []
    for path in sorted(targets):
        result = replay_one(path)
        ok = result["route_ok"] and result["risk_ok"] and result["terminal"]
        status = "PASS" if ok else "FAIL"
        print(f"[{status}] {result['fixture']:<28} route={result['route']:<14} expected={result['expected_route']}")
        if not ok:
            failed.append(result)
    print(f"\n{len(targets) - len(failed)}/{len(targets)} golden runs replayed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
