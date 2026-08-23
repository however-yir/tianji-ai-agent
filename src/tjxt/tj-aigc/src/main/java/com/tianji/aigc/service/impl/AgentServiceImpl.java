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

import java.util.ArrayList;
import java.util.Collections;
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
    private static final double HANDOFF_CONFIDENCE_THRESHOLD = 0.65;
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
        RouteResult route = applyHumanHandoffPolicy(question, parseRouteResult(rawResult));
        AgentTypeEnum agentTypeEnum = AgentTypeEnum.agentNameOf(route.nextAgent());

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
        } catch (Exception e) {
            log.debug("Route result is not structured JSON, using raw text: {}", raw);
        }
        double confidence = AgentTypeEnum.agentNameOf(raw) == null ? 0.5 : 0.85;
        return new RouteResult(
                raw,
                confidence,
                "plain text routing",
                "RouteAgent returned a plain text route",
                raw,
                false,
                false,
                "LOW",
                parseCandidateAgents(null, raw)
        );
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
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
        String handoffReason = resolveHumanHandoffReason(question, route);
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

    private String resolveHumanHandoffReason(String question, RouteResult route) {
        String normalized = question == null ? "" : question.toLowerCase();
        if (route.confidence() < HANDOFF_CONFIDENCE_THRESHOLD) {
            return "路由置信度低于人工兜底阈值";
        }
        if (AgentTypeEnum.COMPLAINT.getAgentName().equals(route.nextAgent()) || containsAny(normalized,
                "投诉", "差评", "举报", "维权", "欺诈", "被骗", "骗人", "315")) {
            return "投诉或维权类问题需要人工介入";
        }
        if (containsAny(normalized,
                "支付失败", "付款失败", "已扣款", "扣款", "重复扣费", "银行卡", "信用卡",
                "支付密码", "验证码", "转账", "不到账", "退款到账")) {
            return "支付敏感问题需要人工确认";
        }
        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record RouteResult(String intent, double confidence, String reason, String routeReason,
                               String nextAgent, boolean needRag,
                               boolean needMemory, String riskLevel, List<String> candidateAgents) {}
}
