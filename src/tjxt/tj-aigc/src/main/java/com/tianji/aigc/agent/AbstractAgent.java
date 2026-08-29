package com.tianji.aigc.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.attachment.AttachmentContextHolder;
import com.tianji.aigc.config.ModelOptionsHolder;
import com.tianji.aigc.config.StreamingProperties;
import com.tianji.aigc.config.ModelOptionsHolder.ModelOptions;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.harness.HarnessEventRecorder;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractAgent implements Agent {
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";



    @Resource
    private ChatClient dashScopeChatClient;
    @Resource
    private ChatClient openAiChatClient;
    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private ChatMemory chatMemory;
    @Resource(name = "messageChatMemoryAdvisor")
    private Advisor messageChatMemoryAdvisor;
    @Resource
    private StreamingProperties streamingProperties;

    // 输出结束的标记
    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();

    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    public static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    @Override
    public String process(String question, String sessionId) {
        // 获取用户id
        var userId = UserContext.getUser();
        var requestId = this.generateRequestId();
        this.registerAttachmentParams(sessionId, requestId);

        //更新会话时间
        this.chatSessionService.update(sessionId, question, userId);
        try {
            return this.getChatClientRequest(sessionId, requestId, question)
                    .call()
                    .content();
        }
        finally {
            if (this.useAttachmentContext()) {
                AttachmentContextHolder.clear(sessionId);
            }
        }
    }

    public Flux<ChatEventVO> processStream(String question, String sessionId) {
        // 获取用户id
        var userId = UserContext.getUser();
        var requestId = this.generateRequestId();
        // 大模型输出内容的缓存器，用于在输出中断后的数据存储
        StringBuilder outputBuilder = new StringBuilder();
        this.registerAttachmentParams(sessionId, requestId);

        //更新会话时间
        this.chatSessionService.update(sessionId, question, userId);

        // 用 Sinks.Many 统一管理事件：模型增量通过 doOnNext 投递，
        // 终态事件（PARAM/TRACE/STOP）保证在 complete / cancel / error 三条路径上都会发一次，
        // 避免之前 takeWhile + concatWith 在取消时下游一起被取消、客户端收不到 STOP 事件的问题。
        int bufferSize = streamingProperties == null ? 256 : streamingProperties.getBufferSize();
        Sinks.Many<ChatEventVO> sink = Sinks.many().unicast()
                .onBackpressureBuffer(Queues.<ChatEventVO>get(bufferSize).get());
        AtomicBoolean terminalEmitted = new AtomicBoolean(false);

        Runnable emitTerminal = () -> {
            if (!terminalEmitted.compareAndSet(false, true)) {
                return;
            }
            // Persist the assistant turn on every terminal path.
            // 之前 doOnCancel 才落库，正常完成路径不落，导致历史记录里只看到用户消息、看不到助手回复。
            this.saveStopHistoryRecord(sessionId, outputBuilder.toString());
            try {
                var map = ToolResultHolder.get(requestId);
                if (CollUtil.isNotEmpty(map)) {
                    ToolResultHolder.remove(requestId);
                    Object trace = map.get(HarnessEventRecorder.TRACE_FIELD);
                    if (trace != null) {
                        sink.tryEmitNext(ChatEventVO.builder()
                                .eventData(trace)
                                .eventType(ChatEventTypeEnum.TRACE.getValue())
                                .build());
                    }
                    sink.tryEmitNext(ChatEventVO.builder()
                            .eventData(map)
                            .eventType(ChatEventTypeEnum.PARAM.getValue())
                            .build());
                }
                sink.tryEmitNext(STOP_EVENT);
                sink.tryEmitComplete();
            }
            finally {
                if (this.useAttachmentContext()) {
                    AttachmentContextHolder.clear(sessionId);
                }
            }
        };

        this.getChatClientRequest(sessionId, requestId, question)
                .stream()
                .chatResponse()
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true))
                .doOnComplete(() -> {
                    GENERATE_STATUS.remove(sessionId);
                    emitTerminal.run();
                })
                .doOnError(throwable -> {
                    GENERATE_STATUS.remove(sessionId);
                    if (this.useAttachmentContext()) {
                        AttachmentContextHolder.clear(sessionId);
                    }
                    emitTerminal.run();
                })
                .doOnCancel(() -> {
                    GENERATE_STATUS.remove(sessionId);
                    if (this.useAttachmentContext()) {
                        AttachmentContextHolder.clear(sessionId);
                    }
                    emitTerminal.run();
                })
                .takeWhile(s -> Optional.ofNullable(GENERATE_STATUS.get(sessionId)).orElse(false))
                .map(chatResponse -> {
                    String text = chatResponse.getResult().getOutput().getText();
                    outputBuilder.append(text);
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .subscribe(
                        event -> {
                            var result = sink.tryEmitNext(event);
                            if (result.isFailure()) {
                                log.warn("[Agent] SSE 缓冲溢出，丢弃增量事件, sessionId={}, cause={}",
                                        sessionId, result);
                            }
                        },
                        err -> {
                            // 模型流异常：把异常也作为终态路径处理
                            GENERATE_STATUS.remove(sessionId);
                            if (this.useAttachmentContext()) {
                                AttachmentContextHolder.clear(sessionId);
                            }
                            emitTerminal.run();
                        },
                        () -> {
                            // takeWhile 触发 upstream complete 时，已经走过上面的 doOnComplete 路径，
                            // 这里只在 takeWhile 没有触发 complete（极少见，防御性）时再补一次。
                            if (!terminalEmitted.get()) {
                                emitTerminal.run();
                            }
                        }
                );

        return sink.asFlux();
    }

    /**
     * 保存停止输出的记录
     *
     * @param sessionId 会话id
     * @param content   大模型输出的内容
     */
    private void saveStopHistoryRecord(String sessionId, String content) {
        String conversationId = ChatService.getConversationId(sessionId);
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }

    private String generateRequestId() {
        return IdUtil.fastSimpleUUID();
    }

    private ChatClient.ChatClientRequestSpec getChatClientRequest(String sessionId, String requestId, String question) {
        List<Advisor> advisors = new ArrayList<>();
        if (this.useChatMemory()) {
            advisors.add(this.messageChatMemoryAdvisor);
        }
        advisors.addAll(this.advisors(question));

        ChatClient client = resolveChatClient();
        ChatClient.ChatClientRequestSpec request = client.prompt()
                .system(promptSystem -> promptSystem.text(this.resolveSystemMessage(sessionId)).params(this.systemMessageParams()))
                .advisors(advisor -> advisor.advisors(advisors).params(this.advisorParams(sessionId, requestId)))
                .tools(this.tools())
                .toolContext(this.toolContext(sessionId, requestId))
                .user(question);

        applyChatOptions(request);
        return request;
    }

    /**
     * 根据 ModelOptionsHolder 中的 provider 选择对应的 ChatClient。
     * 默认使用 dashScopeChatClient。
     */
    private ChatClient resolveChatClient() {
        ModelOptions options = ModelOptionsHolder.get();
        if (options != null && options.hasProvider() && "openai".equalsIgnoreCase(options.provider())) {
            return this.openAiChatClient;
        }
        return this.dashScopeChatClient;
    }

    /**
     * 从 ModelOptionsHolder 读取 model 和 temperature，构建对应的 ChatOptions 并应用到请求。
     */
    private void applyChatOptions(ChatClient.ChatClientRequestSpec request) {
        ModelOptions options = ModelOptionsHolder.get();
        if (options == null) return;

        String provider = options.hasProvider() ? options.provider() : "dashscope";

        // Both providers (OpenAI and DashScope via its compatible endpoint) share the
        // OpenAiChatOptions; the provider name above is a UI-facing brand label.
        OpenAiChatOptions oaiOptions = new OpenAiChatOptions();
        if (options.hasModel()) oaiOptions.setModel(options.model());
        if (options.hasTemperature()) oaiOptions.setTemperature(options.temperature());
        request.options(oaiOptions);
    }

    protected boolean useAttachmentContext() {
        return true;
    }

    private void registerAttachmentParams(String sessionId, String requestId) {
        if (!this.useAttachmentContext()) {
            return;
        }
        AttachmentContext context = AttachmentContextHolder.peek(sessionId);
        if (context != null && context.hasSources()) {
            ToolResultHolder.putAll(requestId, context.toParamMap());
        }
    }

    private String resolveSystemMessage(String sessionId) {
        String systemMessage = this.systemMessage();
        if (!this.useAttachmentContext()) {
            return systemMessage;
        }
        AttachmentContext context = AttachmentContextHolder.peek(sessionId);
        if (context == null || !context.hasSources()) {
            return systemMessage;
        }
        return systemMessage + "\n\n" + context.toSystemPrompt();
    }

    @Override
    public Map<String, Object> advisorParams(String sessionId, String requestId) {
        String conversationId = ChatService.getConversationId(sessionId);
        return Map.of(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId);
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }
}
