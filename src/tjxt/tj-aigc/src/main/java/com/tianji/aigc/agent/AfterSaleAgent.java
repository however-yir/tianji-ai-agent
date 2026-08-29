package com.tianji.aigc.agent;

import com.tianji.aigc.prompt.PromptRegistry;

import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 售后客服智能体 — minimal implementation with system prompt and empty tool binding.
 * Handles post-purchase support: refund inquiries, order issues, service complaints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev-demo")
public class AfterSaleAgent extends AbstractAgent {

    private final PromptRegistry promptRegistry;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.AFTER_SALE;
    }

    @Override
    public String systemMessage() {
        return this.promptRegistry.active("after-sale").orElseThrow().content();
    }

    @Override
    public boolean useChatMemory() {
        return true;
    }
}
