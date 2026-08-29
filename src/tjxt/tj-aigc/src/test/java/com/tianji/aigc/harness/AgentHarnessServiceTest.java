package com.tianji.aigc.harness;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.observability.AgentMetrics;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.api.dto.trade.OrderConfirmVO;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentHarnessServiceTest {

    @Mock
    private CourseClient courseClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private KnowledgeOpsClient knowledgeOpsClient;

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("request-1");
        ToolResultHolder.remove("request-denied");
        ToolResultHolder.remove("request-fail");
        ToolResultHolder.remove("request-preview");
        ToolResultHolder.remove("request-timeout");
    }

    @Test
    void shouldExecuteAllowedActionAndRecordObservation() {
        CourseBaseInfoDTO dto = new CourseBaseInfoDTO();
        dto.setId(1589905661084430337L);
        dto.setName("Java 后端工程师体系课");
        dto.setPrice(19900);
        when(courseClient.baseInfo(1589905661084430337L, true)).thenReturn(dto);
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "course.query",
                "CONSULT",
                "CourseTools.queryCourseById",
                "request-1",
                "session-1",
                10001L,
                Map.of("courseId", 1589905661084430337L)
        );

        AgentObservation observation = service.execute(action);

        assertThat(observation.allowed()).isTrue();
        assertThat(observation.success()).isTrue();
        assertThat(observation.resultField()).isEqualTo("courseInfo_1589905661084430337");
        assertThat(observation.resultAs(CourseInfo.class).getName()).isEqualTo("Java 后端工程师体系课");
        assertThat(ToolResultHolder.get("request-1", "courseInfo_1589905661084430337")).isEqualTo(observation.result());
        assertThat((List<?>) ToolResultHolder.get("request-1", HarnessEventRecorder.TRACE_FIELD))
                .singleElement()
                .satisfies(trace -> assertThat(((Map<?, ?>) trace).get("actionType")).isEqualTo("course.query"));
        verify(courseClient).baseInfo(1589905661084430337L, true);
    }

    @Test
    void shouldRecordDeniedObservationWithoutCallingRuntime() {
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "order.pay",
                "BUY",
                "OrderTools.pay",
                "request-denied",
                "session-1",
                10001L,
                Map.of("courseIds", List.of(1L))
        );

        AgentObservation observation = service.execute(action);

        assertThat(observation.allowed()).isFalse();
        assertThat(observation.success()).isFalse();
        assertThat(observation.errorMessage()).contains("只允许 order.preview");
        assertThat(ToolResultHolder.get("request-denied", "prePlaceOrder")).isNull();
        assertThat((List<?>) ToolResultHolder.get("request-denied", HarnessEventRecorder.TRACE_FIELD))
                .singleElement()
                .satisfies(trace -> assertThat(((Map<?, ?>) trace).get("success")).isEqualTo(false));
        verifyNoInteractions(tradeClient);
    }

    @Test
    void shouldCaptureRuntimeExceptionAsFailedObservation() {
        // 模拟 courseClient.baseInfo 抛 FeignException 这种网络异常。
        // 之前会把异常吞掉但不打日志；现在要求至少能转成失败 observation 并把异常类名透出。
        when(courseClient.baseInfo(1589905661084430337L, true))
                .thenThrow(new RuntimeException("connection refused"));
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "course.query",
                "CONSULT",
                "CourseTools.queryCourseById",
                "request-fail",
                "session-1",
                10001L,
                Map.of("courseId", 1589905661084430337L)
        );

        AgentObservation observation = service.execute(action);

        assertThat(observation.allowed()).isTrue();
        assertThat(observation.success()).isFalse();
        assertThat(observation.errorMessage()).contains("connection refused");
        assertThat(ToolResultHolder.get("request-fail", "courseInfo_1589905661084430337")).isNull();
        assertThat((List<?>) ToolResultHolder.get("request-fail", HarnessEventRecorder.TRACE_FIELD))
                .singleElement()
                .satisfies(trace -> {
                    Map<?, ?> traceMap = (Map<?, ?>) trace;
                    assertThat(traceMap.get("success")).isEqualTo(false);
                    assertThat(((String) traceMap.get("errorMessage"))).contains("connection refused");
                });
    }

    @Test
    void shouldMakeDuplicateOrderPreviewIdempotent() {
        when(tradeClient.prePlaceOrder(List.of(1L))).thenReturn(OrderConfirmVO.builder()
                .orderId(42L)
                .totalAmount(0)
                .courses(List.of())
                .discounts(List.of())
                .build());
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "order.preview",
                "BUY",
                "OrderTools.prePlaceOrder",
                "request-preview",
                "preview-42",
                "session-1",
                10001L,
                Map.of("courseIds", List.of(1L))
        );

        AgentObservation first = service.execute(action);
        AgentObservation duplicate = service.execute(action);

        assertThat(first.success()).isTrue();
        assertThat(duplicate).isSameAs(first);
        verify(tradeClient).prePlaceOrder(List.of(1L));
    }

    @Test
    void shouldExecuteConcurrentPreviewOnlyOnce() throws InterruptedException {
        OrderConfirmVO preview = OrderConfirmVO.builder()
                .orderId(77L).totalAmount(0).courses(List.of()).discounts(List.of()).build();
        when(tradeClient.prePlaceOrder(List.of(1L))).thenAnswer(invocation -> {
            Thread.sleep(150L);
            return preview;
        });
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "order.preview", "BUY", "OrderTools.prePlaceOrder",
                "request-concurrent-preview", "preview-concurrent", "session-1", 10001L,
                Map.of("courseIds", List.of(1L)));

        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<AgentObservation> results = new CopyOnWriteArrayList<>();
        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(service.execute(action));
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        verify(tradeClient, times(1)).prePlaceOrder(List.of(1L));
        assertThat(results).hasSize(callers);
        // exactly one executor succeeded; the duplicates got a deterministic in-flight hint
        assertThat(results).anyMatch(AgentObservation::success);
        assertThat(results).allSatisfy(observation ->
                assertThat(observation.success() || observation.errorMessage().contains("正在处理中")).isTrue());
    }

    @Test
    void shouldAllowRetryAfterFailedPreview() {
        OrderConfirmVO preview = OrderConfirmVO.builder()
                .orderId(88L).totalAmount(0).courses(List.of()).discounts(List.of()).build();
        when(tradeClient.prePlaceOrder(List.of(1L)))
                .thenThrow(new RuntimeException("order timeout"))
                .thenReturn(preview);
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "order.preview", "BUY", "OrderTools.prePlaceOrder",
                "request-retry-preview", "preview-retry", "session-1", 10001L,
                Map.of("courseIds", List.of(1L)));

        AgentObservation failed = service.execute(action);
        AgentObservation retried = service.execute(action);

        assertThat(failed.status()).isEqualTo("FAILURE");
        assertThat(retried.success()).isTrue();
        verify(tradeClient, times(2)).prePlaceOrder(List.of(1L));
    }

    @Test
    void shouldFailClosedWhenIdempotencyStoreIsUnavailable() {
        IdempotencyStore broken = new IdempotencyStore() {
            @Override
            public boolean tryAcquire(String key, long ttlMillis) {
                throw new IllegalStateException("redis connection refused");
            }

            @Override
            public void complete(String key, AgentObservation observation, long ttlMillis) {
            }

            @Override
            public java.util.Optional<AgentObservation> get(String key) {
                return java.util.Optional.empty();
            }

            @Override
            public void release(String key) {
            }
        };
        AgentHarnessService service = new AgentHarnessService(
                new ActionPolicyGuard(), new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient),
                new HarnessEventRecorder(), AgentMetrics.noop(), broken);
        AgentAction action = new AgentAction(
                "order.preview", "BUY", "OrderTools.prePlaceOrder",
                "request-store-down", "preview-store-down", "session-1", 10001L,
                Map.of("courseIds", List.of(1L)));

        AgentObservation observation = service.execute(action);

        assertThat(observation.status()).isEqualTo("FAILURE");
        assertThat(observation.errorMessage()).contains("幂等存储不可用");
        verifyNoInteractions(tradeClient);
    }

    @Test
    void shouldRepresentRuntimeTimeoutAsFailureObservation() {
        when(courseClient.baseInfo(1L, true)).thenThrow(new RuntimeException("read timed out"));
        AgentHarnessService service = newHarnessService();
        AgentAction action = new AgentAction(
                "course.query",
                "CONSULT",
                "CourseTools.queryCourseById",
                "request-timeout",
                "session-1",
                10001L,
                Map.of("courseId", 1L)
        );

        AgentObservation observation = service.execute(action);

        assertThat(observation.status()).isEqualTo("FAILURE");
        assertThat(observation.errorMessage()).contains("timed out");
        assertThat(observation.toTraceMap())
                .containsEntry("traceId", "request-timeout")
                .containsEntry("status", "FAILURE");
    }

    private AgentHarnessService newHarnessService() {
        return new AgentHarnessService(
                new ActionPolicyGuard(),
                new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient),
                new HarnessEventRecorder()
        );
    }
}
