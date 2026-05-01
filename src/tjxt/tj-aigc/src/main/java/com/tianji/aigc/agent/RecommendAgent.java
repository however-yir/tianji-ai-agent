package com.tianji.aigc.agent;

import cn.hutool.core.map.MapUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.tools.CourseTools;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 推荐智能体
 */
@Slf4j
@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class RecommendAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;
    private final CourseTools courseTools;
    private final VectorStore vectorStore;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RECOMMEND;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getRecommendAgentSystemMessage().get();
    }

    @Override
    public Object[] tools() {
        return new Object[]{courseTools};
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        var userId = UserContext.getUser();
        return MapUtil.<String, Object>builder() // 设置tool列表
                .put(Constant.USER_ID, userId) // 设置用户id参数
                .put(Constant.REQUEST_ID, requestId) // 设置请求id参数
                .build();
    }

    @Override
    public List<Advisor> advisors(String question) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.65)
                .build();
        return List.of(new QuestionAnswerAdvisor(vectorStore, searchRequest));
    }
}
