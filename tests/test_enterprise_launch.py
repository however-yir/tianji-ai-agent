from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    return (REPO_ROOT / relative_path).read_text(encoding="utf-8")


def test_enterprise_launch_docs_exist_and_are_linked():
    required_docs = [
        "docs/production-launch-plan.md",
        "docs/release-checklist.md",
        "docs/ops/runbook.md",
        "docs/security/agent-governance.md",
    ]
    for doc in required_docs:
        assert (REPO_ROOT / doc).is_file(), doc

    readme = read_text("README.md")
    for doc in required_docs:
        assert doc in readme


def test_agent_harness_and_operational_guards_are_documented():
    readme = read_text("README.md")
    governance = read_text("docs/security/agent-governance.md")
    schema = read_text("src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionSchema.java")
    policy = read_text("src/tjxt/tj-aigc/src/main/java/com/tianji/aigc/harness/ActionPolicyGuard.java")

    assert "RouteAgent -> 子Agent -> AgentHarness -> Runtime -> Observation -> SSE" in readme
    for action_type in [
        "course.query",
        "course.search",
        "order.preview",
        "knowledgeops.rag_query",
        "mcp.call",
    ]:
        assert action_type in schema
        assert action_type in governance
    assert "禁止真实支付和自动确认购买" in policy
