package com.tianji.aigc.harness;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    private AgentObservation observation(String key) {
        AgentAction action = new AgentAction(
                "order.preview", "BUY", "OrderTools.prePlaceOrder",
                key, key, "session-1", 10001L, java.util.Map.of("courseIds", java.util.List.of(1L)));
        return AgentObservation.of(action, ActionPolicyDecision.allow("ok"), true,
                "prePlaceOrder", java.util.Map.of("orderId", "O-1"), null, 3L);
    }

    @Test
    void shouldClaimExactlyOnceAndCacheTheCompletedResult() {
        String owner = store.tryAcquire("k1", 5000);
        assertThat(owner).isNotNull();
        assertThat(store.tryAcquire("k1", 5000)).isNull();

        store.complete("k1", observation("k1"), 5000, owner);

        assertThat(store.get("k1")).isPresent();
        assertThat(store.get("k1").orElseThrow().success()).isTrue();
        // completed slot blocks a duplicate claim while the result is cached
        assertThat(store.tryAcquire("k1", 5000)).isNull();
    }

    @Test
    void shouldLetOnlyOneConcurrentCallerAcquire() throws Exception {
        int callers = 16;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (store.tryAcquire("concurrent-key", 5000) != null) {
                            winners.incrementAndGet();
                        }
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            pool.shutdownNow();
        }
        assertThat(winners.get()).isEqualTo(1);
    }

    @Test
    void shouldTreatExpiredClaimAsReacquirable() throws Exception {
        assertThat(store.tryAcquire("expiring", 30)).isNotNull();
        Thread.sleep(50);

        assertThat(store.tryAcquire("expiring", 5000)).isNotNull();
    }

    @Test
    void shouldAllowRetryAfterRelease() {
        String owner = store.tryAcquire("retryable", 5000);
        assertThat(owner).isNotNull();
        store.release("retryable", owner);

        assertThat(store.tryAcquire("retryable", 5000)).isNotNull();
        assertThat(store.get("retryable")).isEmpty();
    }

    @Test
    void differentKeysDoNotCollide() {
        assertThat(store.tryAcquire("user-1:preview", 5000)).isNotNull();
        assertThat(store.tryAcquire("user-2:preview", 5000)).isNotNull();
        assertThat(store.tryAcquire("user-1:search", 5000)).isNotNull();
    }
    @Test
    void shouldNotCompleteOrReleaseWithForeignToken() {
        String owner = store.tryAcquire("protected", 5000);
        assertThat(owner).isNotNull();

        // foreign owner cannot complete (result stays absent) ...
        store.complete("protected", observation("protected"), 5000, "foreign-token");
        assertThat(store.get("protected")).isEmpty();

        // ... nor release the claim of the real owner
        store.release("protected", "foreign-token");
        assertThat(store.tryAcquire("protected", 5000)).isNull();
    }
}
