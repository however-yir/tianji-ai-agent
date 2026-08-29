package com.tianji.aigc.agent;

import com.tianji.aigc.prompt.PromptRegistry;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.tools.CourseTools;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 学习规划智能体 — recommends personalized learning paths with course tool binding.
 * Uses CourseTools to look up real course data and provide structured study plans.
 */
@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class StudyPlanAgent extends AbstractAgent {

    private final PromptRegistry promptRegistry;

    private final SystemPromptConfig systemPromptConfig;
    private final CourseTools courseTools;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.STUDY_PLAN;
    }

    @Override
    public String systemMessage() {
        return this.promptRegistry.active("study-plan").orElseThrow().content();
    }

    @Override
    public Object[] tools() {
        return new Object[]{courseTools};
    }

    @Override
    public boolean useChatMemory() {
        return true;
    }
}
