#!/usr/bin/env python3
"""Deterministic internal link checker for README and docs/.

Checks that every repository-relative markdown link resolves to an existing file;
external URLs are ignored (network flakiness must not block CI).
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LINK = re.compile(r"\]\((?!https?://|mailto:|#)([^)#\s]+)")


def main() -> int:
    broken = []
    checked = 0
    for markdown in sorted(list(ROOT.glob("*.md")) + list(ROOT.glob("docs/**/*.md"))):
        text = markdown.read_text(encoding="utf-8")
        for match in LINK.finditer(text):
            target = match.group(1).split("#")[0]
            if not target:
                continue
            resolved = (markdown.parent / target).resolve()
            if not resolved.exists():
                broken.append(f"{markdown.relative_to(ROOT)} -> {target}")
            checked += 1
    if broken:
        print("BROKEN INTERNAL LINKS:")
        for item in broken:
            print(f"  - {item}")
        return 1
    print(f"internal links checked: {checked}, all resolve")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
