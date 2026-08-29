package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

public class RedisChatMemory implements ChatMemory {

    // 默认redis中key的前缀
    public static final String DEFAULT_PREFIX = "CHAT:";

    private final StringRedisTemplate stringRedisTemplate;
    private final String prefix;
    private final MemoryGovernanceProperties properties;

    public RedisChatMemory(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, DEFAULT_PREFIX, new MemoryGovernanceProperties());
    }

    public RedisChatMemory(StringRedisTemplate stringRedisTemplate, String prefix,
                           MemoryGovernanceProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.prefix = prefix;
        this.properties = properties == null ? new MemoryGovernanceProperties() : properties;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (CollUtil.isEmpty(messages)) {
            return;
        }
        String key = this.getKey(conversationId);
        var listOperations = this.stringRedisTemplate.boundListOps(key);
        // 将消息序列化并经脱敏后写入redis中
        messages.forEach(message -> listOperations.rightPush(
                com.tianji.aigc.handoff.HandoffRedaction.redact(MessageUtil.toJson(message))));
        // 有界历史：只保留最近 maxTurns 轮（一条消息一个元素，轮数取 2 倍元素估计）
        listOperations.trim(-properties.getMaxTurns() * 2L, -1);
        // 写入后滚动续期 TTL
        this.stringRedisTemplate.expire(key, Duration.ofDays(properties.getTtlDays()));
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }

    /** Windowed history read kept for UI rendering; Spring AI's ChatMemory interface only declares full reads. */
    public List<Message> get(String conversationId, int lastN) {
        if (lastN <= 0) {
            return List.of();
        }
        BoundListOperations<String, String> listOperations = this.stringRedisTemplate.boundListOps(this.getKey(conversationId));
        List<String> messages = listOperations.range(-lastN, -1);
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }

        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public List<Message> get(String conversationId) {
        BoundListOperations<String, String> listOperations = this.stringRedisTemplate.boundListOps(this.getKey(conversationId));
        List<String> messages = listOperations.range(0, -1);
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void clear(String conversationId) {
        this.stringRedisTemplate.delete(this.getKey(conversationId));
    }
}
