package com.tianji.aigc.agent;

import com.tianji.aigc.prompt.PromptRegistry;

import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Profile("!dev-demo")
public class HumanHandoffAgent extends AbstractAgent {

    private final PromptRegistry promptRegistry;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.HUMAN_HANDOFF;
    }

    @Override
    public String systemMessage() {
        return this.promptRegistry.active("handoff").orElseThrow().content();
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
