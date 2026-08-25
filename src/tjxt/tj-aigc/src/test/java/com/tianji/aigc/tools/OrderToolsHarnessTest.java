package com.tianji.aigc.tools;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.harness.ActionPolicyGuard;
import com.tianji.aigc.harness.AgentHarnessService;
import com.tianji.aigc.harness.AgentRuntime;
import com.tianji.aigc.harness.HarnessEventRecorder;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.tools.result.PrePlaceOrder;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.api.dto.trade.OrderConfirmVO;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderToolsHarnessTest {

    @Mock
    private CourseClient courseClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private KnowledgeOpsClient knowledgeOpsClient;

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("request-1");
        ToolResultHolder.remove("request-empty");
        UserContext.removeUser();
    }

    @Test
    void shouldPrePlaceOrderThroughHarnessAndStoreOrderParams() {
        OrderConfirmVO confirmVO = OrderConfirmVO.builder()
                .orderId(202604290001L)
                .totalAmount(19900)
                .discounts(List.of(new CouponDiscountDTO()
                        .setIds(List.of(9001L))
                        .setRules(List.of("新人立减 20 元"))
                        .setDiscountAmount(2000)))
                .courses(List.of(new OrderCourseDTO()
                        .setId(1589905661084430337L)
                        .setPrice(19900)))
                .build();
        when(tradeClient.prePlaceOrder(List.of(1589905661084430337L))).thenReturn(confirmVO);

        OrderTools tools = new OrderTools(newHarnessService());
        PrePlaceOrder result = tools.prePlaceOrder(
                List.of(1589905661084430337L),
                new ToolContext(Map.of(
                        Constant.USER_ID, 10001L,
                        Constant.REQUEST_ID, "request-1",
                        Constant.SESSION_ID, "session-1",
                        Constant.AGENT_NAME, "BUY"
                ))
        );

        assertThat(result.getOrderId()).isEqualTo(202604290001L);
        assertThat(result.getTotalAmount()).isEqualTo(199.0);
        assertThat(result.getDiscountAmount()).isEqualTo(20.0);
        assertThat(result.getPayAmount()).isEqualTo(179.0);
        assertThat(result.getCourseIds()).containsExactly(1589905661084430337L);
        assertThat(result.getBusinessState()).isEqualTo("USER_CONFIRM_REQUIRED");
        assertThat(result.getStateTrace()).containsExactly("COURSE_SELECTED", "ORDER_PREVIEW", "USER_CONFIRM_REQUIRED");
        assertThat(result.getNextAction()).contains("HANDOFF_OR_DONE");
        assertThat(ToolResultHolder.get("request-1", "prePlaceOrder")).isEqualTo(result);
        assertThat((List<?>) ToolResultHolder.get("request-1", HarnessEventRecorder.TRACE_FIELD))
                .singleElement()
                .satisfies(trace -> {
                    Map<?, ?> traceMap = (Map<?, ?>) trace;
                    assertThat(traceMap.get("agentName")).isEqualTo("BUY");
                    assertThat(traceMap.get("actionType")).isEqualTo("order.preview");
                    assertThat(traceMap.get("success")).isEqualTo(true);
                });
        // 工具执行结束后必须清理 ThreadLocal，避免线程复用导致跨用户身份污染
        assertThat(UserContext.getUser()).isNull();
        verify(tradeClient).prePlaceOrder(List.of(1589905661084430337L));
    }

    @Test
    void shouldNotThrowWhenToolContextIsNull() {
        OrderTools tools = new OrderTools(newHarnessService());

        PrePlaceOrder result = tools.prePlaceOrder(
                List.of(1589905661084430337L),
                null
        );

        assertThat(result).isNull();
        // 防御性：toolContext 为 null 时不应触发 Feign 调用，也不应泄漏 UserContext
        verifyNoInteractions(tradeClient);
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void shouldNotInvokeFeignWhenCourseIdsIsEmpty() {
        OrderTools tools = new OrderTools(newHarnessService());

        PrePlaceOrder result = tools.prePlaceOrder(
                List.of(),
                new ToolContext(Map.of(
                        Constant.USER_ID, 10001L,
                        Constant.REQUEST_ID, "request-empty",
                        Constant.SESSION_ID, "session-empty"
                ))
        );

        assertThat(result).isNull();
        verifyNoInteractions(tradeClient);
        assertThat(UserContext.getUser()).isNull();
    }

    private AgentHarnessService newHarnessService() {
        return new AgentHarnessService(
                new ActionPolicyGuard(),
                new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient),
                new HarnessEventRecorder()
        );
    }
}
