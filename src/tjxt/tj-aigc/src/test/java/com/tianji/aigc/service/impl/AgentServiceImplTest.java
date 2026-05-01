package com.tianji.aigc.service.impl;

import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.vo.ChatEventVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

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

        assertThat(events).containsExactly(downstreamEvent);
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
        assertThat(events.get(0).getEventType()).isEqualTo(ChatEventTypeEnum.DATA.getValue());
        assertThat(events.get(0).getEventData()).isEqualTo("普通回复");
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
}
