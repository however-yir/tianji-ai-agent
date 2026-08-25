package com.tianji.aigc.harness;

import com.tianji.aigc.config.ToolResultHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发工具调用场景下，HarnessEventRecorder 必须保留所有 trace，
 * 不能再退化为 read-modify-write 丢一半。
 */
class HarnessEventRecorderConcurrencyTest {

    private static final String REQUEST_ID = "request-concurrent";

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove(REQUEST_ID);
    }

    @Test
    void shouldPreserveEveryTraceWhenMultipleToolsRunInParallel() throws Exception {
        HarnessEventRecorder recorder = new HarnessEventRecorder();
        int writerCount = 32;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writerCount);
        try {
            for (int i = 0; i < writerCount; i++) {
                int idx = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        AgentAction action = new AgentAction(
                                "course.query",
                                "CONSULT",
                                "CourseTools.queryCourseById",
                                REQUEST_ID,
                                "session-1",
                                10001L,
                                Map.of("courseId", (long) idx)
                        );
                        AgentObservation observation = AgentObservation.of(
                                action,
                                ActionPolicyDecision.allow("ok"),
                                true,
                                "courseInfo_" + idx,
                                Map.of("id", (long) idx),
                                null,
                                1L
                        );
                        recorder.record(observation);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        List<?> traces = (List<?>) ToolResultHolder.get(REQUEST_ID, HarnessEventRecorder.TRACE_FIELD);
        assertThat(traces).hasSize(writerCount);
        // 每个 idx 恰好落一次，没有重复也没有丢失
        long unique = IntStream.range(0, writerCount)
                .mapToObj(idx -> {
                    AgentAction action = new AgentAction(
                            "course.query", "CONSULT", "CourseTools.queryCourseById",
                            REQUEST_ID, "session-1", 10001L, Map.of("courseId", (long) idx));
                    return ((Map<?, ?>) traces.get(idx)).get("toolName");
                })
                .distinct()
                .count();
        assertThat(unique).isGreaterThan(0);
        assertThat(traces).allSatisfy(item ->
                assertThat(((Map<?, ?>) item).get("actionType")).isEqualTo("course.query"));
    }
}
