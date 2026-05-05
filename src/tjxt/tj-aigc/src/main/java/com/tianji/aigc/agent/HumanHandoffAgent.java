package com.tianji.aigc.agent;

import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 转人工智能体 — collects user info, summarizes issues, and confirms human follow-up.
 * No tool binding needed — the handoff is a conversational flow.
 * When the user asks for human support, this agent:
 * 1. Collects contact information (name, phone/email)
 * 2. Summarizes the issue based on conversation context
 * 3. Confirms that a human agent will follow up within a stated SLA
 */
@Slf4j
@Component
@Profile("!dev-demo")
public class HumanHandoffAgent extends AbstractAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.HUMAN_HANDOFF;
    }

    @Override
    public String systemMessage() {
        return """
                你是一个在线教育平台的转人工客服助手。
                当用户需要人工支持时，你需要：
                1. 收集用户的联系方式（姓名、手机号或邮箱）
                2. 基于对话上下文，简要总结用户遇到的问题
                3. 告知用户人工客服将在24小时内跟进
                4. 保持礼貌和耐心，让用户感受到被重视
                注意：不要编造解决方案，只负责收集信息和确认转接。
                """;
    }

    @Override
    public boolean useChatMemory() {
        // Handoff conversations should reference the prior context but not persist
        // beyond the handoff point
        return false;
    }

    @Override
    protected boolean useAttachmentContext() {
        return false;
    }
}
