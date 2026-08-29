# Dependency Baseline & Upgrade Decision Record

Reviewed 2026-08-29. The guiding criterion is **stable + maintained + compatible**,
not the largest version number. Any upgrade had to keep the whole tjxt (16 modules)
Spring Boot / Spring Cloud stack building and all 80+ agent tests green.

## Decision summary

| Dependency | Before | After | Rationale |
|---|---|---|---|
| Spring AI | 1.0.0-**M6** (milestone) | **1.0.9** (1.0.x GA line) | Milestone → GA; M6's paired Alibaba starter has no GA release below |
| Spring AI Alibaba | starter 1.0.0-M6.1 (milestone) | **removed** — DashScope via OpenAI-compatible endpoint | No stable `spring-ai-alibaba-starter` exists on Maven Central (only M2…M6.1); DashScope is OpenAI-compatible |
| Spring Boot | 3.3.5 | 3.3.5 (unchanged) | 1.0.9 GA line compiles and tests green on Boot 3.3.5; bumping to 3.4/3.5 would cascade Spring Cloud 2024/2025 + Alibaba 2024/2025 across all 16 modules |
| Spring Cloud / Alibaba | 2023.0.3 / 2023.0.3.2 | unchanged | Same cascade rationale |
| MyBatis Plus | 3.5.9 | unchanged | No breaking issue observed |
| starter artifacts | `spring-ai-openai-spring-boot-starter` / `spring-ai-elasticsearch-store-spring-boot-starter` | `spring-ai-starter-model-openai` / `spring-ai-starter-vector-store-elasticsearch` | GA renamed the starter artifact names |
| RAG advisor | `QuestionAnswerAdvisor` (M6) | `RetrievalAugmentationAdvisor` + `VectorStoreDocumentRetriever` (GA) | GA replaced the QnA advisor with the RAG pipeline advisor |
| ChatMemory | M6 `get(String, int)` | GA `get(String)` + kept windowed overload locally | GA simplified the interface |
| `AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY` | used directly | local constant `chat_memory_conversation_id` | GA removed the abstract advisor class |
| `Media` | `org.springframework.ai.model.Media` | `org.springframework.ai.content.Media` | GA package move |
| `MessageChatMemoryAdvisor` | constructor | `builder(ChatMemory).build()` | GA made it builder-only |

## Why a 1.0.9 GA line and not 1.1.x / 2.x

- **Spring AI 1.1.8 / 2.0.1 exist** on Maven Central, but Spring AI 2.x targets Spring Boot 4
  compatibility and full API migration; 1.1.x requires a Boot 3.4/3.5-family bump.
- The 16-module tjxt reactor pins Spring Cloud 2023.0.x (Boot 3.3). Moving Spring AI to a
  Boot 3.4+ line forces a coordinated upgrade of the whole microservice stack. We keep
  that as a tracked follow-up rather than a risky point upgrade.
- Spring AI Alibaba **has no stable starter** (Central version list is M2 → M6.1 only),
  so pairing 1.1.x with an official Alibaba integration is not possible; the stable
  `spring-ai-alibaba-bom` 1.1.2.3 exists, but its starter artifacts are not the ones the
  project used. DashScope is instead reached through `spring-ai-starter-model-openai`
  with `spring.ai.openai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1`,
  which is the community-standard, fully GA path.

## What was migrated in code (evidence)

- `AgentServiceImpl` / `AbstractAgent` / `ChatServiceImpl` / `MessageUtil` /
  `RedisChatMemory` / `MemoryMessageUtil` → GA APIs (see test suite:
  `AgentServiceImplTest`, `MessageUtilTest`, `RedisChatMemoryTest`).
- Removed the project-local `MyChatClientAutoConfiguration` fork: GA now ships the
  `ChatClient.Builder` autoconfiguration (`chatClientBuilder` bean) directly.
- `SpringAIConfig` now consumes the official `ChatClient.Builder` bean.
- Verified by: `mvn -pl tj-aigc -am install` + `mvn test` (80 tests, 0 failures),
  offline evaluator (160 cases, 98.75%), acceptance suite, and the CI runs on main.

## Environment variables worth knowing after the change

| Variable | Meaning |
|---|---|
| `AIGC_DASHSCOPE_BASE_URL` | DashScope OpenAI-compatible endpoint base url |
| `AIGC_DASHSCOPE_API_KEY` | DashScope API key (same key as before) |
| `AIGC_OPENAI_CHAT_ENABLED` | master switch for the OpenAI-compatible chat |
