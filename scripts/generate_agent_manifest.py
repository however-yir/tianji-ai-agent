#!/usr/bin/env python3
"""Generate (or verify) the machine-readable Agent release manifest.

Deterministic: hashes come from repository content (prompts, evaluation dataset, golden
runs); versions come from the project poms. Nothing dynamic is stored in git - CI
generates the artifact.

Usage:
    python3 scripts/generate_agent_manifest.py --output-dir artifacts/manifest
    python3 scripts/generate_agent_manifest.py --verify
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_sha256(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def dir_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    for file in sorted(p for p in path.rglob("*") if p.is_file()):
        digest.update(file.relative_to(path).as_posix().encode())
        digest.update(file.read_bytes())
    return digest.hexdigest()


def pom_property(pom: Path, prop: str) -> str:
    content = pom.read_text(encoding="utf-8")
    match = re.search(rf"<{prop}>([^<]+)</{prop}>", content)
    return match.group(1).strip() if match else ""


def prompt_versions(root: Path) -> dict:
    versions = {}
    for spec in sorted((root / "src/tjxt/tj-aigc/src/main/resources/prompts").glob("*/v*.md")):
        prompt_id = spec.parent.name
        version = spec.stem
        versions[prompt_id] = {
            "version": version,
            "checksum": file_sha256(spec),
        }
    return versions


def dataset_hash(root: Path) -> str:
    return file_sha256(root / "docs/evaluation/route-agent-dataset.jsonl")


def golden_runs_hash(root: Path) -> str:
    return dir_sha256(root / "tests/golden-runs")


def model_profiles(path: Path) -> list:
    content = path.read_text(encoding="utf-8")
    profiles = re.findall(r"^\s{8}(\w+):\n", content, re.MULTILINE)
    return profiles


def build_manifest(root: Path, metrics: Path | None, image_digest: str = "") -> dict:
    aigc_pom = root / "src/tjxt/tj-aigc/pom.xml"
    root_pom = root / "src/tjxt/pom.xml"
    manifest = {
        "schemaVersion": 1,
        "gitSha": __import__("os").environ.get("GITHUB_SHA", "local"),
        "buildTime": __import__("os").environ.get("GITHUB_ACTIONS", "") == "true"
            and __import__("os").environ.get("SOURCE_DATE_EPOCH", "") or None,
        "promptVersions": prompt_versions(root),
        "promptChecksumAlgorithm": "sha256",
        "modelProfiles": model_profiles(root / "src/tjxt/tj-aigc/src/main/resources/application.yml"),
        "policyVersion": "v1.1-route-safety-wordlist+action-guard",
        "executionBudgetVersion": "v1-budget",
        "evaluationDatasetHash": dataset_hash(root),
        "datasetHashAlgorithm": "sha256",
        "goldenRunHash": golden_runs_hash(root),
        "javaVersion": pom_property(aigc_pom, "maven.compiler.source") or "17",
        "springBootVersion": pom_property(root_pom, "spring-boot-starter-parent") or pom_property(root_pom, "spring-boot.version"),
        "springAiVersion": pom_property(root_pom, "spring-ai.version"),
        "frontendVersion": root.joinpath("web/chat-ui/package.json").exists()
            and json.loads(root.joinpath("web/chat-ui/package.json").read_text()).get("version", "") or "",
        "containerImage": "ghcr.io/however-yir/tianji-ai-agent",
        "imageDigest": image_digest,
    }
    if metrics is not None and metrics.exists():
        manifest["evaluationMetrics"] = json.loads(metrics.read_text(encoding="utf-8"))
    return manifest


def validate(root: Path, manifest: dict) -> list[str]:
    problems = []
    prompt_dir = root / "src/tjxt/tj-aigc/src/main/resources/prompts"
    seen = set()
    for spec in sorted(prompt_dir.glob("*/v*.md")):
        prompt_id = spec.parent.name
        if prompt_id in seen:
            problems.append(f"duplicate prompt version dir: {prompt_id}")
        seen.add(prompt_id)
        resolved = manifest.get("promptVersions", {}).get(prompt_id)
        if not resolved:
            problems.append(f"prompt {prompt_id} missing from manifest")
        elif resolved["checksum"] != file_sha256(spec):
            problems.append(f"prompt checksum mismatch: {prompt_id}")
    if manifest.get("evaluationDatasetHash") != dataset_hash(root):
        problems.append("dataset hash mismatch")
    if manifest.get("goldenRunHash") != golden_runs_hash(root):
        problems.append("golden-run hash mismatch")
    if not manifest.get("springAiVersion"):
        problems.append("springAiVersion missing")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="Agent release manifest generator")
    parser.add_argument("--output-dir", default="artifacts/manifest")
    parser.add_argument("--metrics", default="")
    parser.add_argument("--image-digest", default="")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    metrics = Path(args.metrics) if args.metrics else None
    manifest = build_manifest(ROOT, metrics, args.image_digest)

    if args.verify:
        problems = validate(ROOT, manifest)
        if problems:
            print("MANIFEST INVALID:")
            for p in problems:
                print(" -", p)
            return 1
        print("MANIFEST VALID")
        return 0

    out = ROOT / args.output_dir
    out.mkdir(parents=True, exist_ok=True)
    target = out / "agent-release-manifest.json"
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"manifest written: {target}")
    print(f"prompts: {len(manifest['promptVersions'])} | dataset hash: {manifest['evaluationDatasetHash'][:16]} | golden hash: {manifest['goldenRunHash'][:16]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
