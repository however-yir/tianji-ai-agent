package com.tianji.aigc.agent;

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

    private final SystemPromptConfig systemPromptConfig;
    private final CourseTools courseTools;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.STUDY_PLAN;
    }

    @Override
    public String systemMessage() {
        return """
                你是一个在线教育平台的学习规划助手。
                根据用户的学习目标、当前水平和可用时间，制定个性化的学习路径。
                回答时请：
                1. 明确用户的学习目标和现有基础
                2. 推荐匹配的课程，给出学习顺序和预估时长
                3. 列出前置知识和关键里程碑
                4. 给出可执行的学习时间表建议
                推荐课程时，使用课程查询工具获取真实课程数据。
                """;
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
