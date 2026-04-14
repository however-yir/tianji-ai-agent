package com.tianji.aigc.agent;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Tag("manual-integration")
class KnowledgeAgentTest {

    @Resource
    private KnowledgeAgent knowledgeAgent;

    @Test
    void processStream() {
        String question = "简要说明，java什么";
        String sessionId = "123";
        try {
            UserContext.setUser(123L);
            Flux<ChatEventVO> flux = knowledgeAgent.processStream(question, sessionId);
            ChatEventVO lastEvent = flux.blockLast(Duration.ofSeconds(30));
            assertNotNull(lastEvent);
        } finally {
            UserContext.removeUser();
        }
    }

}
