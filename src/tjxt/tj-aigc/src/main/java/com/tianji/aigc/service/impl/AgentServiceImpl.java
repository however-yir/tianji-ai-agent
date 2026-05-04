package com.tianji.aigc.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        // Step 1: RouteAgent intent analysis
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        String rawResult = routeAgent.process(question, sessionId);

        // Step 2: Parse structured JSON routing result
        RouteResult route = parseRouteResult(rawResult);
        AgentTypeEnum agentTypeEnum = route != null
                ? AgentTypeEnum.agentNameOf(route.nextAgent())
                : AgentTypeEnum.agentNameOf(rawResult);

        log.info("[Agent路由] sessionId={}, 目标Agent={}, 耗时={}ms",
                sessionId, agentTypeEnum, System.currentTimeMillis() - startTime);

        // Step 3: Emit ROUTE event
        ChatEventVO routeEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.ROUTE.getValue())
                .eventData(route != null ? route : Map.of(
                        "intent", rawResult,
                        "nextAgent", rawResult,
                        "confidence", 0.5))
                .build();

        // Step 4: Route to target agent
        Agent agent = this.findAgentByType(agentTypeEnum);
        if (null == agent) {
            log.warn("[Agent路由] 未找到Agent类型={}, 回退到直接回复, sessionId={}", agentTypeEnum, sessionId);
            return Flux.just(routeEvent, AbstractAgent.STOP_EVENT);
        }

        return Flux.concat(
                Flux.just(routeEvent),
                agent.processStream(question, sessionId)
        );
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

    private Agent findAgentByType(AgentTypeEnum agentTypeEnum) {
        if (agentTypeEnum == null) return null;
        return this.agentRegistry.get(agentTypeEnum);
    }

    private RouteResult parseRouteResult(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            String json = raw.trim();
            if (json.startsWith("{")) {
                Map<String, Object> parsed = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                return new RouteResult(
                        String.valueOf(parsed.getOrDefault("intent", raw)),
                        parseDouble(parsed.get("confidence"), 0.5),
                        String.valueOf(parsed.getOrDefault("reason", "")),
                        String.valueOf(parsed.getOrDefault("nextAgent", raw)),
                        Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("needRag", "false"))),
                        Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("needMemory", "false"))),
                        String.valueOf(parsed.getOrDefault("riskLevel", "LOW"))
                );
            }
        } catch (Exception e) {
            log.debug("Route result is not structured JSON, using raw text: {}", raw);
        }
        return new RouteResult(raw, 0.5, "plain text routing", raw, false, false, "LOW");
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record RouteResult(String intent, double confidence, String reason,
                               String nextAgent, boolean needRag,
                               boolean needMemory, String riskLevel) {}
}
