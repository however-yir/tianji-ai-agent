package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class HumanHandoffAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.HUMAN_HANDOFF;
    }

    @Override
    public String systemMessage() {
        return """
                You are a customer service handoff agent. When the user needs human support,
                collect their contact information (name, phone/email), summarize their issue,
                and confirm that a human agent will follow up. Be polite and reassuring.
                """;
    }

    @Override
    public boolean useChatMemory() {
        return false;
    }
}
