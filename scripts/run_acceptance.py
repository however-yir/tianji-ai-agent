#!/usr/bin/env python3
"""Run the deterministic acceptance suite and emit a compact machine-readable report."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]


def run_step(name: str, command: list[str], cwd: Path) -> dict[str, Any]:
    started = time.monotonic()
    print(f"\n[acceptance] {name}")
    print("$ " + " ".join(command))
    result = subprocess.run(command, cwd=cwd, check=False)
    return {
        "name": name,
        "command": command,
        "success": result.returncode == 0,
        "exit_code": result.returncode,
        "duration_seconds": round(time.monotonic() - started, 2),
    }


def require_command(command: str) -> None:
    if shutil.which(command) is None:
        raise RuntimeError(f"required command is not available: {command}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Tianji's no-LLM acceptance suite.")
    parser.add_argument("--output-dir", default="artifacts/acceptance")
    parser.add_argument("--skip-frontend", action="store_true")
    args = parser.parse_args()

    for command in ("mvn",):
        require_command(command)
    if not args.skip_frontend:
        require_command("npm")

    output_dir = (ROOT / args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    evaluation_dir = output_dir / "evaluation"
    steps: list[dict[str, Any]] = []
    commands: list[tuple[str, list[str], Path]] = [
        (
            "offline route contract",
            [sys.executable, "scripts/evaluation/evaluate_route_agent.py", "--output-dir", str(evaluation_dir)],
            ROOT,
        ),
        (
            "tj-aigc dependency chain",
            ["mvn", "-B", "-ntp", "-f", "src/tjxt/pom.xml", "-pl", "tj-aigc", "-am", "-DskipTests", "install"],
            ROOT,
        ),
        (
            "agent governance and SSE contracts",
            [
                "mvn", "-B", "-ntp", "-f", "src/tjxt/tj-aigc/pom.xml",
                "-Dtest=RouteSafetyPolicyTest,ActionPolicyGuardTest,AgentHarnessServiceTest,AgentServiceImplTest,SseEventContractTest,CourseToolsHarnessTest,OrderToolsHarnessTest",
                "test",
            ],
            ROOT,
        ),
    ]
    if not args.skip_frontend:
        commands.extend([
            ("frontend dependencies", ["npm", "ci"], ROOT / "web/chat-ui"),
            ("frontend SSE contract", ["npm", "run", "test:run"], ROOT / "web/chat-ui"),
        ])

    for name, command, cwd in commands:
        step = run_step(name, command, cwd)
        steps.append(step)
        if not step["success"]:
            break

    report = {
        "suite": "tianji-agent-acceptance",
        "offline": True,
        "success": all(step["success"] for step in steps) and len(steps) == len(commands),
        "cases": {
            "recommendation": "AgentServiceImplTest + CourseToolsHarnessTest",
            "course_consultation": "AgentServiceImplTest + CourseToolsHarnessTest",
            "order_preview": "OrderToolsHarnessTest",
            "illegal_payment": "ActionPolicyGuardTest",
            "handoff": "RouteSafetyPolicyTest + AgentServiceImplTest",
            "runtime_timeout": "AgentHarnessServiceTest + SseEventContractTest",
        },
        "steps": steps,
    }
    report_path = output_dir / "acceptance-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\n[acceptance] report: {report_path}")
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
