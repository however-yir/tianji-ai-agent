package cn.itcast.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.date.DateUtil;
import cn.itcast.constants.Constant;
import cn.itcast.service.ChatService;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Override
    public String chat(String question, String sessionId) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3) //设置返回最多的数据条数
                .build();
        return this.chatClient.prompt()
                // .system(Constant.SYSTEM_ROLE) // 系统角色
                .system(prompt -> prompt.param("now", DateUtil.now()))
                // 设置会话记忆参数
                .advisors(advisor -> advisor
                        .advisors(new QuestionAnswerAdvisor(vectorStore, searchRequest)) //RAG
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                .user(question) // 用户消息
                .call() // 发送消息
                .content(); // 获取返回结果
    }

    @Override
    public Flux<String> chatStream(String question, String sessionId) {
        // SearchRequest searchRequest = SearchRequest.builder()
        //         .query(question)
        //         .topK(3) //设置返回最多的数据条数
        //         .build();
        return this.chatClient.prompt()
                // .system(Constant.SYSTEM_ROLE) // 系统角色
                // .system(prompt -> prompt.param("now", DateUtil.now()))
                // .advisors(advisor -> advisor
                //         .advisors(new QuestionAnswerAdvisor(vectorStore, searchRequest)) //RAG
                //         .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                .user(question)
                .stream()
                .content()
                // .doOnNext(content ->  log.info("question: {}, content: {}", question, content))
                .concatWith(Flux.just("[END]")); // 给前端结束的标识，这个标识是和前端协商的
    }

    @Override
    public Flux<String> chatStreamMultiModel(String question, String sessionId, List<MultipartFile> files) {
        // 将上传的图片转成媒体对象
        List<Media> media = CollStreamUtil.toList(files, file -> new Media(MimeTypeUtils.IMAGE_PNG, file.getResource()));
        // 将用户文字与媒体对象合成一个用户消息
        var userMessage = new UserMessage(question, media);

        // 构造模型的请求参数
        var prompt = new Prompt(userMessage, DashScopeChatOptions.builder()
                .withModel("qwen-omni-turbo") // 设置新的大模型名称
                .withMultiModel(true)
                .build());

        return this.chatClient.prompt(prompt)
                // .system(Constant.SYSTEM_ROLE) // 系统角色
                .system(s -> s.param("now", DateUtil.now()))
                .advisors(advisor -> advisor
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                .stream()
                .content()
                .concatWith(Flux.just("[END]")); // 给前端结束的标识，这个标识是和前端协商的
    }
}
