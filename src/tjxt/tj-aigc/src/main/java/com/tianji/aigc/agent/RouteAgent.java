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

    /**
     * Returns the route agent system message, instructing it to output structured JSON:
     * {
     *   "intent": "BUY",
     *   "confidence": 0.91,
     *   "reason": "用户明确表达购买课程并要求生成订单",
     *   "nextAgent": "BUY",
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
        // Fallback structured routing prompt when Nacos config is unavailable
        return """
                You are an intent routing agent for an education customer service system.
                Analyze the user's question and route to the most appropriate agent.

                Available agents: RECOMMEND, CONSULT, BUY, KNOWLEDGE, AFTER_SALE, COMPLAINT, STUDY_PLAN, HUMAN_HANDOFF

                Return JSON only:
                {
                  "intent": "BUY",
                  "confidence": 0.91,
                  "reason": "brief explanation of routing decision",
                  "nextAgent": "BUY",
                  "needRag": true,
                  "needMemory": true,
                  "riskLevel": "LOW"
                }
                """;
    }

    @Override
    public boolean useChatMemory() {
        return false;
    }
}
