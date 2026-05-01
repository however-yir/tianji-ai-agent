package com.tianji.aigc.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private BoundListOperations<String, String> listOperations;

    private RedisChatMemory redisChatMemory;

    @BeforeEach
    void setUp() {
        this.redisChatMemory = new RedisChatMemory(stringRedisTemplate);
    }

    @Test
    void shouldReadLatestMessagesFromTail() {
        String conversationId = "session-1";
        when(stringRedisTemplate.boundListOps("CHAT:" + conversationId)).thenReturn(listOperations);
        when(listOperations.range(-2, -1)).thenReturn(List.of(
                MessageUtil.toJson(new UserMessage("latest-question")),
                MessageUtil.toJson(new AssistantMessage("latest-answer"))
        ));

        List<Message> messages = redisChatMemory.get(conversationId, 2);

        verify(listOperations).range(-2, -1);
        assertThat(messages)
                .extracting(Message::getText)
                .containsExactly("latest-question", "latest-answer");
    }

    @Test
    void shouldReturnEmptyWhenRequestedWindowIsNotPositive() {
        assertThat(redisChatMemory.get("session-1", 0)).isEmpty();

        verifyNoInteractions(stringRedisTemplate);
    }
}
