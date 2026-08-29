# Provenance & Originality

This repository combines a **upstream microservice baseline** with **original AI Agent
engineering work**. The split matters for review credibility and is stated honestly here.

## Upstream baseline (not original here)

The `src/tjxt/**` modules — `tj-common`, `tj-auth`, `tj-api`, `tj-gateway`, `tj-user`,
`tj-message`, `tj-media`, `tj-course`, `tj-search`, `tj-learning`, `tj-pay`, `tj-trade`,
`tj-exam`, `tj-promotion`, `tj-data`, `tj-remark` — are the Tianji (天机) education-platform
microservices that were used as the business scaffolding. They carry their own TODOs and
upstream idioms (MyBatis-Plus, Nacos, Knife4j, Tencent COS storage) and were not written in
this project. The `src/my-spring-ai*` and `src/openai-java-demo` directories are learning
references, not runtime components.

## Original AI Agent work (the contribution)

Everything under `src/tjxt/tj-aigc` that implements the Agent layer — plus the evaluation,
acceptance and AgentOps tooling — is original work of this repository:

| Area | Evidence |
| --- | --- |
| Intent routing + safety fallback | `RouteAgent`, `RouteSafetyPolicy`, `AgentServiceImpl` |
| Deterministic governance | `AgentHarnessService`, `ActionPolicyGuard`, `AgentAction`, `AgentObservation`, `AgentRuntime` |
| Execution budget & loop detection | `ExecutionBudgetService`, `BudgetState`, `ReasonCode` |
| SSE business contract | `SseEventContract`, `ChatEventVO` + backend/frontend contract tests |
| Versioned prompt governance | `prompts/*/v<n>.md`, `PromptRegistry`, `PromptResolution` |
| Run evidence & replay | `AgentRunRecord`, `RunRecorder`, `InMemoryAgentRunRepository`, `tests/golden-runs/`, `scripts/replay_agent_run.py` |
| Model runtime governance | `ModelProfile*` (deterministic profiles), token capture, `CostEstimator` |
| Memory governance | `RedisChatMemory` bounds/TTL/redaction, session deletion |
| Feedback loop | `Feedback*` + frontend thumbs + review-candidate export |
| Fault injection & failure matrix | `FaultInjectionFaultInjector`, `docs/failure-matrix.md` |
| Evaluation & release evidence | 160-case dataset, evaluator, manifest generator, acceptance suite |
| Observability | `AgentMetrics` (low-cardinality), dashboard spec |

The `tj-aigc` module *uses* upstream `tj-api`/`tj-course`/`tj-trade` contracts as its
business backend; the Agent orchestration, governance and evaluation layers are this
project's own.

## Third-party components

Standard OSS (Spring Boot/Cloud/AI 1.0.9, React, Mermaid, DOMPurify, Redis, MySQL,
Thymeleaf tooling...). See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) and the CI
SBOM output (`ghcr.io` images ship SBOM + provenance via build-push-action).

## License

Repository LICENSE (MIT) applies to the whole repository; upstream modules retain their
original authorship and license terms.
