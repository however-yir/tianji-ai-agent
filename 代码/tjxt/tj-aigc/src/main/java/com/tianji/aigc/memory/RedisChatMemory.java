package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

public class RedisChatMemory implements ChatMemory {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 默认redis中key的前缀
    public static final String DEFAULT_PREFIX = "CHAT:";

    private String prefix = DEFAULT_PREFIX;

    public RedisChatMemory() {

    }

    public RedisChatMemory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (CollUtil.isEmpty(messages)) {
            return;
        }
        String key = this.getKey(conversationId);
        var listOperations = this.stringRedisTemplate.boundListOps(key);
        // 将消息序列化后写入到redis中
        messages.forEach(message -> listOperations.rightPush(MessageUtil.toJson(message)));
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (lastN < 0) {
            return List.of();
        }
        String key = this.getKey(conversationId);
        var listOperations = this.stringRedisTemplate.boundListOps(key);
        var messages = listOperations.range(0, lastN);

        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void clear(String conversationId) {
        this.stringRedisTemplate.delete(this.getKey(conversationId));
    }

    /**
     * 根据对话ID优化对话记录，删除最后的2条消息，因为这2条消息是从路由智能体存储的，请求由后续的智能体处理
     * 为了确保历史消息的完整性，所以需要将中间转发的消息清理掉
     *
     * @param conversationId 对话的唯一标识符
     */
    public void optimization(String conversationId) {
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        // 从Redis列表右侧弹出2个元素
        listOps.rightPop(2);
    }

}
