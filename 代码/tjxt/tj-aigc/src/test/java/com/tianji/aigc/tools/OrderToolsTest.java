package com.tianji.aigc.tools;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.PrePlaceOrder;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderToolsTest {

    @Mock
    private TradeClient tradeClient;

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("request-1");
        UserContext.removeUser();
    }

    @Test
    void shouldPrePlaceOrderAndStoreOrderParams() {
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

        OrderTools tools = new OrderTools(tradeClient);
        PrePlaceOrder result = tools.prePlaceOrder(
                List.of(1589905661084430337L),
                new ToolContext(Map.of(Constant.USER_ID, 10001L, Constant.REQUEST_ID, "request-1"))
        );

        assertThat(result.getOrderId()).isEqualTo(202604290001L);
        assertThat(result.getTotalAmount()).isEqualTo(199.0);
        assertThat(result.getDiscountAmount()).isEqualTo(20.0);
        assertThat(result.getPayAmount()).isEqualTo(179.0);
        assertThat(result.getCourseIds()).containsExactly(1589905661084430337L);
        assertThat(ToolResultHolder.get("request-1", "prePlaceOrder")).isEqualTo(result);
        assertThat(UserContext.getUser()).isEqualTo(10001L);
        verify(tradeClient).prePlaceOrder(List.of(1589905661084430337L));
    }
}
