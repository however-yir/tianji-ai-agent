package com.tianji.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强型智能体
 */
// @Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient dashScopeChatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final ChatSessionService chatSessionService;

    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();
    // 输出结束的标记
    private static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取用户id
        var userId = UserContext.getUser();
        // 更新对话标题
        this.chatSessionService.update(sessionId, question, userId);
        //生成请求id
        String requestId = IdUtil.fastSimpleUUID();
        //获取对话id
        String conversationId = ChatService.getConversationId(sessionId);
        // 收集大模型生成的内容，用于停止生成时，保存数据到redis中
        StringBuilder textBuilder = new StringBuilder();
        return this.dashScopeChatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(systemPromptConfig.getChatSystemMessage().get()) // 系统提示词
                        .param("now", DateUtil.now()) //系统当前时间
                )
                .advisors(advisor -> advisor
                        .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
                                .query("") // 查询条件，为空代表全部
                                .topK(999) // 总共查询多少条数据
                                .build()))
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId)) //在工具上下文中传递请求id
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true)) // 生成开始时，标记为正在输出
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId)) // 生成结束后，将标识移除
                .doOnError(t -> GENERATE_STATUS.remove(sessionId)) // 生成失败时，将标识移除
                .doOnCancel(() -> {
                    // 手动保存聊天记录到redis中
                    this.saveStopHistoryRecord(conversationId, textBuilder.toString());
                })
                //是否进行输出的条件，true：继续输出，false：停止输出
                .takeWhile(s -> GENERATE_STATUS.getOrDefault(sessionId, false))
                .map(response -> {
                    String finishReason = response.getResult().getMetadata().getFinishReason();
                    if (StrUtil.equals(Constant.STOP, finishReason)) {
                        // 获取到消息id
                        String messageId = response.getMetadata().getId();
                        // 将消息id 与 请求id关联
                        ToolResultHolder.put(messageId, Constant.REQUEST_ID, requestId);
                    }
                    // 获取到大模型返回的文本内容
                    String text = response.getResult().getOutput().getText();
                    // 将文本内容追加到textBuilder中
                    textBuilder.append(text);
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    //判断工具的保持器中是否有tool执行的结果，如果有，写入到输出流中
                    Map<String, Object> result = ToolResultHolder.get(requestId);
                    if (ObjectUtil.isNotEmpty(result)) {
                        //删除容器中的数据，数据是临时使用，一次请求结束后就变成垃圾数据了
                        ToolResultHolder.remove(requestId);
                        //参数数据
                        ChatEventVO chatEventVO = ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.PARAM.getValue()) // 停止事件
                                .eventData(result)
                                .build();
                        return Flux.just(chatEventVO, STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT);
                }));
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 会话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }
}
