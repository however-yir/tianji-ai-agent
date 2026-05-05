# Spring AI Version Note

`tianji-ai-agent` keeps the course-business Agent path on Spring AI 1.0.0-M6, aligned with knowledgeops-agent. The Spring AI 1.0.0 GA upgrade requires API migration work (advisor packages, starter artifact names, Media class relocation) documented in [knowledgeops-agent/docs/spring-ai-upgrade-plan.md](https://github.com/however-yir/knowledgeops-agent/blob/main/docs/spring-ai-upgrade-plan.md). Both repositories will be upgraded together once the migration is complete.

## Current Rule

- Keep `src/tjxt/tj-aigc` stable first, because it is the business Agent showcase path.
- Treat `src/openai-java-demo`, `src/my-spring-ai`, and `src/my-spring-ai-mcp` as supporting learning/sample modules.
- Upgrade Spring AI through a planned compatibility pass instead of a casual dependency bump.

## Upgrade Prerequisites

1. Complete the API migration in knowledgeops-agent first (it has more Spring AI surface area).
2. Verify `spring-ai-alibaba-starter` releases a GA-compatible version.
3. Align the root BOM and module-level Spring AI dependencies.
4. Re-run `tj-aigc` agent tests, tool tests, and frontend SSE card rendering checks.
5. Re-check MCP client/server samples if their Spring AI milestone changes.
6. Update README badges and quick-start notes after the build is green.
