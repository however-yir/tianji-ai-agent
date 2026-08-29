package com.tianji.aigc.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
    void shouldClaimWithSetNxTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("tj:harness:idem:order.preview:10001:preview-r1"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertThat(store.tryAcquire("order.preview:10001:preview-r1", 5000)).isTrue();
        verify(valueOps).setIfAbsent(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void shouldReportDuplicateClaim() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(store.tryAcquire("k", 5000)).isFalse();
    }

    @Test
    void shouldPublishAndRoundTripObservationJson() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        AgentObservation observation = observation();
        store.complete("k1", observation, 60000);

        verify(valueOps).set(eq("tj:harness:idem:k1"), anyString(), eq(Duration.ofMillis(60000)));
        // feed the serialized value back as if read from Redis
        when(valueOps.get("tj:harness:idem:k1")).thenAnswer(invocation -> {
            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(valueOps).set(eq("tj:harness:idem:k1"), captor.capture(), any(Duration.class));
            return captor.getValue();
        });

        Optional<AgentObservation> restored = store.get("k1");
        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().actionType()).isEqualTo("order.preview");
        assertThat(restored.orElseThrow().traceId()).isEqualTo("request-r1");
        assertThat(restored.orElseThrow().status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldReturnEmptyWhenNoStoredResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("tj:harness:idem:k2")).thenReturn(null);

        assertThat(store.get("k2")).isEmpty();
    }

    @Test
    void shouldReleaseByDeletingKey() {
        store.release("k3");

        verify(redisTemplate).delete("tj:harness:idem:k3");
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
