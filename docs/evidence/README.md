# Tianji AI Agent Evidence Pack

This pack collects the shortest public proof path for reviewing the business Agent workflow.

## Runtime Evidence

- Local proof path: `bash scripts/quick-start-mac.sh`
- Main CI: `.github/workflows/ci.yml`
- Baseline release: `AI Matrix Baseline 2026.05`
- Release: `v0.1.0 - Course Agent Showcase`
- Frontend app: `web/chat-ui`
- Backend Agent module: `代码/tjxt/tj-aigc`

## Product And Architecture Evidence

- Demo GIF: `docs/assets/screenshots/demo.gif`
- Default chat screenshot: `docs/assets/screenshots/chat-default.jpg`
- Course card screenshot: `docs/assets/screenshots/course-card.jpg`
- Order card screenshot: `docs/assets/screenshots/order-card.jpg`
- Agent design: `docs/agent-design.md`
- MCP extension guide: `docs/mcp-extension-guide.md`
- Observability guide: `docs/observability/slo-and-alerting.md`

## Verification Checklist

- Start `dev-demo` without external model credentials.
- Send a recommendation prompt and confirm the route event targets `RECOMMEND`.
- Send a course detail prompt and confirm evidence/citation payloads render.
- Send a pre-order prompt and confirm `PARAM` includes the order card data.
- Check memory behavior across multiple turns in the same session.
- Open the latest GitHub Actions run and confirm the CI workflow is green.
