package cn.itcast.service;

import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {

    /**
     * 普通聊天
     *
     * @param question  用户提问
     * @param sessionId 对话id
     * @return 大模型的回答
     */
    String chat(String question, String sessionId);

    /**
     * 流式聊天
     *
     * @param question  用户提问
     * @param sessionId 对话id
     * @return 大模型的回答
     */
    Flux<String> chatStream(String question, String sessionId);

    /**
     * 多模型聊天
     *
     * @param question  用户提问
     * @param sessionId 会话id
     * @param files     文件列表
     * @return 大模型的回答
     */
    Flux<String> chatStreamMultiModel(String question, String sessionId, List<MultipartFile> files);
}
