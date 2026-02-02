package com.tianji.aigc.service;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import reactor.core.publisher.Flux;

/**
 * @author zzj
 * @version 1.0
 */
public interface ChatService {

    /**
     * 基于sessionId生成对话id，规则：用户id_sessionId
     *
     * @param sessionId 会话id
     * @return 对话id
     */
    static String getConversationId(String sessionId) {
        return UserContext.getUser() + "_" + sessionId;
    }

    /**
     * 流式对话聊天
     *
     * @param question  问题
     * @param sessionId 会话id
     * @return 大模型返回的内容
     */
    Flux<ChatEventVO> chat(String question, String sessionId);

    /**
     * 停止生成
     *
     * @param sessionId 会话id
     */
    void stop(String sessionId);

    /**
     * 文本对话
     *
     * @param question 问题
     * @return 大模型的生产的内容
     */
    default String chatText(String question) {
        return "";
    }
}
