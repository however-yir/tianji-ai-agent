#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  echo "enterprise launch validation failed: $*" >&2
  exit 1
}

require_file() {
  local file="$1"
  [[ -f "$ROOT/$file" ]] || fail "missing $file"
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  grep -Eq "$pattern" "$ROOT/$file" || fail "missing pattern '$pattern' in $file"
}

required_files=(
  "docs/production-launch-plan.md"
  "docs/release-checklist.md"
  "docs/ops/runbook.md"
  "docs/security/agent-governance.md"
  "docs/enterprise-roadmap-300.md"
  "docs/observability/slo-and-alerting.md"
  "docs/agent-harness.md"
  "helm/tianji-ai-agent/Chart.yaml"
  "helm/tianji-ai-agent/values.yaml"
  "helm/tianji-ai-agent/templates/deployment.yaml"
  "src/tjxt/Dockerfile"
  "web/chat-ui/Dockerfile"
  ".github/workflows/ci.yml"
)

for file in "${required_files[@]}"; do
  require_file "$file"
done

roadmap_count="$(grep -Ec '^- \[ \] E[0-9]{3} ' "$ROOT/docs/enterprise-roadmap-300.md")"
if [[ "$roadmap_count" -ne 300 ]]; then
  fail "expected 300 enterprise roadmap items, found $roadmap_count"
fi

require_pattern 'RouteAgent -> 子Agent -> AgentHarness -> Runtime -> Observation -> SSE' "README.md"
require_pattern 'production-launch-plan\.md' "README.md"
require_pattern 'release-checklist\.md' "README.md"
require_pattern 'enterprise-roadmap-300\.md' "README.md"
require_pattern 'course\.query' "src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionSchema.java"
require_pattern 'course\.search' "src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionSchema.java"
require_pattern 'order\.preview' "src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionSchema.java"
require_pattern 'knowledgeops\.rag_query' "src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionSchema.java"
require_pattern 'mcp\.call' "src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionSchema.java"
require_pattern '禁止真实支付和自动确认购买' "src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionPolicyGuard.java"
require_pattern 'prometheus' "src/tjxt/tj-aigc/src/main/resources/application.yml"
require_pattern 'readiness' "helm/tianji-ai-agent/values.yaml"
require_pattern 'validate_enterprise_launch\.sh' ".github/workflows/ci.yml"

echo "enterprise launch validation passed"
