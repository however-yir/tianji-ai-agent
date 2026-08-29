package com.tianji.aigc.harness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production idempotency store backed by Redis. The claim is a per-owner token written
 * with {@code SET NX EX}; completion and release are compare-and-swap via Lua, so a stale
 * executor can never overwrite or release another owner's claim.
 *
 * <p>When Redis is unavailable the harness fails closed.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "tj:harness:idem:";

    /** Compare-and-swap: set the value only when the stored token matches the owner. */
    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]) return 1 "
                    + "else return 0 end",
            Long.class);

    /** Compare-and-delete: delete only when the stored token matches the owner. */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tryAcquire(String key, long ttlMillis) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey(key), token, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    @Override
    public void complete(String key, AgentObservation observation, long ttlMillis, String ownerToken) {
        try {
            String value = objectMapper.writeValueAsString(observation);
            redisTemplate.execute(COMPARE_AND_SET,
                    List.of(redisKey(key)),
                    List.of(ownerToken, value, String.valueOf(ttlMillis)));
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
    public void release(String key, String ownerToken) {
        redisTemplate.execute(COMPARE_AND_DELETE, List.of(redisKey(key)), List.of(ownerToken));
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
