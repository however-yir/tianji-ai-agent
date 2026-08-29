package com.tianji.aigc.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.observability.AgentMetrics;
import com.tianji.aigc.route.RouteSafetyPolicy;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.sse.SseEventContract;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("!dev-demo")
public class AgentServiceImpl implements ChatService {

    private final ChatClient openAiChatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final Map<AgentTypeEnum, Agent> agentRegistry;
    private final AgentMetrics agentMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> DEFAULT_CANDIDATE_AGENTS = List.of(
            AgentTypeEnum.RECOMMEND.getAgentName(),
            AgentTypeEnum.CONSULT.getAgentName(),
            AgentTypeEnum.BUY.getAgentName(),
            AgentTypeEnum.KNOWLEDGE.getAgentName(),
            AgentTypeEnum.AFTER_SALE.getAgentName(),
            AgentTypeEnum.COMPLAINT.getAgentName(),
            AgentTypeEnum.STUDY_PLAN.getAgentName(),
            AgentTypeEnum.HUMAN_HANDOFF.getAgentName()
    );

    public AgentServiceImpl(ChatClient openAiChatClient,
                            SystemPromptConfig systemPromptConfig,
                            List<Agent> agents) {
        this(openAiChatClient, systemPromptConfig, agents, AgentMetrics.noop());
    }

    @Autowired
    public AgentServiceImpl(ChatClient openAiChatClient,
                            SystemPromptConfig systemPromptConfig,
                            List<Agent> agents,
                            AgentMetrics agentMetrics) {
        this.openAiChatClient = openAiChatClient;
        this.systemPromptConfig = systemPromptConfig;
        this.agentMetrics = agentMetrics;
        this.agentRegistry = agents.stream()
                .collect(Collectors.toUnmodifiableMap(Agent::getAgentType, Function.identity()));
    }

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        long startTime = System.currentTimeMillis();
        // Step 1: RouteAgent intent analysis
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        RouteResult route;
        try {
            String rawResult = routeAgent.process(question, sessionId);
            route = applyHumanHandoffPolicy(question, parseRouteResult(rawResult));
        }
        catch (RuntimeException routeFailure) {
            // RouteAgent itself blew up (model timeout, Nacos config missing, JSON parse error...).
            // Don't fail the whole SSE — degrade to HUMAN_HANDOFF so the user still gets a response.
            log.error("[Agent路由] RouteAgent执行异常，回退到人工兜底, sessionId={}, error={}",
                    sessionId, routeFailure.getMessage(), routeFailure);
            route = new RouteResult(
                    AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                    0.0,
                    "RouteAgent执行异常：" + routeFailure.getMessage(),
                    "RouteAgent failed, falling back to human handoff",
                    AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                    false,
                    false,
                    "HIGH",
                    List.of(AgentTypeEnum.HUMAN_HANDOFF.getAgentName())
            );
        }

        AgentTypeEnum agentTypeEnum = AgentTypeEnum.agentNameOf(route.nextAgent());
        agentMetrics.recordRoute(route.nextAgent());
        log.info("[Agent路由] sessionId={}, 目标Agent={}, 耗时={}ms",
                sessionId, agentTypeEnum, System.currentTimeMillis() - startTime);

