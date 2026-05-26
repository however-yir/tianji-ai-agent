package com.tianji.aigc.service.impl;

import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.harness.ActionPolicyGuard;
import com.tianji.aigc.harness.AgentHarnessService;
import com.tianji.aigc.harness.AgentRuntime;
import com.tianji.aigc.harness.HarnessEventRecorder;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.tools.CourseTools;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private ChatClient openAiChatClient;
    @Mock
    private SystemPromptConfig systemPromptConfig;
    @Mock
    private Agent routeAgent;
    @Mock
    private Agent recommendAgent;
    @Mock
    private Agent humanHandoffAgent;
    @Mock
    private CourseClient courseClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private KnowledgeOpsClient knowledgeOpsClient;

    @Test
    void shouldRouteQuestionToResolvedAgent() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(recommendAgent.getAgentType()).thenReturn(AgentTypeEnum.RECOMMEND);
        when(routeAgent.process("推荐课程", "session-1")).thenReturn(AgentTypeEnum.RECOMMEND.getAgentName());

        ChatEventVO downstreamEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData("recommend-result")
                .build();
        when(recommendAgent.processStream("推荐课程", "session-1")).thenReturn(Flux.just(downstreamEvent));

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent, recommendAgent)
        );

        List<ChatEventVO> events = service.chat("推荐课程", "session-1").collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo(ChatEventTypeEnum.ROUTE.getValue());
        assertThat(events.get(1)).isEqualTo(downstreamEvent);
    }

    @Test
    void shouldEmitRouteTraceFieldsForStructuredRouteResult() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(recommendAgent.getAgentType()).thenReturn(AgentTypeEnum.RECOMMEND);
        when(routeAgent.process("推荐 Java 课程", "session-route")).thenReturn("""
                {
                  "intent": "RECOMMEND",
                  "confidence": 0.92,
                  "reason": "用户需要课程推荐",
                  "routeReason": "推荐意图明确",
                  "nextAgent": "RECOMMEND",
                  "candidateAgents": ["RECOMMEND", "CONSULT"],
                  "needRag": true,
                  "needMemory": true,
                  "riskLevel": "LOW"
                }
                """);
        ChatEventVO downstreamEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData("recommend-result")
                .build();
        when(recommendAgent.processStream("推荐 Java 课程", "session-route")).thenReturn(Flux.just(downstreamEvent));

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent, recommendAgent)
        );

        List<ChatEventVO> events = service.chat("推荐 Java 课程", "session-route").collectList().block();

        AgentServiceImpl.RouteResult route = (AgentServiceImpl.RouteResult) events.get(0).getEventData();
        assertThat(route.confidence()).isEqualTo(0.92);
        assertThat(route.routeReason()).isEqualTo("推荐意图明确");
        assertThat(route.candidateAgents()).contains("RECOMMEND", "CONSULT");
    }

    @Test
    void shouldRouteToHarnessBackedToolAndEmitTraceAndParams() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(routeAgent.process("介绍课程 1589905661084430337", "session-harness")).thenReturn("""
                {
                  "intent": "CONSULT",
                  "confidence": 0.9,
                  "reason": "用户查询课程详情",
                  "routeReason": "课程咨询意图明确",
                  "nextAgent": "CONSULT",
                  "candidateAgents": ["CONSULT", "RECOMMEND"],
                  "riskLevel": "LOW"
                }
                """);
        CourseBaseInfoDTO dto = new CourseBaseInfoDTO();
        dto.setId(1589905661084430337L);
        dto.setName("Java 后端工程师体系课");
        dto.setPrice(19900);
        when(courseClient.baseInfo(1589905661084430337L, true)).thenReturn(dto);
        CourseTools courseTools = new CourseTools(newHarnessService());
        Agent consultAgent = new HarnessBackedConsultAgent(courseTools);

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent, consultAgent)
        );

        List<ChatEventVO> events = service.chat("介绍课程 1589905661084430337", "session-harness").collectList().block();

        assertThat(events).hasSize(4);
        assertThat(events.get(0).getEventType()).isEqualTo(ChatEventTypeEnum.ROUTE.getValue());
        assertThat(events.get(1).getEventType()).isEqualTo(ChatEventTypeEnum.TRACE.getValue());
        assertThat(events.get(2).getEventType()).isEqualTo(ChatEventTypeEnum.PARAM.getValue());
        assertThat((List<?>) events.get(1).getEventData())
                .singleElement()
                .satisfies(trace -> {
                    Map<?, ?> traceMap = (Map<?, ?>) trace;
                    assertThat(traceMap.get("agentName")).isEqualTo("CONSULT");
                    assertThat(traceMap.get("actionType")).isEqualTo("course.query");
                    assertThat(traceMap.get("success")).isEqualTo(true);
                });
        Map<?, ?> params = (Map<?, ?>) events.get(2).getEventData();
        assertThat(params.containsKey("courseInfo_1589905661084430337")).isTrue();
        assertThat(params.containsKey(HarnessEventRecorder.TRACE_FIELD)).isTrue();
        CourseInfo courseInfo = (CourseInfo) params.get("courseInfo_1589905661084430337");
        assertThat(courseInfo.getName()).isEqualTo("Java 后端工程师体系课");
        verify(courseClient).baseInfo(1589905661084430337L, true);
    }

    @Test
    void shouldRouteLowConfidenceResultToHumanHandoff() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(humanHandoffAgent.getAgentType()).thenReturn(AgentTypeEnum.HUMAN_HANDOFF);
        when(routeAgent.process("这个情况有点复杂", "session-low-confidence")).thenReturn("""
                {
                  "intent": "CONSULT",
                  "confidence": 0.42,
                  "reason": "意图不够清晰",
                  "nextAgent": "CONSULT",
                  "candidateAgents": ["CONSULT", "HUMAN_HANDOFF"],
                  "riskLevel": "MEDIUM"
                }
                """);
        ChatEventVO handoffEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData("handoff")
                .build();
        when(humanHandoffAgent.processStream("这个情况有点复杂", "session-low-confidence")).thenReturn(Flux.just(handoffEvent));

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent, humanHandoffAgent)
        );

        List<ChatEventVO> events = service.chat("这个情况有点复杂", "session-low-confidence").collectList().block();

        AgentServiceImpl.RouteResult route = (AgentServiceImpl.RouteResult) events.get(0).getEventData();
        assertThat(route.nextAgent()).isEqualTo(AgentTypeEnum.HUMAN_HANDOFF.getAgentName());
        assertThat(route.routeReason()).contains("置信度");
        assertThat(events.get(1)).isEqualTo(handoffEvent);
    }

    @Test
    void shouldRouteComplaintQuestionsToHumanHandoff() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(humanHandoffAgent.getAgentType()).thenReturn(AgentTypeEnum.HUMAN_HANDOFF);
        when(routeAgent.process("我要投诉课程质量，感觉被骗了", "session-complaint"))
                .thenReturn("{\"intent\":\"COMPLAINT\",\"confidence\":0.9,\"nextAgent\":\"COMPLAINT\"}");
        ChatEventVO handoffEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData("handoff")
                .build();
        when(humanHandoffAgent.processStream("我要投诉课程质量，感觉被骗了", "session-complaint")).thenReturn(Flux.just(handoffEvent));

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent, humanHandoffAgent)
        );

        List<ChatEventVO> events = service.chat("我要投诉课程质量，感觉被骗了", "session-complaint").collectList().block();

        AgentServiceImpl.RouteResult route = (AgentServiceImpl.RouteResult) events.get(0).getEventData();
        assertThat(route.nextAgent()).isEqualTo(AgentTypeEnum.HUMAN_HANDOFF.getAgentName());
        assertThat(route.routeReason()).contains("投诉");
    }

    @Test
    void shouldRoutePaymentSensitiveQuestionsToHumanHandoff() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(humanHandoffAgent.getAgentType()).thenReturn(AgentTypeEnum.HUMAN_HANDOFF);
        when(routeAgent.process("我已经扣款了但是订单不到账", "session-pay"))
                .thenReturn("{\"intent\":\"AFTER_SALE\",\"confidence\":0.9,\"nextAgent\":\"AFTER_SALE\"}");
        ChatEventVO handoffEvent = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData("handoff")
                .build();
        when(humanHandoffAgent.processStream("我已经扣款了但是订单不到账", "session-pay")).thenReturn(Flux.just(handoffEvent));

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent, humanHandoffAgent)
        );

        List<ChatEventVO> events = service.chat("我已经扣款了但是订单不到账", "session-pay").collectList().block();

        AgentServiceImpl.RouteResult route = (AgentServiceImpl.RouteResult) events.get(0).getEventData();
        assertThat(route.nextAgent()).isEqualTo(AgentTypeEnum.HUMAN_HANDOFF.getAgentName());
        assertThat(route.routeReason()).contains("支付");
    }

    @Test
    void shouldReturnDirectResultWhenRouteOutputDoesNotMatchAnyAgent() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);
        when(routeAgent.process("你好", "session-2")).thenReturn("普通回复");

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent)
        );

        List<ChatEventVO> events = service.chat("你好", "session-2").collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo(ChatEventTypeEnum.ROUTE.getValue());
        assertThat(events.get(1).getEventType()).isEqualTo(ChatEventTypeEnum.STOP.getValue());
    }

    @Test
    void shouldDelegateStopToRouteAgent() {
        when(routeAgent.getAgentType()).thenReturn(AgentTypeEnum.ROUTE);

        AgentServiceImpl service = new AgentServiceImpl(
                openAiChatClient,
                systemPromptConfig,
                List.of(routeAgent)
        );

        service.stop("session-3");

        verify(routeAgent).stop("session-3");
    }

    private AgentHarnessService newHarnessService() {
        return new AgentHarnessService(
                new ActionPolicyGuard(),
                new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient),
                new HarnessEventRecorder()
        );
    }

    private static class HarnessBackedConsultAgent implements Agent {
        private static final String REQUEST_ID = "request-harness";

        private final CourseTools courseTools;

        private HarnessBackedConsultAgent(CourseTools courseTools) {
            this.courseTools = courseTools;
        }

        @Override
        public Flux<ChatEventVO> processStream(String question, String sessionId) {
            courseTools.queryCourseById(
                    1589905661084430337L,
                    new ToolContext(Map.of(
                            Constant.REQUEST_ID, REQUEST_ID,
                            Constant.SESSION_ID, sessionId,
                            Constant.AGENT_NAME, "CONSULT",
                            Constant.USER_ID, 10001L
                    ))
            );
            Map<String, Object> params = ToolResultHolder.get(REQUEST_ID);
            ToolResultHolder.remove(REQUEST_ID);
            Object trace = params.get(HarnessEventRecorder.TRACE_FIELD);
            return Flux.just(
                    ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.TRACE.getValue())
                            .eventData(trace)
                            .build(),
                    ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.PARAM.getValue())
                            .eventData(params)
                            .build(),
                    ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.STOP.getValue())
                            .build()
            );
        }

        @Override
        public String process(String question, String sessionId) {
            return "";
        }

        @Override
        public AgentTypeEnum getAgentType() {
            return AgentTypeEnum.CONSULT;
        }

        @Override
        public void stop(String sessionId) {
        }
    }
}
