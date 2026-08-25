package com.tianji.aigc.harness;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    private AgentHarnessService newHarnessService() {
        return new AgentHarnessService(
                new ActionPolicyGuard(),
                new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient),
                new HarnessEventRecorder()
        );
    }
}
