package cn.itcast.service.impl;

import cn.itcast.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;

    /**
     * 处理用户问题并返回流式响应内容
     *
     * @param question 用户输入的问题内容
     * @return 包含逐条响应内容和结束标记的响应流，每个元素为字符串格式
     */
    @Override
    public Flux<String> chatStream(String question, String sessionId) {
        // 调用聊天客户端生成流式响应内容
        return this.chatClient.prompt()
                .user(question)
                .stream()
                .content()
                // 在流结束时添加结束标记
                .concatWith(Flux.just("[END]"));
    }

}
