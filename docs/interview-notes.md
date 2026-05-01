# Interview Notes

## One-Minute Pitch

`tianji-ai-agent` is a course-business Agent case built around a real interaction loop: a user asks for course advice, the system routes intent, calls course or order tools, streams the answer, and returns structured course/order cards to the frontend.

## What It Proves

- Multi-agent routing is used for a business flow, not only a chat demo.
- Tool Calling connects the model layer with course and trade service contracts.
- SSE events carry both text tokens and structured `PARAM` payloads for UI cards.
- The frontend has visible product evidence: sessions, attachments, stop generation, voice entry, course cards, and order cards.
- The demo profile lowers setup cost by avoiding required production login and model credentials for the first run.

## Best Technical Story

The strongest story is the split between `RouteAgent` and the domain agents. `RouteAgent` keeps intent classification narrow, while `RecommendAgent`, `ConsultAgent`, `BuyAgent`, and `KnowledgeAgent` own their own tools and prompts. This makes the business path explainable: route first, execute with scoped tools, then stream both language and structured UI data.

## Tradeoffs To Explain

- The demo keeps some attachment and session behavior in memory to make local startup reliable.
- Spring AI versions are pinned around the M6/M7 era for compatibility with the existing course-service sample code; upgrading should be handled as a planned compatibility task.
- The project is intentionally centered on one domain flow instead of trying to be a general Agent framework.

## Validation Path

```bash
bash scripts/quick-start-mac.sh
cd web/chat-ui && npm ci && npm run lint && npm run test:run && npm run build
cd ../../src/tjxt/tj-aigc && mvn test
```

## Follow-Up Ideas

- Add a small intent-routing evaluation dataset for fixed course questions.
- Export an OpenAPI/SSE event contract for frontend-backend integration.
- Add a compatibility note for the Spring AI upgrade path.
