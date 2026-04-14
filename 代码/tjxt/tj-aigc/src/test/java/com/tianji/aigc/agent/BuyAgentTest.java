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
class BuyAgentTest {

    @Resource
    private BuyAgent buyAgent;

    @Test
    void processStream() {
        String question = "下单购买，课程id为：1589905661084430337";
        String sessionId = "123";
        try {
            UserContext.setUser(123L);
            Flux<ChatEventVO> flux = buyAgent.processStream(question, sessionId);
            ChatEventVO lastEvent = flux.blockLast(Duration.ofSeconds(30));
            assertNotNull(lastEvent);
        } finally {
            UserContext.removeUser();
        }
    }

}
