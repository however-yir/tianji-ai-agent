package com.tianji.aigc.harness;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum ActionSchema {
    COURSE_QUERY("course.query", "CourseTools.queryCourseById", "读取课程基础信息", Set.of("courseId"), "courseInfo"),
    COURSE_SEARCH("course.search", "CourseTools.search", "课程搜索扩展位", Set.of("keyword"), "courseSearch"),
    ORDER_PREVIEW("order.preview", "OrderTools.prePlaceOrder", "生成订单预览，不支付不确认", Set.of("courseIds"), "prePlaceOrder"),
    KNOWLEDGEOPS_RAG_QUERY("knowledgeops.rag_query", "KnowledgeOpsClient.ragSearch", "KnowledgeOps RAG 检索", Set.of("query"), "knowledgeOpsRagQuery"),
    MCP_CALL("mcp.call", "McpRuntime.call", "MCP 工具调用扩展位", Set.of("server", "tool"), "mcpCall");

    private final String actionType;
    private final String defaultToolName;
    private final String description;
    private final Set<String> requiredInputs;
    private final String defaultResultField;

    ActionSchema(String actionType, String defaultToolName, String description,
                 Set<String> requiredInputs, String defaultResultField) {
        this.actionType = actionType;
        this.defaultToolName = defaultToolName;
        this.description = description;
        this.requiredInputs = requiredInputs;
        this.defaultResultField = defaultResultField;
    }

    public String getActionType() {
        return actionType;
    }

    public String getDefaultToolName() {
        return defaultToolName;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getRequiredInputs() {
        return requiredInputs;
    }

    public String resultField(AgentAction action) {
        if (this == COURSE_QUERY) {
            Object courseId = action.input().get("courseId");
            return "courseInfo_" + courseId;
        }
        return defaultResultField;
    }

    public static Optional<ActionSchema> resolve(String actionType) {
        return Arrays.stream(values())
                .filter(schema -> schema.actionType.equals(actionType))
                .findFirst();
    }
}
