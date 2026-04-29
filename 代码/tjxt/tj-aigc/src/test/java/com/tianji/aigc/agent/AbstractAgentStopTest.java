package com.tianji.aigc.agent;

import com.tianji.aigc.enums.AgentTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractAgentStopTest {

    private final TestAgent agent = new TestAgent();

    @AfterEach
    void tearDown() {
        AbstractAgent.GENERATE_STATUS.clear();
    }

    @Test
    void shouldRemoveGenerateStatusWhenStopped() {
        AbstractAgent.GENERATE_STATUS.put("session-1", true);

        agent.stop("session-1");

        assertThat(AbstractAgent.GENERATE_STATUS).doesNotContainKey("session-1");
    }

    private static class TestAgent extends AbstractAgent {

        @Override
        public AgentTypeEnum getAgentType() {
            return AgentTypeEnum.KNOWLEDGE;
        }

        @Override
        public String systemMessage() {
            return "test prompt";
        }
    }
}
