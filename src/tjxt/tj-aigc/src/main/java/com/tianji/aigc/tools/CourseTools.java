package com.tianji.aigc.tools;

import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.harness.AgentAction;
import com.tianji.aigc.harness.AgentHarnessService;
import com.tianji.aigc.tools.result.CourseInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class CourseTools {

    private static final String DEFAULT_AGENT = "COURSE_AGENT";

    private final AgentHarnessService agentHarnessService;

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryCourseById(@ToolParam(description = Constant.ToolParams.COURSE_ID) Long courseId,
                                      ToolContext toolContext
    ) {
        if (courseId == null) {
            return null;
        }
        AgentAction action = new AgentAction(
                "course.query",
                resolveAgentName(toolContext, DEFAULT_AGENT),
                "CourseTools.queryCourseById",
                resolveString(toolContext, Constant.REQUEST_ID),
                resolveString(toolContext, Constant.SESSION_ID),
                resolveLong(toolContext, Constant.USER_ID),
                Map.of("courseId", courseId)
        );
        return agentHarnessService.execute(action).resultAs(CourseInfo.class);
    }

    private String resolveString(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long resolveLong(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveAgentName(ToolContext toolContext, String fallback) {
        String agentName = resolveString(toolContext, Constant.AGENT_NAME);
        return agentName == null || agentName.isBlank() ? fallback : agentName;
    }

}
