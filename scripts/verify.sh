#!/usr/bin/env bash
# One-command local verification: deterministic gates only (no live LLM, no OWASP DB,
# no heavy load test, no image publish).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> frontend lint/test/build"
(cd web/chat-ui && npm ci --silent && npm run lint && npm run test:run && npm run build)

echo "==> python tests + lint + compile"
python3 -m pytest -q tests
ruff check tests scripts performance
python3 -m compileall -q tests scripts performance

echo "==> offline route evaluation"
python3 scripts/evaluation/evaluate_route_agent.py --output-dir /tmp/h3-eval

echo "==> golden run replay"
python3 scripts/replay_agent_run.py

echo "==> agent release manifest (verify)"
python3 scripts/generate_agent_manifest.py --verify

echo "==> java core tests + coverage + static gates"
mvn -B -ntp -f src/tjxt/pom.xml -pl tj-aigc -am -DskipTests install
mvn -B -ntp -f src/tjxt/tj-aigc/pom.xml verify checkstyle:check spotbugs:check

echo "==> helm lint/template"
helm lint helm/tianji-ai-agent
helm template tianji-prod helm/tianji-ai-agent >/dev/null

echo "==> acceptance suite"
bash scripts/acceptance.sh

echo "ALL VERIFY STEPS PASSED"
