package cn.itcast.controller;

import cn.itcast.dto.ChatDTO;
import cn.itcast.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final VectorStore vectorStore;

    /**
     * 普通对话
     */
    @PostMapping
    public String chat(@RequestBody ChatDTO chatDTO) {
        return chatService.chat(chatDTO.getQuestion(), chatDTO.getSessionId());
    }

    /**
     * 流式对话
     */
    @PostMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatDTO chatDTO) {
        return this.chatService.chatStream(chatDTO.getQuestion(), chatDTO.getSessionId());
    }

    /**
     * 搜索向量数据库
     *
     * @param query 搜索关键字
     */
    @PostMapping("/search")
    public List<Document> search(@RequestParam("query") String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(3) //设置返回最多的数据条数
                .build();
        return this.vectorStore.similaritySearch(searchRequest);
    }

    /**
     * 处理多模型流式聊天请求的端点方法
     * 该方法接收用户问题、会话ID和可选文件列表，返回流式文本响应
     *
     * @param question 用户输入的问题内容
     * @param sessionId 唯一会话标识符，用于关联聊天上下文
     * @param files 可选的上传文件列表，用于多模态处理（可为空）
     * @return Flux<String> 流式返回的文本响应，按事件流格式传输
     */
    @PostMapping(value = "stream-multi-model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamMultiModel(@RequestParam("question") String question,
                                             @RequestParam("sessionId") String sessionId,
                                             @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return chatService.chatStreamMultiModel(question, sessionId, files);
    }
}
