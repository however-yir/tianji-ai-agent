#!/usr/bin/env python3
import argparse
import json
import sys
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

AGENTS = [
    "RECOMMEND",
    "CONSULT",
    "BUY",
    "KNOWLEDGE",
    "AFTER_SALE",
    "STUDY_PLAN",
    "HUMAN_HANDOFF",
]

COMPLAINT_KEYWORDS = ("投诉", "差评", "举报", "维权", "欺诈", "被骗", "骗人", "315")
PAYMENT_KEYWORDS = ("支付失败", "付款失败", "已扣款", "扣款", "重复扣费", "银行卡", "信用卡", "支付密码", "验证码", "转账", "不到账", "退款到账")
HUMAN_KEYWORDS = ("人工", "真人", "客服介入", "电话联系", "转人工")
BUY_KEYWORDS = ("购买", "下单", "预下单", "生成确认订单", "创建订单", "订单预览")
RECOMMEND_KEYWORDS = ("推荐", "选课", "有哪些课程", "适合入门", "转后端")
CONSULT_KEYWORDS = ("介绍一下", "适合谁", "价格", "多少钱", "有效期", "课程详情", "讲哪些", "讲师")
AFTER_SALE_KEYWORDS = ("售后", "退款流程", "开发票", "发票", "无法播放", "订单异常")
STUDY_PLAN_KEYWORDS = ("学习计划", "学习规划", "学习路径", "规划", "排一个", "按阶段")


def load_dataset(path: Path) -> list[dict]:
    rows = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        if "input" not in row or "expectedAgent" not in row:
            raise ValueError(f"{path}:{line_no} must include input and expectedAgent")
        rows.append(row)
    return rows


def contains_any(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword.lower() in text for keyword in keywords)


def offline_route(question: str) -> str:
    text = question.lower()
    if contains_any(text, COMPLAINT_KEYWORDS + PAYMENT_KEYWORDS + HUMAN_KEYWORDS):
        return "HUMAN_HANDOFF"
    if contains_any(text, BUY_KEYWORDS):
        return "BUY"
    if contains_any(text, RECOMMEND_KEYWORDS):
        return "RECOMMEND"
    if contains_any(text, CONSULT_KEYWORDS):
        return "CONSULT"
    if contains_any(text, AFTER_SALE_KEYWORDS):
        return "AFTER_SALE"
    if contains_any(text, STUDY_PLAN_KEYWORDS):
        return "STUDY_PLAN"
    return "KNOWLEDGE"


def route_via_api(api_url: str, token: str, question: str, session_id: str) -> str:
    base = api_url.rstrip("/")
    url = base if base.endswith("/chat") else f"{base}/chat"
    payload = json.dumps({"question": question, "sessionId": session_id}, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    if token:
        headers["Authorization"] = token if token.startswith("Bearer ") else f"Bearer {token}"
    request = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:
        for raw_line in response:
            line = raw_line.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            data = line.removeprefix("data:").strip()
            if not data or data == "[DONE]":
                continue
            event = json.loads(data)
            if event.get("eventType") == 1004:
                route = event.get("eventData") or {}
                if isinstance(route, dict):
                    return str(route.get("nextAgent") or route.get("intent") or "UNKNOWN")
                return str(route)
    return "UNKNOWN"


def print_confusion_matrix(labels: list[str], matrix: dict[str, Counter]) -> None:
    print("\nConfusion matrix (rows=expected, cols=predicted)")
    print("\t" + "\t".join(labels))
    for expected in labels:
        row = [str(matrix[expected][predicted]) for predicted in labels]
        print(f"{expected}\t" + "\t".join(row))


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate RouteAgent routing accuracy.")
    parser.add_argument("--dataset", default="docs/evaluation/route-agent-dataset.jsonl")
    parser.add_argument("--api-url", default="", help="Optional running tj-aigc base URL, for example http://127.0.0.1:8094")
    parser.add_argument("--token", default="", help="Optional bearer token for API mode")
    parser.add_argument("--min-accuracy", type=float, default=0.85)
    args = parser.parse_args()

    rows = load_dataset(Path(args.dataset))
    matrix: dict[str, Counter] = defaultdict(Counter)
    mistakes = []

    for index, row in enumerate(rows, start=1):
        expected = row["expectedAgent"]
        predicted = route_via_api(args.api_url, args.token, row["input"], f"route-eval-{index}") if args.api_url else offline_route(row["input"])
        matrix[expected][predicted] += 1
        if expected != predicted:
            mistakes.append((row["id"], row["input"], expected, predicted))

    total = len(rows)
    correct = total - len(mistakes)
    accuracy = correct / total if total else 0.0
    labels = sorted(set(AGENTS) | {row["expectedAgent"] for row in rows} | {item[3] for item in mistakes})

    mode = "api" if args.api_url else "offline"
    print(f"RouteAgent eval mode: {mode}")
    print(f"Dataset: {args.dataset}")
    print(f"Samples: {total}")
    print(f"Accuracy: {accuracy:.2%} ({correct}/{total})")
    print_confusion_matrix(labels, matrix)

    if mistakes:
        print("\nMistakes")
        for case_id, question, expected, predicted in mistakes:
            print(f"- {case_id}: expected={expected}, predicted={predicted}, input={question}")

    if accuracy < args.min_accuracy:
        print(f"\nFAILED: accuracy {accuracy:.2%} < {args.min_accuracy:.2%}", file=sys.stderr)
        return 1
    print(f"\nPASSED: accuracy {accuracy:.2%} >= {args.min_accuracy:.2%}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
