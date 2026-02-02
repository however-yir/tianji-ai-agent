package com.tianji.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements ChatService {

    private final ChatClient openAiChatClient;
    private final SystemPromptConfig systemPromptConfig;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 先通过意图分析智能体，进行分析
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        String result = routeAgent.process(question, sessionId);
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.agentNameOf(result);

        //根据枚举类型查询bean
        Agent agent = this.findAgentByType(agentTypeEnum);
        if (null == agent) {
            // 找不到对应的智能体，直接返回结果
            ChatEventVO chatEventVO = ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(result)
                    .build();
            return Flux.just(chatEventVO, AbstractAgent.STOP_EVENT);
        }

        return agent.processStream(question, sessionId);
    }

    private Agent findAgentByType(AgentTypeEnum agentTypeEnum) {
        // 根据智能体类型，在Spring容器中查找对应的bean
        if (agentTypeEnum == null) {
            return null;
        }

        Map<String, Agent> agentMap = SpringUtil.getBeansOfType(Agent.class);
        for (Agent agent : agentMap.values()) {
            if (agent.getAgentType() == agentTypeEnum) {
                return agent;
            }
        }
        return null;
    }

    @Override
    public void stop(String sessionId) {
        this.findAgentByType(AgentTypeEnum.ROUTE).stop(sessionId);
    }

    @Override
    public String chatText(String question) {
        return this.openAiChatClient.prompt()
                .system(this.systemPromptConfig.getTextSystemMessage().get())
                .user(question)
                .call()
                .content();
    }
}
