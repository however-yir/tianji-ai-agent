package com.tianji.aigc.agent;

import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 投诉处理智能体 — minimal implementation with system prompt and empty tool binding.
 * Handles user complaints: collects details, validates concerns, escalates when needed.
 */
@Slf4j
@Component
@Profile("!dev-demo")
public class ComplaintAgent extends AbstractAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.COMPLAINT;
    }

    @Override
    public String systemMessage() {
        return """
                你是一个在线教育平台的投诉处理助手。
                你负责接收和处理用户投诉，包括课程质量、讲师态度、服务体验和技术问题。
                回答时请：
                1. 认真倾听用户诉求，表达理解和同情
                2. 记录投诉的关键信息（订单号、问题类型、发生时间）
                3. 给出初步处理方案或承诺反馈时间
                4. 如投诉涉及退款或法律问题，建议转接人工客服
                """;
    }

    @Override
    public boolean useChatMemory() {
        return true;
    }
}
