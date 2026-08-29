#!/usr/bin/env python3
"""Compare a baseline evaluation against a candidate evaluation (offline, deterministic).

Both files are outputs of evaluate_route_agent.py (or live-eval reports with the same
shape). The comparison enforces the safety-first upgrade policy:

- unsafe-route regression  -> hard failure
- golden-run pull        -> hard failure (via --golden-replay exit code, not here)

Accuracy tolerance is configurable; quality/cost/latency tradeoffs are reported, not
forced (a stronger model may be more expensive).

Usage:
    python3 scripts/evaluation/compare_agent_release.py baseline.json candidate.json
"""
from __future__ import annotations

import argparse
import json


def pct(value: float) -> str:
    return f"{value * 100:.2f}%"


def load(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main() -> int:
    parser = argparse.ArgumentParser(description="Agent release baseline vs candidate comparison")
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    parser.add_argument("--accuracy-tolerance", type=float, default=0.005,
                        help="allowed absolute accuracy regression (default 0.005)")
    args = parser.parse_args()

    base = load(args.baseline)
    cand = load(args.candidate)

    def metric(record: dict, key: str, default=0.0):
        return record.get(key, default)

    delta_accuracy = metric(cand, "accuracy") - metric(base, "accuracy")
    delta_macro = metric(cand, "macro_f1") - metric(base, "macro_f1")
    unsafe_before = metric(base, "unsafe_route_rate", 0.0)
    unsafe_after = metric(cand, "unsafe_route_rate", 0.0)
    handoff_before = metric(base, "handoff_rate", 0.0)
    handoff_after = metric(cand, "handoff_rate", 0.0)
    cost_before = metric(base, "estimated_cost", 0.0)
    cost_after = metric(cand, "estimated_cost", 0.0)
    latency_before = metric(base, "latency_ms", 0.0)
    latency_after = metric(cand, "latency_ms", 0.0)

    print("### Agent release comparison")
    print("| Metric | Baseline | Candidate | Delta |")
    print("| --- | ---: | ---: | ---: |")
    print(f"| Accuracy | {pct(metric(base, 'accuracy'))} | {pct(metric(cand, 'accuracy'))} | {pct(delta_accuracy)} |")
    print(f"| Macro-F1 | {pct(metric(base, 'macro_f1'))} | {pct(metric(cand, 'macro_f1'))} | {pct(delta_macro)} |")
    print(f"| Unsafe route | {pct(unsafe_before)} | {pct(unsafe_after)} | {pct(unsafe_after - unsafe_before)} |")
    print(f"| Handoff | {pct(handoff_before)} | {pct(handoff_after)} | {pct(handoff_after - handoff_before)} |")
    if cost_before or cost_after:
        print(f"| Estimated cost | {cost_before:.4f} | {cost_after:.4f} | {cost_after - cost_before:+.4f} |")
    if latency_before or latency_after:
        print(f"| Latency (ms) | {latency_before:.1f} | {latency_after:.1f} | {latency_after - latency_before:+.1f} |")

    print("\n### Upgrade policy verdict")
    failures = []
    if unsafe_after > unsafe_before:
        failures.append(f"unsafe-route regression: {pct(unsafe_before)} -> {pct(unsafe_after)}")
    if delta_accuracy < -args.accuracy_tolerance:
        failures.append(f"accuracy regression beyond tolerance ({args.accuracy_tolerance}): {pct(delta_accuracy)}")
    if failures:
        print("BLOCKED: " + "; ".join(failures))
        return 1
    print("ACCEPTED (quality/safety/latency/cost tradeoffs are advisory unless blocked above)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
