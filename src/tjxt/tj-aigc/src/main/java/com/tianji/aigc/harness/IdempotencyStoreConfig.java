package com.tianji.aigc.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Defaults to the single-JVM store (safe for local demo and tests).
 * Enable the distributed store with {@code tj.ai.harness.idempotency.store=redis}.
 */
@Configuration
public class IdempotencyStoreConfig {

    public static final String REDIS_STORE_PROPERTY = "tj.ai.harness.idempotency.store";

    @Bean
    @ConditionalOnProperty(name = REDIS_STORE_PROPERTY, havingValue = "redis")
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }
}
