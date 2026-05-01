package cn.itcast.controller;

import cn.itcast.dto.ChatDTO;
import cn.itcast.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;


    /**
     * 处理流式聊天请求，返回服务器发送事件（SSE）格式的响应流
     *
     * @param chatDTO 用户输入的聊天问题
     * @return 包含逐条聊天响应的响应式数据流，通过Server-Sent Events协议传输
     */
    @PostMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatDTO chatDTO) {
        return chatService.chatStream(chatDTO.getQuestion(), chatDTO.getSessionId());
    }

}
