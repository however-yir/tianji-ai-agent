# Spring AI Version Note

`tianji-ai-agent` keeps the course-business Agent path on the Spring AI version that is known to work with the current teaching/sample modules. Some retained sample modules use nearby milestone versions because they demonstrate MCP or Spring AI Alibaba features from that period.

## Current Rule

- Keep `代码/tjxt/tj-aigc` stable first, because it is the business Agent showcase path.
- Treat `代码/openai-java-demo`, `代码/my-spring-ai`, and `代码/my-spring-ai-mcp` as supporting learning/sample modules.
- Upgrade Spring AI through a planned compatibility pass instead of a casual dependency bump.

## Upgrade Checklist

1. Align the root BOM and module-level Spring AI dependencies.
2. Re-run `tj-aigc` agent tests, tool tests, and frontend SSE card rendering checks.
3. Re-check MCP client/server samples if their Spring AI milestone changes.
4. Update README badges and quick-start notes after the build is green.
