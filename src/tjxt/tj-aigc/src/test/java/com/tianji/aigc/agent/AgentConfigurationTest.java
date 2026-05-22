package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.knowledgeops.KnowledgeOpsProperties;
import com.tianji.aigc.tools.CourseTools;
import com.tianji.aigc.tools.OrderTools;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConfigurationTest {

    @Mock
    private SystemPromptConfig systemPromptConfig;
    @Mock
    private CourseTools courseTools;
    @Mock
    private OrderTools orderTools;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private KnowledgeOpsClient knowledgeOpsClient;
    @Mock
    private KnowledgeOpsProperties knowledgeOpsProperties;

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void shouldConfigureRouteAgentAsMemorylessRouter() {
        when(systemPromptConfig.getRouteAgentSystemMessage()).thenReturn(new AtomicReference<>("route prompt"));

        RouteAgent routeAgent = new RouteAgent(systemPromptConfig);

        assertThat(routeAgent.getAgentType()).isEqualTo(AgentTypeEnum.ROUTE);
        assertThat(routeAgent.systemMessage()).isEqualTo("route prompt");
        assertThat(routeAgent.useChatMemory()).isFalse();
        assertThat(routeAgent.useAttachmentContext()).isFalse();
    }

    @Test
    void shouldExposeRecommendAgentToolsAndContext() {
        when(systemPromptConfig.getRecommendAgentSystemMessage()).thenReturn(new AtomicReference<>("recommend prompt"));
        when(knowledgeOpsProperties.isEnabled()).thenReturn(false);
        UserContext.setUser(10001L);

        RecommendAgent recommendAgent = new RecommendAgent(systemPromptConfig, courseTools, vectorStore, knowledgeOpsClient, knowledgeOpsProperties);

        assertThat(recommendAgent.getAgentType()).isEqualTo(AgentTypeEnum.RECOMMEND);
        assertThat(recommendAgent.systemMessage()).isEqualTo("recommend prompt");
        assertThat(recommendAgent.tools()).containsExactly(courseTools);
        assertThat(recommendAgent.toolContext("session-1", "request-1"))
                .containsEntry(Constant.USER_ID, 10001L)
                .containsEntry(Constant.REQUEST_ID, "request-1");
        assertThat(recommendAgent.advisors("Java 入门")).hasSize(1);
    }

    @Test
    void shouldExposeConsultAgentToolsAndSystemParams() {
        when(systemPromptConfig.getConsultAgentSystemMessage()).thenReturn(new AtomicReference<>("consult prompt"));
        UserContext.setUser(10002L);

        ConsultAgent consultAgent = new ConsultAgent(systemPromptConfig, vectorStore, courseTools);

        assertThat(consultAgent.getAgentType()).isEqualTo(AgentTypeEnum.CONSULT);
        assertThat(consultAgent.systemMessage()).isEqualTo("consult prompt");
        assertThat(consultAgent.tools()).containsExactly(courseTools);
        assertThat(consultAgent.toolContext("session-1", "request-1"))
                .containsEntry(Constant.USER_ID, 10002L)
                .containsEntry(Constant.REQUEST_ID, "request-1");
        assertThat(consultAgent.systemMessageParams()).containsKey("now");
    }

    @Test
    void shouldExposeBuyAgentOrderToolAndContext() {
        when(systemPromptConfig.getBuyAgentSystemMessage()).thenReturn(new AtomicReference<>("buy prompt"));
        UserContext.setUser(10003L);

        BuyAgent buyAgent = new BuyAgent(systemPromptConfig, orderTools);

        assertThat(buyAgent.getAgentType()).isEqualTo(AgentTypeEnum.BUY);
        assertThat(buyAgent.systemMessage())
                .contains("buy prompt")
                .contains("COURSE_SELECTED -> ORDER_PREVIEW -> USER_CONFIRM_REQUIRED -> HANDOFF_OR_DONE");
        assertThat(buyAgent.stateMachine()).containsExactly(
                "COURSE_SELECTED",
                "ORDER_PREVIEW",
                "USER_CONFIRM_REQUIRED",
                "HANDOFF_OR_DONE"
        );
        assertThat(buyAgent.tools()).containsExactly(orderTools);
        assertThat(buyAgent.toolContext("session-1", "request-1"))
                .containsEntry(Constant.USER_ID, 10003L)
                .containsEntry(Constant.REQUEST_ID, "request-1");
    }

    @Test
    void shouldConfigureKnowledgeAgent() {
        when(systemPromptConfig.getKnowledgeAgentSystemMessage()).thenReturn(new AtomicReference<>("knowledge prompt"));
        when(knowledgeOpsProperties.isEnabled()).thenReturn(false);

        KnowledgeAgent knowledgeAgent = new KnowledgeAgent(systemPromptConfig, knowledgeOpsClient, knowledgeOpsProperties, vectorStore);

        assertThat(knowledgeAgent.getAgentType()).isEqualTo(AgentTypeEnum.KNOWLEDGE);
        assertThat(knowledgeAgent.systemMessage()).isEqualTo("knowledge prompt");
        assertThat(knowledgeAgent.advisors("test question")).hasSize(1);
    }
}
