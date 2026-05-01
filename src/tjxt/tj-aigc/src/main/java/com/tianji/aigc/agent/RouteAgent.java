package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 路由智能体
 */
@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class RouteAgent extends AbstractAgent {

    @Override
    protected boolean useAttachmentContext() {
        return false;
    }

    private final SystemPromptConfig systemPromptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getRouteAgentSystemMessage().get();
    }

    @Override
    public boolean useChatMemory() {
        return false;
    }
}
