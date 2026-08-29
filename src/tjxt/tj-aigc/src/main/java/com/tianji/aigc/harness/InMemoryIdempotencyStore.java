package com.tianji.aigc.harness;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Single-JVM idempotency slots used by unit tests and the default local profile.
 * The {@link RedisIdempotencyStore} is the production implementation.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final class Slot {
        private String ownerToken;
        private long acquiredExpireAt;
        private AgentObservation completed;
        private long completedExpireAt;

        synchronized String tryAcquire(String token, long now, long ttlMillis) {
            if (completed != null && completedExpireAt > now) {
                return null;
            }
            if (ownerToken != null && acquiredExpireAt > now) {
                return null;
            }
            ownerToken = token;
            acquiredExpireAt = now + ttlMillis;
            return token;
        }

        synchronized void complete(AgentObservation observation, String token, long now, long ttlMillis) {
            if (token != null && token.equals(ownerToken)) {
                ownerToken = null;
                completed = observation;
                completedExpireAt = now + ttlMillis;
            }
        }

        synchronized Optional<AgentObservation> get(long now) {
            if (completed == null || completedExpireAt <= now) {
                return Optional.empty();
            }
            return Optional.of(completed);
        }

        synchronized void release(String token) {
            if (token != null && token.equals(ownerToken)) {
                ownerToken = null;
            }
        }
    }

    private final ConcurrentMap<String, Slot> slots = new ConcurrentHashMap<>();

    @Override
    public String tryAcquire(String key, long ttlMillis) {
        return slots.computeIfAbsent(key, k -> new Slot())
                .tryAcquire(UUID.randomUUID().toString(), System.currentTimeMillis(), ttlMillis);
    }

    @Override
    public void complete(String key, AgentObservation observation, long ttlMillis, String ownerToken) {
        Slot slot = slots.get(key);
        if (slot != null) {
            slot.complete(observation, ownerToken, System.currentTimeMillis(), ttlMillis);
        }
    }

    @Override
    public Optional<AgentObservation> get(String key) {
        Slot slot = slots.get(key);
        return slot == null ? Optional.empty() : slot.get(System.currentTimeMillis());
    }

    @Override
    public void release(String key, String ownerToken) {
        Slot slot = slots.get(key);
        if (slot != null) {
            slot.release(ownerToken);
        }
    }
}
