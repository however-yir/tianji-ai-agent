package com.tianji.aigc.agent;

import com.tianji.aigc.prompt.PromptRegistry;

import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 投诉处理智能体 — minimal implementation with system prompt and empty tool binding.
 * Handles user complaints: collects details, validates concerns, escalates when needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev-demo")
public class ComplaintAgent extends AbstractAgent {

    private final PromptRegistry promptRegistry;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.COMPLAINT;
    }

    @Override
    public String systemMessage() {
        return this.promptRegistry.active("complaint").orElseThrow().content();
    }

    @Override
    public boolean useChatMemory() {
        return true;
    }
}
