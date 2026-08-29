# Data Retention Design Controls

| Data | Storage | TTL | User content? | Deletion behavior |
| --- | --- | --- | --- | --- |
| Chat memory (Redis) | Redis list per session | 30 days rolling (configurable `tj.ai.memory.ttl-days`) | Yes (redacted) | `DELETE /session/{id}` clears session + memory; trim to `max-turns` on write |
| Run metadata | In-memory repository | 30 days (lazy expiry) | No metadata only; actions store type/status/latency/hash | Retention expiry; not deleted by session delete |
| Feedback | FeedbackService (in-memory, upsert per message) | session lifetime | Optional redacted comment | Not auto-deleted (supports review) |
| Attachments | InMemoryAttachmentService | expire cycle | Yes (OCR-derived) | Expired on TTL; cleared source text not reintroduced |
| Trace | Event stream (not persisted) | n/a | Redacted failure reasons | n/a |
| Evaluation fixtures | git tracked | permanent | Synthetic cases | reviewed by humans |

These are **design controls**, not compliance claims (no GDPR/Cyber-law certification).

**Isolation:** session memory keys are per-conversation; budget and run state are
request-scoped; metrics never use user/session/trace ids as labels.

**Feedback loop:** negative feedback becomes a *candidate case* in
`artifacts/feedback-candidates.json` (formatted by `scripts/agentops.py candidates`), then
requires **human review** before any evaluation dataset change.
