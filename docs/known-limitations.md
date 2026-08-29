# Known Limitations (Final RC)

Honest list of what this project is **not**. These are design boundaries, not hidden issues.

- **Not validated under real large-scale production traffic** — metrics, load profiles and
  incident runs come from hermetic/local verification, not a live production fleet.
- **External contact-center/ticketing is not integrated** — the handoff contract stores
  REQUESTED records (adapter-ready); ACKNOWLEDGED/RESOLVED transitions await a real adapter.
- **Live LLM evaluation is non-deterministic and manual** — never part of blocking CI; it
  needs provider credentials and is not comparable to the offline baseline.
- **Final transaction exactly-once belongs to the downstream business service** — the Agent
  idempotency layer suppresses duplicate *agent actions*; it intentionally does not replace
  payment/order transaction semantics (crash-between-side-effect-and-complete is a
  documented boundary).
- **Single OpenAI-compatible provider today** — provider failover is documented as future
  work (`docs/agentops.md`); there is no automatic multi-provider routing.
- **Run record and feedback storage are in-memory** — interfaces are stable
  (`AgentRunRepository`, `FeedbackRepository`); persistence is a deployment choice.
- **Session ownership guard is enforced when an authenticated user context is present** —
  demo modes without auth keep open behavior by design (dev-demo uses a fixed demo user).
- **Prompt overrides via Nacos are possible but versioned only at runtime** — the run record
  stores prompt id/version/checksum of the effective text plus an `overridden` flag;
  Nacos content itself is not stored in git.
