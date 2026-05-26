package com.tianji.aigc.harness;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Component
public class ActionPolicyGuard {

    private static final Set<String> BLOCKED_ORDER_KEYS = Set.of(
            "pay", "payment", "paid", "confirm", "confirmed",
            "autoConfirm", "auto_confirm", "confirmPurchase", "purchaseConfirmed"
    );

    public ActionPolicyDecision decide(AgentAction action) {
        if (action == null || !StringUtils.hasText(action.actionType())) {
            return ActionPolicyDecision.deny("actionType 不能为空");
        }
        ActionSchema schema = ActionSchema.resolve(action.actionType()).orElse(null);
        if (schema == null) {
            if (action.actionType().startsWith("order.")) {
                return ActionPolicyDecision.deny("订单动作只允许 order.preview；禁止真实支付和自动确认购买");
            }
            return ActionPolicyDecision.deny("未知动作 schema: " + action.actionType());
        }
        return switch (schema) {
            case COURSE_QUERY -> requirePositiveNumber(action, "courseId", "允许查询课程基础信息");
            case COURSE_SEARCH -> requireText(action, "keyword", "允许课程搜索扩展动作");
            case ORDER_PREVIEW -> decideOrderPreview(action);
            case KNOWLEDGEOPS_RAG_QUERY -> requireText(action, "query", "允许 KnowledgeOps RAG 查询");
            case MCP_CALL -> ActionPolicyDecision.deny("mcp.call 仅作为未来 MCP Runtime 扩展位");
        };
    }

    private ActionPolicyDecision decideOrderPreview(AgentAction action) {
        Map<String, Object> input = action.input();
        for (String key : BLOCKED_ORDER_KEYS) {
            if (input.containsKey(key)) {
                return ActionPolicyDecision.deny("order.preview 只允许预下单；禁止真实支付和自动确认购买");
            }
        }
        Object courseIds = input.get("courseIds");
        if (!(courseIds instanceof Collection<?> collection) || collection.isEmpty()) {
            return ActionPolicyDecision.deny("order.preview 需要 courseIds");
        }
        boolean hasInvalidId = collection.stream().anyMatch(id -> !(id instanceof Number number) || number.longValue() <= 0);
        if (hasInvalidId) {
            return ActionPolicyDecision.deny("order.preview 的 courseIds 必须是正数");
        }
        return ActionPolicyDecision.allow("允许订单预览；不执行支付、不自动确认购买");
    }

    private ActionPolicyDecision requirePositiveNumber(AgentAction action, String key, String reason) {
        Object value = action.input().get(key);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            return ActionPolicyDecision.deny(action.actionType() + " 需要正数参数 " + key);
        }
        return ActionPolicyDecision.allow(reason);
    }

    private ActionPolicyDecision requireText(AgentAction action, String key, String reason) {
        Object value = action.input().get(key);
        if (!StringUtils.hasText(value == null ? null : String.valueOf(value))) {
            return ActionPolicyDecision.deny(action.actionType() + " 需要文本参数 " + key);
        }
        return ActionPolicyDecision.allow(reason);
    }
}
