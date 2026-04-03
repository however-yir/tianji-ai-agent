#!/usr/bin/env python3
import json
from pathlib import Path

TEMPLATE = [
    {
        "task_id": "task-001",
        "input": "帮我规划一个学习计划并推荐课程",
        "expected": {
            "steps": ["需求分析", "课程检索", "计划生成"],
            "must_include": ["时间安排", "课程建议"]
        }
    }
]


def main():
    out = Path("docs/evaluation/agent_eval_dataset.sample.json")
    out.write_text(json.dumps(TEMPLATE, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {out}")


if __name__ == "__main__":
    main()
