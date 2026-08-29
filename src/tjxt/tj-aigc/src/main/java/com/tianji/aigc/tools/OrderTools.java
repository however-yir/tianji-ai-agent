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
        Map<String, Object> contextMap = toolContext == null || toolContext.getContext() == null
                ? Map.of() : toolContext.getContext();
        if (ids == null || ids.isEmpty()) {
            // Policy guard would reject this anyway; short-circuit to avoid the
            // runtime exception bubbling up as a generic tool failure.
            return null;
        }
        // 没有 toolContext 说明 Spring AI 框架没有把 agent/session/user 上下文注入进来，
        // 直接调用 harness 反而会带着错误的 requestId/userId 命中业务接口。
        // 这里只清理 UserContext 即可，调用方会收到 null 结果（模型会自然降级）。
        if (toolContext == null || toolContext.getContext() == null) {
            UserContext.removeUser();
            return null;
        }
        // 设置用户ID，用于身份验证，否在在Feign调用时会出现401错误
        UserContext.setUser(Convert.toLong(contextMap.get(Constant.USER_ID)));
        try {
            // 大模型传入的ids，可能是int类型，所以转化为long类型，再调用Feign
            List<Long> courseIds = CollStreamUtil.toList(ids, Number::longValue);
            AgentAction action = new AgentAction(
                    "order.preview",
                    resolveAgentName(toolContext, DEFAULT_AGENT),
                    "OrderTools.prePlaceOrder",
                    resolveString(toolContext, Constant.REQUEST_ID),
                    resolveString(toolContext, Constant.SESSION_ID),
                    Convert.toLong(contextMap.get(Constant.USER_ID)),
                    Map.of("courseIds", courseIds)
            );
            return agentHarnessService.execute(action).resultAs(PrePlaceOrder.class);
        }
        finally {
            // 工具在流式/线程池线程上执行，必须清理 ThreadLocal，否则线程复用后会把当前用户身份泄漏给下一个请求
            UserContext.removeUser();
        }
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
