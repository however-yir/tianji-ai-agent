package com.tianji.aigc.harness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Production idempotency store backed by Redis {@code SET NX EX} / {@code SET EX}.
 * The claim is atomic across instances, which in-memory duplicates cannot provide.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "tj:harness:idem:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean tryAcquire(String key, long ttlMillis) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey(key), "1", Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void complete(String key, AgentObservation observation, long ttlMillis) {
        try {
            redisTemplate.opsForValue()
                    .set(redisKey(key), objectMapper.writeValueAsString(observation), Duration.ofMillis(ttlMillis));
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化 AgentObservation: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AgentObservation> get(String key) {
        String stored = redisTemplate.opsForValue().get(redisKey(key));
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(stored, AgentObservation.class));
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法反序列化幂等结果: " + e.getMessage(), e);
        }
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(redisKey(key));
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
