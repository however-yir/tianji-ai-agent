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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void shouldTrimHistoryToMaxTurnsOnWrite() {
        String conversationId = "session-trim";
        when(stringRedisTemplate.boundListOps("CHAT:" + conversationId)).thenReturn(listOperations);
        MemoryGovernanceProperties props = new MemoryGovernanceProperties();
        props.setMaxTurns(4);
        RedisChatMemory memory = new RedisChatMemory(stringRedisTemplate, "CHAT:", props);

        memory.add(conversationId, List.of(new UserMessage("q1"), new UserMessage("q2")));

        org.mockito.Mockito.verify(listOperations).trim(-8L, -1);
        org.mockito.Mockito.verify(stringRedisTemplate).expire(eq("CHAT:" + conversationId), any(java.time.Duration.class));
    }

    @Test
    void shouldRedactSensitiveContentBeforeStoring() {
        String conversationId = "session-redact";
        when(stringRedisTemplate.boundListOps("CHAT:" + conversationId)).thenReturn(listOperations);
        RedisChatMemory memory = new RedisChatMemory(stringRedisTemplate);

        memory.add(conversationId, List.of(new UserMessage("我的手机号 13812345678")));

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(listOperations).rightPush(captor.capture());
        assertThat(captor.getValue()).doesNotContain("13812345678");
        assertThat(captor.getValue()).contains("[phone redacted]");
    }

    @Test
    void shouldUseConfiguredTtlForExpiry() {
        String conversationId = "session-ttl";
        when(stringRedisTemplate.boundListOps("CHAT:" + conversationId)).thenReturn(listOperations);
        MemoryGovernanceProperties props = new MemoryGovernanceProperties();
        props.setTtlDays(7);
        RedisChatMemory memory = new RedisChatMemory(stringRedisTemplate, "CHAT:", props);

        memory.add(conversationId, List.of(new UserMessage("q")));

        org.mockito.Mockito.verify(stringRedisTemplate).expire(eq("CHAT:" + conversationId),
                eq(java.time.Duration.ofDays(7)));
    }

    @Test
    void shouldClearConversationMemory() {
        String conversationId = "session-clear";
        redisChatMemory.clear(conversationId);

        org.mockito.Mockito.verify(stringRedisTemplate).delete("CHAT:" + conversationId);
    }
}
