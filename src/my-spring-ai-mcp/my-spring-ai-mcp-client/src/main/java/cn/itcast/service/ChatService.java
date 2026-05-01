package cn.itcast.service;

import reactor.core.publisher.Flux;

public interface ChatService {

    /**
     * 流式聊天
     *
     * @param question 用户提问
     * @param sessionId 会话id
     * @return 大模型的回答
     */
    Flux<String> chatStream(String question, String sessionId);

}
