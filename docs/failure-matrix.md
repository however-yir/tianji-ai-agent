# Agent Failure Matrix

How the Tianji agent system behaves under each deterministic failure class.
Every row is reproducible: the fault-injection profile (`--spring.profiles.active=fault-injection`
+ `tj.ai.fault-injection.scenario=...`) simulates the downstream failure, and the matching
unit/acceptance test asserts the behavior. Nothing here depends on real LLM or real third-party
network state.

> Fault injection is strictly profile-scoped and **never enabled in production**.
> `NoopFaultInjector` is the default; the active injector only exists under the `fault-injection` profile.

| Failure | Detection | Agent behavior | User behavior | Metric | Test |
|---|---|---|---|---|---|
| RouteAgent model timeout / JSON parse error | `AgentServiceImpl` catches runtime failure around `routeAgent.process` | RouteSafety fallback → HUMAN_HANDOFF | User gets a human-handoff ROUTE + ticket id; SSE terminates with STOP | `agent.route.handoff` | `AgentServiceImplTest.shouldRoute*`, `RouteSafetyPolicyTest` |
| Course service timeout | `AgentRuntime.execute` throws → harness catches | FAILED Observation, sanitized TRACE event, no partial card | Graceful response, stream ends with STOP | `agent.runtime.failure` | `AgentHarnessServiceTest.shouldRepresentRuntimeTimeoutAsFailureObservation` |
| Order service timeout | `AgentRuntime.execute` throws → harness catches | FAILED Observation; idempotency claim released → user may retry same actionId | No payment, no order side effect; retry works | `agent.action.denied` / `agent.runtime.failure` | `AgentHarnessServiceTest.shouldAllowRetryAfterFailedPreview` |
| Idempotency store (Redis) unavailable | `IdempotencyStore.tryAcquire` throws | Harness fails closed: action rejected, runtime tool **never invoked** | User sees "幂等存储不可用，本次请求被拒绝" | `agent.runtime.failure` | `AgentHarnessServiceTest.shouldFailClosedWhenIdempotencyStoreIsUnavailable` |
| Downstream HTTP 500 | fault-injection scenario `http_500` | FAILED Observation, TRACE failure | Graceful failure, STOP emitted | `agent.runtime.failure` | `FaultInjectionFaultInjectorTest` |
| Malformed tool response | fault-injection scenario `malformed_tool_response` | FAILED Observation (exception → Observation conversion) | Graceful failure, STOP emitted | `agent.runtime.failure` | `FaultInjectionFaultInjectorTest` |
| Slow downstream response | fault-injection scenario `slow_response` | Action waits; SSE stays streaming | Progress + eventual STOP | `agent.runtime.latency` | `FaultInjectionFaultInjectorTest` |
| Duplicate callback / concurrent same actionId | `IdempotencyStore` atomic claim (SET NX / in-memory CAS) | Exactly one executor; duplicates get cached result or in-flight hint | Consistent preview result, no duplicate order preview | `agent.action.total` | `AgentHarnessServiceTest.shouldExecuteConcurrentPreviewOnlyOnce` |
| Cancellation + completion race | `SseEventContract` terminal guarantee | STOP emitted exactly once; late child events ignored after termination | No hanging stream | `sse.session.completed/failed` | `SseEventContractTest` |
| Prompt injection attempting bypass | `ActionPolicyGuard` deterministic deny | Action denied before runtime; user text never becomes a tool call | Denied reason surfaced in TRACE | `agent.action.denied` | `ActionPolicyGuardTest.shouldDenyUnknownAndPrivilegedActions...` |

## Reproducing

```bash
# Course timeout in acceptance/demo
SPRING_PROFILES_ACTIVE=fault-injection AI_FAULT_SCENARIO=course_timeout java -jar tj-aigc.jar
# Then ask the chat: "推荐 Java 课程" -> observe FAILURE TRACE + STOP
```
