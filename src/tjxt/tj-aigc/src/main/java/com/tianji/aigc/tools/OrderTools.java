package com.tianji.aigc.tools;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.convert.Convert;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.harness.AgentAction;
import com.tianji.aigc.harness.AgentHarnessService;
import com.tianji.aigc.tools.result.PrePlaceOrder;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class OrderTools {

    private static final String DEFAULT_AGENT = "BUY";

    private final AgentHarnessService agentHarnessService;

    @Tool(description = Constant.Tools.PRE_PLACE_ORDER)
    public PrePlaceOrder prePlaceOrder(@ToolParam(description = Constant.ToolParams.COURSE_IDS) List<Number> ids,
                                       ToolContext toolContext) {
        // 设置用户ID，用于身份验证，否在在Feign调用时会出现401错误
        UserContext.setUser(Convert.toLong(toolContext.getContext().get(Constant.USER_ID)));
        // 大模型传入的ids，可能是int类型，所以转化为long类型，再调用Feign
        List<Long> courseIds = CollStreamUtil.toList(ids, Number::longValue);
        AgentAction action = new AgentAction(
                "order.preview",
                resolveAgentName(toolContext, DEFAULT_AGENT),
                "OrderTools.prePlaceOrder",
                resolveString(toolContext, Constant.REQUEST_ID),
                resolveString(toolContext, Constant.SESSION_ID),
                Convert.toLong(toolContext.getContext().get(Constant.USER_ID)),
                Map.of("courseIds", courseIds)
        );
        return agentHarnessService.execute(action).resultAs(PrePlaceOrder.class);
    }

    private String resolveString(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String resolveAgentName(ToolContext toolContext, String fallback) {
        String agentName = resolveString(toolContext, Constant.AGENT_NAME);
        return agentName == null || agentName.isBlank() ? fallback : agentName;
    }

}
