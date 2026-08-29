package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.prompt.PromptRegistry;
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
    private final PromptRegistry promptRegistry;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    /**
     * Returns the route agent system message, instructing it to output structured JSON:
     * {
     *   "intent": "BUY",
     *   "confidence": 0.91,
     *   "reason": "用户明确表达购买课程并要求生成订单",
     *   "routeReason": "购买意图清晰，且只请求预下单确认",
     *   "nextAgent": "BUY",
     *   "candidateAgents": ["BUY", "CONSULT", "HUMAN_HANDOFF"],
     *   "needRag": true,
     *   "needMemory": true,
     *   "riskLevel": "LOW"
     * }
     */
    @Override
    public String systemMessage() {
        String configured = this.systemPromptConfig.getRouteAgentSystemMessage().get();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return this.promptRegistry.active("route").orElseThrow().content();
    }

    @Override
    public boolean useChatMemory() {
        return false;
    }
}
