package com.tianji.aigc.harness;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Single-JVM idempotency slots used by unit tests and the default local profile.
 * The {@link RedisIdempotencyStore} is the production implementation.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final class Slot {
        private boolean acquired;
        private long acquiredExpireAt;
        private AgentObservation completed;
        private long completedExpireAt;

        synchronized boolean tryAcquire(long now, long ttlMillis) {
            if (completed != null && completedExpireAt > now) {
                return false;
            }
            if (acquired && acquiredExpireAt > now) {
                return false;
            }
            acquired = true;
            acquiredExpireAt = now + ttlMillis;
            return true;
        }

        synchronized void complete(AgentObservation observation, long now, long ttlMillis) {
            acquired = false;
            completed = observation;
            completedExpireAt = now + ttlMillis;
        }

        synchronized Optional<AgentObservation> get(long now) {
            if (completed == null || completedExpireAt <= now) {
                return Optional.empty();
            }
            return Optional.of(completed);
        }

        synchronized void release() {
            acquired = false;
        }
    }

    private final ConcurrentMap<String, Slot> slots = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long ttlMillis) {
        return slots.computeIfAbsent(key, k -> new Slot())
                .tryAcquire(System.currentTimeMillis(), ttlMillis);
    }

    @Override
    public void complete(String key, AgentObservation observation, long ttlMillis) {
        slots.computeIfAbsent(key, k -> new Slot())
                .complete(observation, System.currentTimeMillis(), ttlMillis);
    }

    @Override
    public Optional<AgentObservation> get(String key) {
        Slot slot = slots.get(key);
        return slot == null ? Optional.empty() : slot.get(System.currentTimeMillis());
    }

    @Override
    public void release(String key) {
        Slot slot = slots.get(key);
        if (slot != null) {
            slot.release();
        }
    }
}
