package com.tianji.aigc.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RedisIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    private AgentObservation observation() {
        AgentAction action = new AgentAction(
                "order.preview", "BUY", "OrderTools.prePlaceOrder",
                "request-r1", "preview-r1", "session-r1", 10001L, Map.of("courseIds", List.of(1L)));
        return AgentObservation.of(action, ActionPolicyDecision.allow("ok"), true,
                "prePlaceOrder", Map.of("orderId", "O-42"), null, 5L);
    }

    @Test
    void shouldClaimWithSetNxTtlAndReturnOwnerToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("tj:harness:idem:k1"), anyString(), any(Duration.class)))
                .thenReturn(true);

        String token = store.tryAcquire("k1", 5000);

        assertThat(token).isNotNull();
        verify(valueOps).setIfAbsent(eq("tj:harness:idem:k1"), eq(token), eq(Duration.ofMillis(5000)));
    }

    @Test
    void shouldReportDuplicateClaimAsNullToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(store.tryAcquire("k", 5000)).isNull();
    }

    @Test
    void shouldCompleteAndReleaseAsCompareAndSwap() {
        store.complete("k1", observation(), 60000, "owner-1");
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("tj:harness:idem:k1")),
                org.mockito.ArgumentMatchers.<List<Object>>argThat(args ->
                        args.toString().contains("owner-1") && args.toString().contains("order.preview")));

        store.release("k3", "owner-2");
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("tj:harness:idem:k3")),
                org.mockito.ArgumentMatchers.<List<Object>>argThat(args -> args.toString().contains("owner-2")));
    }

    @Test
    void shouldSerializeObservationForCasWrite() {
        AgentObservation observation = observation();
        store.complete("k1", observation, 60000, "owner-r1");

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("tj:harness:idem:k1")),
                org.mockito.ArgumentMatchers.<List<Object>>argThat(args -> {
                    // ARGV: [ownerToken, serializedObservation, ttlMillis]
                    assertThat(args.get(0)).isEqualTo("owner-r1");
                    assertThat(String.valueOf(args.get(1))).contains("order.preview");
                    return true;
                }));
    }

    @Test
    void shouldReturnEmptyWhenNoStoredResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("tj:harness:idem:k2")).thenReturn(null);

        assertThat(store.get("k2")).isEmpty();
    }

    @Test
    void shouldPropagateUnavailableStoreAsRuntimeFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> store.tryAcquire("k4", 5000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection refused");
    }
}
