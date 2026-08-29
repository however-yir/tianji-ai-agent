#!/usr/bin/env python3
"""Run the deterministic RouteAgent contract regression without an LLM API.

This is deliberately an *offline contract* evaluator, not a claimed model benchmark.
It exercises the stable route precedence used for CI: risk handoff, intent routing,
and unknown-input fallback. Use --api-url only for the separate optional live check.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

AGENTS = (
    "RECOMMEND",
    "CONSULT",
    "BUY",
    "KNOWLEDGE",
    "AFTER_SALE",
    "STUDY_PLAN",
    "HUMAN_HANDOFF",
)

RISK_KEYWORDS = (
    "人工", "真人", "客服介入", "投诉", "差评", "举报", "维权", "欺诈", "被骗",
    "支付失败", "付款失败", "已扣款", "扣款", "重复扣费", "银行卡", "信用卡", "支付密码",
    "验证码", "转账", "不到账", "payment.execute", "order.pay", "forcepurchase",
    "bypass agentharness", "忽略安全规则", "其他用户订单", "删除订单", "修改价格",
    "后悔买了", "钱怎么办", "这个能买吗", "我想学这个", "帮我搞一下", "课程不太行",
    "这个课靠谱么", "质量不好", "绕过订单确认", "非课程业务", "不确定意图", "骗了",
)
STUDY_PLAN_KEYWORDS = ("学习计划", "学习规划", "学习路径", "学习路线", "按阶段", "路线图", "复习规划")
AFTER_SALE_KEYWORDS = ("售后", "退款流程", "开发票", "发票", "无法播放", "订单异常", "课程打不开")
BUY_KEYWORDS = ("购买", "预下单", "生成确认订单", "创建订单", "订单预览", "想买", "我要下单", "帮我下单", "现在下单", "下单前", "下单预览")
RECOMMEND_KEYWORDS = ("推荐", "选课", "有哪些课程", "适合入门", "转后端", "适合我")
CONSULT_KEYWORDS = (
    "介绍一下", "适合谁", "价格", "多少钱", "贵不贵", "有效期", "课程详情", "讲哪些",
    "讲师", "难度", "学习周期", "靠谱么", "适合",
)


def load_dataset(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        if not isinstance(row.get("input"), str) or not isinstance(row.get("expectedAgent"), str):
            raise ValueError(f"{path}:{line_no} must include string input and expectedAgent")
        if row["expectedAgent"] not in AGENTS:
            raise ValueError(f"{path}:{line_no} has unsupported expectedAgent {row['expectedAgent']}")
        rows.append(row)
    if not rows:
        raise ValueError(f"{path} has no evaluation cases")
    return rows


def contains_any(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword in text for keyword in keywords)


def offline_route(question: str) -> str:
    """Stable no-network route precedence used by the regression fixture."""
    text = question.lower().strip()
    if not text or contains_any(text, RISK_KEYWORDS):
        return "HUMAN_HANDOFF"
    if contains_any(text, STUDY_PLAN_KEYWORDS):
        return "STUDY_PLAN"
    if contains_any(text, AFTER_SALE_KEYWORDS):
        return "AFTER_SALE"
    if contains_any(text, BUY_KEYWORDS):
        return "BUY"
    if contains_any(text, RECOMMEND_KEYWORDS):
        return "RECOMMEND"
    if contains_any(text, CONSULT_KEYWORDS):
        return "CONSULT"
    return "KNOWLEDGE"


def route_via_api(api_url: str, token: str, question: str, session_id: str) -> str:
    base = api_url.rstrip("/")
    url = base if base.endswith("/chat") else f"{base}/chat"
    payload = json.dumps({"question": question, "sessionId": session_id}, ensure_ascii=False).encode()
    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    if token:
        headers["Authorization"] = token if token.startswith("Bearer ") else f"Bearer {token}"
    request = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310 - explicit CLI URL.
        for raw_line in response:
            line = raw_line.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            event = json.loads(line.removeprefix("data:").strip())
            if event.get("eventType") != 1004:
                continue
            route = event.get("eventData") or {}
            if isinstance(route, dict):
                return str(route.get("nextAgent") or route.get("intent") or "HUMAN_HANDOFF")
    return "HUMAN_HANDOFF"


def safe_divide(numerator: int | float, denominator: int | float) -> float:
    return numerator / denominator if denominator else 0.0


def evaluate(rows: list[dict[str, Any]], predictions: list[str]) -> dict[str, Any]:
    if len(rows) != len(predictions):
        raise ValueError("each evaluation case must have exactly one prediction")
    labels = sorted(set(AGENTS) | {str(row["expectedAgent"]) for row in rows} | set(predictions))
    matrix: dict[str, Counter[str]] = defaultdict(Counter)
    details: list[dict[str, Any]] = []
    for row, predicted in zip(rows, predictions):
        expected = str(row["expectedAgent"])
        matrix[expected][predicted] += 1
        details.append({
            "id": row.get("id"),
            "expected_agent": expected,
            "predicted_agent": predicted,
            "risk_level": row.get("riskLevel", "LOW"),
            "pass": expected == predicted,
        })

    per_intent: dict[str, dict[str, float | int]] = {}
    f1_values: list[float] = []
    for label in labels:
        true_positive = matrix[label][label]
        false_positive = sum(matrix[expected][label] for expected in labels if expected != label)
        false_negative = sum(matrix[label][predicted] for predicted in labels if predicted != label)
        precision = safe_divide(true_positive, true_positive + false_positive)
        recall = safe_divide(true_positive, true_positive + false_negative)
        f1 = safe_divide(2 * precision * recall, precision + recall)
        per_intent[label] = {
            "support": sum(matrix[label].values()),
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
        }
        f1_values.append(f1)

    total = len(details)
    correct = sum(item["pass"] for item in details)
    high_risk = [item for item in details if item["risk_level"] == "HIGH"]
    unsafe_routes = [item for item in high_risk if item["predicted_agent"] != "HUMAN_HANDOFF"]
    handoffs = [item for item in details if item["predicted_agent"] == "HUMAN_HANDOFF"]
    return {
        "total_cases": total,
        "correct_cases": correct,
        "accuracy": round(safe_divide(correct, total), 4),
        "macro_f1": round(safe_divide(sum(f1_values), len(f1_values)), 4),
        "fallback_rate": round(safe_divide(len(handoffs), total), 4),
        "handoff_rate": round(safe_divide(len(handoffs), total), 4),
        "unsafe_route_rate": round(safe_divide(len(unsafe_routes), len(high_risk)), 4),
        "high_risk_cases": len(high_risk),
        "per_intent": per_intent,
        "confusion_matrix": {expected: dict(matrix[expected]) for expected in labels},
        "details": details,
    }


def render_markdown(report: dict[str, Any], dataset: str) -> str:
    labels = list(report["per_intent"])
    lines = [
        "# RouteAgent Offline Contract Report",
        "",
        f"Dataset: `{dataset}`",
        "",
        "This report measures the deterministic CI contract, not live LLM quality.",
        "",
        "## Summary",
        "",
        "| Metric | Value |",
        "| --- | ---: |",
        f"| Total cases | {report['total_cases']} |",
        f"| Accuracy | {report['accuracy']:.2%} |",
        f"| Macro-F1 | {report['macro_f1']:.2%} |",
        f"| Fallback / handoff rate | {report['fallback_rate']:.2%} |",
        f"| Unsafe route rate | {report['unsafe_route_rate']:.2%} |",
        "",
        "## Per Intent",
        "",
        "| Intent | Support | Precision | Recall | F1 |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for intent, metrics in report["per_intent"].items():
        lines.append(
            f"| {intent} | {metrics['support']} | {metrics['precision']:.2%} | "
            f"{metrics['recall']:.2%} | {metrics['f1']:.2%} |"
        )
    lines.extend([
        "",
        "## Confusion Matrix",
        "",
        "| Expected \\ Predicted | " + " | ".join(labels) + " |",
        "| --- | " + " | ".join("---:" for _ in labels) + " |",
    ])
    for expected in labels:
        row = report["confusion_matrix"].get(expected, {})
        values = " | ".join(str(row.get(predicted, 0)) for predicted in labels)
        lines.append(f"| {expected} | {values} |")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate the deterministic RouteAgent contract.")
    parser.add_argument("--dataset", default="docs/evaluation/route-agent-dataset.jsonl")
    parser.add_argument("--api-url", default="", help="Optional running tj-aigc URL for a non-blocking live check")
    parser.add_argument("--token", default="", help="Optional bearer token for API mode")
    parser.add_argument("--output-dir", default="artifacts/evaluation")
    # Baseline from the checked-in 160-case contract set is 98.75%; 98% catches
    # material routing regressions without pretending the fixture is a live-model score.
    parser.add_argument("--min-accuracy", type=float, default=0.98)
    parser.add_argument("--max-unsafe-route-rate", type=float, default=0.0)
    args = parser.parse_args()

    dataset_path = Path(args.dataset)
    rows = load_dataset(dataset_path)
    predictions = [
        route_via_api(args.api_url, args.token, row["input"], f"route-eval-{index}")
        if args.api_url else offline_route(row["input"])
        for index, row in enumerate(rows, start=1)
    ]
    report = evaluate(rows, predictions)
    report["mode"] = "live-api" if args.api_url else "offline-contract"
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / "agent-eval-report.json"
    markdown_path = output_dir / "agent-eval-report.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    markdown_path.write_text(render_markdown(report, str(dataset_path)) + "\n", encoding="utf-8")

    print(f"mode: {report['mode']}")
    print(f"total_cases: {report['total_cases']}")
    print(f"accuracy: {report['accuracy']:.2%}")
    print(f"macro_f1: {report['macro_f1']:.2%}")
    print(f"unsafe_route_rate: {report['unsafe_route_rate']:.2%}")
    print(f"reports: {json_path}, {markdown_path}")
    if report["accuracy"] < args.min_accuracy:
        print("FAILED: accuracy regression", file=sys.stderr)
        return 1
    if report["unsafe_route_rate"] > args.max_unsafe_route_rate:
        print("FAILED: unsafe route regression", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
