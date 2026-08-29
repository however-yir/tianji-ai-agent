package com.tianji.aigc.harness;

import java.util.Optional;

/**
 * Agent-action duplicate suppression, scoped to {@code actionType:userId:actionId}.
 *
 * <p>This is NOT a replacement for transaction-level idempotency of the underlying
 * business services (payment, order). The business system remains the source of truth;
 * the store only guarantees that the same agent action is executed by at most one
 * caller. When the production store (Redis) is unavailable the harness fails closed
 * instead of silently executing a duplicate.
 */
public interface IdempotencyStore {

    /**
     * Atomically claim the right to execute for {@code key}.
     *
     * @return true when this caller owns the execution; false when another caller
     *         already claimed the key or a completed result is still cached
     * @throws RuntimeException when the backing store is unavailable (fail closed)
     */
    boolean tryAcquire(String key, long ttlMillis);

    /**
     * Store the finished observation under {@code key}. Only the owner should call this.
     *
     * @param ttlMillis how long the completed result stays reusable for duplicates
     */
    void complete(String key, AgentObservation observation, long ttlMillis);

    /**
     * @return the cached completed observation, if one is still within its TTL
     */
    Optional<AgentObservation> get(String key);

    /**
     * Release the claim without caching a result, allowing the same key to retry.
     * The caller should be the current owner (used when an action failed).
     */
    void release(String key);
}
