package com.tianji.aigc.agent;

import cn.hutool.core.map.MapUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.tools.OrderTools;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class BuyAgent extends AbstractAgent {

    private static final List<String> STATE_MACHINE = List.of(
            "COURSE_SELECTED",
            "ORDER_PREVIEW",
            "USER_CONFIRM_REQUIRED",
            "HANDOFF_OR_DONE"
    );

    private final SystemPromptConfig systemPromptConfig;
    private final OrderTools orderTools;

    @Override
    public String systemMessage() {
        String configured = this.systemPromptConfig.getBuyAgentSystemMessage().get();
        String stateMachinePrompt = """

                购买链路必须按明确状态机执行：
                COURSE_SELECTED -> ORDER_PREVIEW -> USER_CONFIRM_REQUIRED -> HANDOFF_OR_DONE
                - COURSE_SELECTED：确认用户已给出课程 ID 或明确课程选择。
                - ORDER_PREVIEW：只调用预下单工具生成确认信息，不直接支付。
                - USER_CONFIRM_REQUIRED：要求用户确认订单金额、优惠和课程信息。
                - HANDOFF_OR_DONE：支付异常、扣款、验证码、银行卡等敏感问题转人工；用户确认后提示继续走正式支付链路。
                """;
        return (configured == null ? "" : configured) + stateMachinePrompt;
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.BUY;
    }

    @Override
    public Object[] tools() {
        return new Object[]{orderTools};
    }

    public List<String> stateMachine() {
        return STATE_MACHINE;
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        var userId = UserContext.getUser();
        return MapUtil.<String, Object>builder() // 设置tool列表
                .put(Constant.USER_ID, userId) // 设置用户id参数
                .put(Constant.REQUEST_ID, requestId) // 设置请求id参数
                .build();
    }
}
