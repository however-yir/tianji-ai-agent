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
class RecommendAgentTest {

    @Resource
    private RecommendAgent recommendAgent;

    @Test
    void processStream() {
        String question = "推荐课程，20岁，本科，对产品运营感兴趣";
        String sessionId = "123";
        try {
            UserContext.setUser(123L);
            Flux<ChatEventVO> flux = recommendAgent.processStream(question, sessionId);
            ChatEventVO lastEvent = flux.blockLast(Duration.ofSeconds(30));
            assertNotNull(lastEvent);
        } finally {
            UserContext.removeUser();
        }
    }

}