        // Step 3: Emit ROUTE event
        ChatEventVO routeEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.ROUTE.getValue())
                .eventData(route)
                .build();

        // Step 4: Route to target agent
        Agent agent = this.findAgentByType(agentTypeEnum);
        if (null == agent) {
            // nextAgent 未知时回退到人工兜底 Agent，保证用户一定能拿到回答，而不是只有路由事件+STOP
            log.warn("[Agent路由] 未找到Agent类型={}, 回退到人工兜底Agent, sessionId={}", agentTypeEnum, sessionId);
            agent = this.findAgentByType(AgentTypeEnum.HUMAN_HANDOFF);
        }
        if (null == agent) {
            return completeSseStream(routeEvent, Flux.empty(), sessionId);
        }

        try {
            return completeSseStream(routeEvent, agent.processStream(question, sessionId), sessionId);
        }
        catch (RuntimeException streamFailure) {
            log.warn("[Agent路由] 子Agent启动流失败，sessionId={}, agent={}",
                    sessionId, agentTypeEnum, streamFailure);
            return completeSseStream(routeEvent, Flux.error(streamFailure), sessionId);
        }
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

    private Flux<ChatEventVO> completeSseStream(ChatEventVO routeEvent, Flux<ChatEventVO> childStream,
                                                 String sessionId) {
        AtomicBoolean failed = new AtomicBoolean(false);
        return SseEventContract.terminate(routeEvent, childStream, sessionId)
                .doOnNext(event -> {
                    if (event.getEventType() == ChatEventTypeEnum.TRACE.getValue()
                            && event.getEventData() instanceof Map<?, ?> trace
                            && "FAILURE".equals(trace.get("status"))) {
                        failed.set(true);
                    }
                })
                .doOnComplete(() -> agentMetrics.recordSseSession(failed.get()));
    }

    private RouteResult parseRouteResult(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new RouteResult(
                    AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                    0.0,
                    "empty routing result",
                    "RouteAgent returned empty result",
                    AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                    false,
                    false,
                    "HIGH",
                    List.of(AgentTypeEnum.HUMAN_HANDOFF.getAgentName())
            );
        }
        try {
            String json = raw.trim();
            if (json.startsWith("{")) {
                Map<String, Object> parsed = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                String reason = String.valueOf(parsed.getOrDefault("reason",
                        parsed.getOrDefault("routeReason", "")));
                String routeReason = String.valueOf(parsed.getOrDefault("routeReason", reason));
                // nextAgent 缺失时不能回退整段原文（必然解析不出 Agent），改回人工兜底 Agent
                Object nextAgentValue = parsed.get("nextAgent");
                String nextAgent = nextAgentValue != null && StringUtils.hasText(String.valueOf(nextAgentValue))
                        ? String.valueOf(nextAgentValue)
                        : AgentTypeEnum.HUMAN_HANDOFF.getAgentName();
                return new RouteResult(
                        String.valueOf(parsed.getOrDefault("intent", nextAgent)),
                        parseDouble(parsed.get("confidence"), 0.5),
                        reason,
                        routeReason,
                        nextAgent,
                        Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("needRag", "false"))),
                        Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("needMemory", "false"))),
                        String.valueOf(parsed.getOrDefault("riskLevel", "LOW")),
                        parseCandidateAgents(parsed.get("candidateAgents"), nextAgent)
                );
            }
        }
        catch (Exception e) {
            log.debug("Route result is not structured JSON, using raw text: {}", raw);
        }
        // Plain text fallback: the LLM didn't follow the JSON contract.
        // Treat the raw text as best-effort routing — only trust it when it matches
        // a known AgentTypeEnum. Otherwise the routing result itself is the failure signal,
        // and we must NOT put the raw user-facing text into the candidateAgents list,
        // because the front-end renders it as a routable option.
        AgentTypeEnum resolved = AgentTypeEnum.agentNameOf(raw);
        if (resolved == null) {
            return new RouteResult(
                    AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                    0.5,
                    "plain text routing: " + raw,
                    "RouteAgent returned a plain text route, falling back to human handoff",
                    AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                    false,
                    false,
                    "MEDIUM",
                    List.of(AgentTypeEnum.HUMAN_HANDOFF.getAgentName())
            );
        }
        return new RouteResult(
                resolved.getAgentName(),
                0.85,
                "plain text routing",
                "RouteAgent returned a plain text route",
                resolved.getAgentName(),
                false,
                false,
                "LOW",
                parseCandidateAgents(null, resolved.getAgentName())
        );
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private List<String> parseCandidateAgents(Object value, String nextAgent) {
        List<String> candidates = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .forEach(candidates::add);
        }
        if (StringUtils.hasText(nextAgent) && !candidates.contains(nextAgent)) {
            candidates.add(0, nextAgent);
        }
        DEFAULT_CANDIDATE_AGENTS.stream()
                .filter(agent -> !candidates.contains(agent))
                .limit(2)
                .forEach(candidates::add);
        return Collections.unmodifiableList(candidates);
    }

    private RouteResult applyHumanHandoffPolicy(String question, RouteResult route) {
        String handoffReason = RouteSafetyPolicy.handoffReason(question, route.nextAgent(), route.confidence());
        if (!StringUtils.hasText(handoffReason)) {
            return route;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(AgentTypeEnum.HUMAN_HANDOFF.getAgentName());
        if (StringUtils.hasText(route.nextAgent()) && !AgentTypeEnum.HUMAN_HANDOFF.getAgentName().equals(route.nextAgent())) {
            candidates.add(route.nextAgent());
        }
        route.candidateAgents().stream()
                .filter(agent -> !candidates.contains(agent))
                .limit(2)
                .forEach(candidates::add);
        return new RouteResult(
                route.intent(),
                route.confidence(),
                handoffReason,
                handoffReason,
                AgentTypeEnum.HUMAN_HANDOFF.getAgentName(),
                route.needRag(),
                route.needMemory(),
                "HIGH",
                Collections.unmodifiableList(candidates)
        );
    }

    public record RouteResult(String intent, double confidence, String reason, String routeReason,
                               String nextAgent, boolean needRag,
                               boolean needMemory, String riskLevel, List<String> candidateAgents) {}
}
