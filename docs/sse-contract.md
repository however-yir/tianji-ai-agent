# SSE Business Contract

`POST /chat` is a server-sent event stream represented by `ChatEventVO`.
The contract is intentionally business-oriented: model text is not the only output;
route decisions, governed actions and card parameters are first-class events.

## Event Types

| Code | Event | Payload | Consumer responsibility |
| ---: | --- | --- | --- |
| 1001 | `DATA` | non-empty string chunk | append assistant text |
| 1002 | `STOP` | optional | end loading state; exactly one per request |
| 1003 | `PARAM` | object | render course/order business cards |
| 1004 | `ROUTE` | route object | render routing decision and confidence |
| 1005 | `TRACE` | trace object or list | render governed tool execution |
| 1006 | `EVIDENCE` | object or list | render citations |
| 1007 | `MEMORY` | object or list | render memory hit summary |

## Envelope

Every event is serialized as:

```json
{ "eventType": 1004, "eventData": { ... }, "version": 1 }
```

- `version` defaults to `1` and is informative: clients MUST tolerate unknown fields and
  MUST NOT fail on additional keys. A future structural change will bump the version and
  document the migration here.
- Unknown `eventType` values are dropped by the client validator; malformed payloads are
  rejected and the stream still terminates with `STOP`.

## Ordering And Failure Rules

The minimum valid sequence is:

```text
ROUTE -> (DATA | TRACE | PARAM | EVIDENCE | MEMORY)* -> STOP
```

- `STOP` is terminal. Events after it are ignored by the server contract.
- A malformed event is dropped rather than passed to the UI.
- A child-Agent stream failure becomes a sanitized `TRACE` failure record followed by `STOP`.
- The browser treats a stream that closes without `STOP` as a contract failure instead of leaving a message in a permanent loading state.
- `TRACE` carries correlation fields (`sessionId`, `requestId`, `traceId`) and action state, but does not include user credentials, raw prompts, or full tool payloads.

The executable evidence is `SseEventContractTest` on the backend and
`web/chat-ui/src/api/client.test.ts` on the frontend.
