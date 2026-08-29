package com.tianji.aigc.route;

import com.tianji.aigc.enums.AgentTypeEnum;
import org.springframework.util.StringUtils;

/**
 * Deterministic post-routing boundary. It does not classify a user's business intent;
 * it only upgrades unsafe, uncertain or privileged requests to human handoff.
 */
public final class RouteSafetyPolicy {

    public static final double HANDOFF_CONFIDENCE_THRESHOLD = 0.65;

    private RouteSafetyPolicy() {
    }

    public static String handoffReason(String question, String proposedAgent, double confidence) {
        String normalized = question == null ? "" : question.toLowerCase();
        if (confidence < HANDOFF_CONFIDENCE_THRESHOLD) {
            return "路由置信度低于人工兜底阈值";
        }
        if (AgentTypeEnum.COMPLAINT.getAgentName().equals(proposedAgent) || containsAny(normalized,
                "投诉", "差评", "举报", "维权", "欺诈", "被骗", "骗人", "315")) {
            return "投诉或维权类问题需要人工介入";
        }
        if (containsAny(normalized,
                "支付失败", "付款失败", "已扣款", "扣款", "重复扣费", "银行卡", "信用卡",
                "支付密码", "验证码", "转账", "不到账", "退款到账", "payment.execute",
                "order.pay", "forcepurchase", "bypass agentharness", "ignore all previous instructions",
                "绕过订单确认", "其他用户订单", "删除订单", "修改价格")) {
            return "支付、越权或提示注入风险需要人工确认";
        }
        if (containsAny(normalized, "人工", "真人", "客服介入")) {
            return "用户明确要求人工客服";
        }
        return "";
    }

    public static boolean requiresHumanHandoff(String question, String proposedAgent, double confidence) {
        return StringUtils.hasText(handoffReason(question, proposedAgent, confidence));
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
