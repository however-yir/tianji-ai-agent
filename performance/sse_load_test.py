#!/usr/bin/env python3
"""Hermetic SSE load test for the Tianji chat endpoint.

Targets a locally running tj-aigc service started with the dev-demo profile, so the
orchestration + SSE contract is measured WITHOUT any real LLM call. Start the service with:

    SPRING_PROFILES_ACTIVE=dev-demo java -jar tj-aigc.jar

Then:

    python3 performance/sse_load_test.py --base-url http://127.0.0.1:8094 --concurrency 10 --requests 50

Metrics: request count, error rate, time-to-first-event, stream duration, P50/P95/P99,
SSE completion rate, missing-STOP rate.
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field


@dataclass
class Result:
    ok: bool = False
    errored: bool = False
    stopped: bool = False
    first_event_ms: float | None = None
    duration_ms: float | None = None
    event_types: list[int] = field(default_factory=list)


def parse_event(line: str) -> dict | None:
    line = line.strip()
    if not line.startswith("data:"):
        return None
    try:
        return json.loads(line.removeprefix("data:").strip())
    except json.JSONDecodeError:
        return None


def run_one(base_url: str, session_id: str, question: str, timeout: float) -> Result:
    url = base_url.rstrip("/") + "/chat"
    payload = json.dumps({"question": question, "sessionId": session_id}).encode("utf-8")
    request = urllib.request.Request(url, data=payload, headers={
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    })
    result = Result()
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            for raw in response:
                event = parse_event(raw.decode("utf-8"))
                if event is None:
                    continue
                if result.first_event_ms is None:
                    result.first_event_ms = (time.monotonic() - started) * 1000
                event_type = event.get("eventType")
                result.event_types.append(event_type)
                if event_type == 1002:  # STOP
                    result.stopped = True
                    break
        result.duration_ms = (time.monotonic() - started) * 1000
        result.ok = result.stopped
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        result.errored = True
        result.duration_ms = (time.monotonic() - started) * 1000
        result.ok = False
        print(f"  ! {session_id}: {exc}")
    return result


def pct(values: list[float], percentile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, round(percentile / 100 * (len(ordered) - 1)))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser(description="Hermetic SSE load test (dev-demo target).")
    parser.add_argument("--base-url", default="http://127.0.0.1:8094")
    parser.add_argument("--concurrency", type=int, default=5)
    parser.add_argument("--requests", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    print(f"[load] concurrency={args.concurrency} requests={args.requests} target={args.base_url}")
    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [
            pool.submit(run_one, args.base_url, f"load-{i}", "推荐一门 Java 课程", args.timeout)
            for i in range(args.requests)
        ]
        results = [f.result() for f in futures]
    wall = time.monotonic() - started

    ok = [r for r in results if r.ok]
    throttled = [r.first_event_ms for r in ok if r.first_event_ms is not None]
    durations = [r.duration_ms for r in ok if r.duration_ms is not None]
    missing_stop = [r for r in results if not r.errored and not r.stopped]
    complete_events = sum(1 for r in ok if r.event_types and r.event_types[-1] == 1002)

    report = {
        "target": args.base_url,
        "concurrency": args.concurrency,
        "requests": args.requests,
        "wall_seconds": round(wall, 2),
        "request_count": len(results),
        "error_rate": round(len([r for r in results if r.errored]) / len(results), 4),
        "success_rate": round(len(ok) / len(results), 4),
        "p50_first_event_ms": round(pct(throttled, 50), 1),
        "p95_first_event_ms": round(pct(throttled, 95), 1),
        "p99_first_event_ms": round(pct(throttled, 99), 1),
        "p50_stream_ms": round(pct(durations, 50), 1),
        "p95_stream_ms": round(pct(durations, 95), 1),
        "p99_stream_ms": round(pct(durations, 99), 1),
        "sse_completion_rate": round(complete_events / len(ok), 4) if ok else 0.0,
        "missing_stop_rate": round(len(missing_stop) / len(results), 4) if results else 0.0,
        "fake_model_used": True,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["missing_stop_rate"] == 0 and report["error_rate"] < 0.05 else 1


if __name__ == "__main__":
    raise SystemExit(main())
