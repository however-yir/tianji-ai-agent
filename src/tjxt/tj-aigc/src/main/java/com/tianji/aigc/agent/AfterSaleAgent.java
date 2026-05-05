package com.tianji.aigc.agent;

import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 售后客服智能体 — minimal implementation with system prompt and empty tool binding.
 * Handles post-purchase support: refund inquiries, order issues, service complaints.
 */
@Slf4j
@Component
@Profile("!dev-demo")
public class AfterSaleAgent extends AbstractAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.AFTER_SALE;
    }

    @Override
    public String systemMessage() {
        return """
                你是一个在线教育平台的售后客服助手。
                你负责处理用户的售后问题，包括退款查询、订单异常、课程质量反馈和售后服务咨询。
                回答时请：
                1. 先确认用户的订单信息
                2. 根据问题类型给出对应的处理流程
                3. 如果需要人工介入，建议转接人工客服
                """;
    }

    @Override
    public boolean useChatMemory() {
        return true;
    }
}
