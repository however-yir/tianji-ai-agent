package com.tianji.aigc.service.impl;

import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.attachment.AttachmentContextHolder;
import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("!dev-demo")
public class AgentServiceImpl implements ChatService {

    private final ChatClient openAiChatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final Map<AgentTypeEnum, Agent> agentRegistry;

    public AgentServiceImpl(ChatClient openAiChatClient,
                            SystemPromptConfig systemPromptConfig,
                            List<Agent> agents) {
        this.openAiChatClient = openAiChatClient;
        this.systemPromptConfig = systemPromptConfig;
        this.agentRegistry = agents.stream()
                .collect(Collectors.toUnmodifiableMap(Agent::getAgentType, Function.identity()));
    }

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        long startTime = System.currentTimeMillis();
        // 先通过意图分析智能体，进行分析
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        String result = routeAgent.process(question, sessionId);
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.agentNameOf(result);
        log.info("[Agent路由] sessionId={}, 目标Agent={}, 耗时={}ms",
                sessionId, agentTypeEnum, System.currentTimeMillis() - startTime);

        //根据枚举类型查询bean
        Agent agent = this.findAgentByType(agentTypeEnum);
        if (null == agent) {
            log.warn("[Agent路由] 未找到Agent类型={}, 回退到直接回复, sessionId={}", agentTypeEnum, sessionId);
            // 找不到对应的智能体，直接返回结果
            ChatEventVO chatEventVO = ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(result)
                    .build();
            AttachmentContext context = AttachmentContextHolder.take(sessionId);
            if (context != null && context.hasSources()) {
                ChatEventVO paramEvent = ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.PARAM.getValue())
                        .eventData(context.toParamMap())
                        .build();
                return Flux.just(chatEventVO, paramEvent, AbstractAgent.STOP_EVENT);
            }
            return Flux.just(chatEventVO, AbstractAgent.STOP_EVENT);
        }

        return agent.processStream(question, sessionId);
    }

    private Agent findAgentByType(AgentTypeEnum agentTypeEnum) {
        if (agentTypeEnum == null) {
            return null;
        }
        return this.agentRegistry.get(agentTypeEnum);
    }

    @Override
    public void stop(String sessionId) {
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        if (routeAgent != null) {
            routeAgent.stop(sessionId);
        }
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
