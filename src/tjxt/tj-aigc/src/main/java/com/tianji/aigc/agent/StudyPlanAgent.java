package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class StudyPlanAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.STUDY_PLAN;
    }

    @Override
    public String systemMessage() {
        return """
                You are a study plan advisor for an online education platform.
                Based on the user's goals, current level, and available time,
                recommend a personalized learning path with courses and milestones.
                Be specific about course order, estimated time, and prerequisites.
                """;
    }

    @Override
    public boolean useChatMemory() {
        return true;
    }
}
