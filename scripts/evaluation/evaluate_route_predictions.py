#!/usr/bin/env python3
"""Evaluate RouteAgent predictions against a JSONL dataset."""

import argparse
import json
from collections import defaultdict
from pathlib import Path


def load_jsonl(path: Path) -> list[dict]:
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            text = line.strip()
            if not text:
                continue
            try:
                rows.append(json.loads(text))
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid JSONL at {path}:{line_no}: {exc}") from exc
    return rows


def evaluate(dataset: list[dict], predictions: list[dict]) -> dict:
    prediction_map = {str(row.get("id")): row for row in predictions}
    confusion: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    details = []

    for case in dataset:
        case_id = str(case.get("id"))
        expected = str(case.get("expected_agent") or case.get("expectedAgent") or "").strip()
        predicted_row = prediction_map.get(case_id, {})
        predicted = str(
            predicted_row.get("predicted_agent") or predicted_row.get("predictedAgent") or ""
        ).strip()
        passed = expected == predicted
        confusion[expected][predicted or "MISSING"] += 1
        details.append(
            {
                "id": case_id,
                "expected_agent": expected,
                "predicted_agent": predicted or "MISSING",
                "confidence": predicted_row.get("confidence"),
                "pass": passed,
                "risk_level": case.get("risk_level", "LOW"),
            }
        )

    total = len(details)
    passed = sum(1 for item in details if item["pass"])
    covered = sum(1 for item in details if item["predicted_agent"] != "MISSING")
    high_risk = [item for item in details if item["risk_level"] == "HIGH"]
    high_risk_passed = sum(1 for item in high_risk if item["pass"])

    return {
        "summary": {
            "total": total,
            "passed": passed,
            "accuracy": round(passed / max(total, 1), 4),
            "coverage": round(covered / max(total, 1), 4),
            "high_risk_total": len(high_risk),
            "high_risk_accuracy": round(high_risk_passed / max(len(high_risk), 1), 4),
        },
        "confusion_matrix": {
            expected: dict(predicted_counts)
            for expected, predicted_counts in sorted(confusion.items())
        },
        "details": details,
    }


def render_markdown(report: dict) -> str:
    summary = report["summary"]
    lines = [
        "# RouteAgent Evaluation Report",
        "",
        "## Summary",
        "",
        "| Metric | Value |",
        "| --- | ---: |",
        f"| Total | {summary['total']} |",
        f"| Passed | {summary['passed']} |",
        f"| Accuracy | {summary['accuracy']:.2%} |",
        f"| Coverage | {summary['coverage']:.2%} |",
        f"| High Risk Cases | {summary['high_risk_total']} |",
        f"| High Risk Accuracy | {summary['high_risk_accuracy']:.2%} |",
        "",
        "## Confusion Matrix",
        "",
    ]

    for expected, predicted_counts in report["confusion_matrix"].items():
        parts = ", ".join(
            f"{predicted}={count}" for predicted, count in sorted(predicted_counts.items())
        )
        lines.append(f"- `{expected}`: {parts}")

    lines.extend(
        [
            "",
            "## Details",
            "",
            "| ID | Expected | Predicted | Confidence | Risk | Pass |",
            "| --- | --- | --- | ---: | --- | :---: |",
        ]
    )
    for item in report["details"]:
        confidence = item["confidence"]
        confidence_text = "-" if confidence is None else f"{float(confidence):.2f}"
        lines.append(
            f"| {item['id']} | {item['expected_agent']} | {item['predicted_agent']} | "
            f"{confidence_text} | {item['risk_level']} | {'Y' if item['pass'] else 'N'} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="docs/evaluation/route-agent-dataset.jsonl")
    parser.add_argument(
        "--predictions",
        default="docs/evaluation/route-agent-predictions.sample.jsonl",
    )
    parser.add_argument("--output-json", default="docs/evaluation/route-agent-report.json")
    parser.add_argument("--output-md", default="docs/evaluation/route-agent-report.md")
    parser.add_argument("--min-accuracy", type=float, default=0.85)
    args = parser.parse_args()

    report = evaluate(load_jsonl(Path(args.dataset)), load_jsonl(Path(args.predictions)))
    Path(args.output_json).write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    Path(args.output_md).write_text(render_markdown(report), encoding="utf-8")
    print(f"written: {args.output_json}")
    print(f"written: {args.output_md}")
    print(f"accuracy: {report['summary']['accuracy']:.2%}")
    if report["summary"]["accuracy"] < args.min_accuracy:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
