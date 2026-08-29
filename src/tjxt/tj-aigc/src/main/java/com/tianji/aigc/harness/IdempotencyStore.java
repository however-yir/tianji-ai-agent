package com.tianji.aigc.harness;

import java.util.Optional;

/**
 * Agent-action duplicate suppression, scoped to {@code actionType:userId:actionId}.
 *
 * <p>Claims are owner-token based: {@link #tryAcquire} hands out a per-claim token that
 * must be presented to {@link #complete} / {@link #release}, so a stale executor can never
 * overwrite another owner's result. The claim TTL covers the full run budget.
 *
 * <p>This is NOT a replacement for transaction-level idempotency of the underlying
 * business services. The business system remains the source of truth. When the production
 * store (Redis) is unavailable the harness fails closed instead of silently duplicating.
 */
public interface IdempotencyStore {

    /**
     * Atomically claim the right to execute for {@code key}.
     *
     * @return the owner token when this caller owns the execution; {@code null} when
     *         another caller already claimed the key or a completed result is cached
     * @throws RuntimeException when the backing store is unavailable (fail closed)
     */
    String tryAcquire(String key, long ttlMillis);

    /**
     * Store the finished observation under {@code key} only when the caller still owns the
     * claim (compare-and-swap). No-op otherwise.
     */
    void complete(String key, AgentObservation observation, long ttlMillis, String ownerToken);

    /**
     * @return the cached completed observation, if one is still within its TTL
     */
    Optional<AgentObservation> get(String key);

    /**
     * Release the claim without caching a result, allowing the same key to retry.
     * Only effective when {@code ownerToken} matches the current claim owner.
     */
    void release(String key, String ownerToken);
}
