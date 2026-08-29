package com.tianji.aigc.controller;

import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.attachment.AttachmentContextHolder;
import com.tianji.aigc.config.ModelOptionsHolder;
import com.tianji.aigc.config.ModelOptionsHolder.ModelOptions;
import com.tianji.aigc.config.ModelProviderProperties;
import com.tianji.aigc.dto.ChatDTO;
import com.tianji.aigc.service.AttachmentService;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.aigc.vo.TemplateVO;
import com.tianji.common.annotations.NoWrapper;
import com.tianji.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final ModelProviderProperties modelProviderProperties;
    private static final TemplateVO TEMPLATE_VO = new TemplateVO();

    @NoWrapper
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        // 空问题在 dev-demo 会在 buildParams 处 NPE，在真实链路会把 null 传给模型；统一前置校验并返回 400
        if (chatDTO.getQuestion() == null || chatDTO.getQuestion().isBlank()) {
            throw new BadRequestException("问题内容不能为空。");
        }
        AttachmentContext context = attachmentService.buildContext(chatDTO.getAttachmentIds(), chatDTO.getQuestion());
        AttachmentContextHolder.put(chatDTO.getSessionId(), context);

        ModelOptions options = new ModelOptions(
                chatDTO.getProvider(),
                chatDTO.getModel(),
                chatDTO.getTemperature()
        );
        // set 与 clear 必须在同一线程：模型选项只在构建 Flux 链时被同步读取，
        // 之前用 doFinally 在流终止线程清理会清错线程，导致 Tomcat 线程上的 ThreadLocal 残留污染下一个请求
        ModelOptionsHolder.set(options);
        try {
            return this.chatService.chat(chatDTO.getQuestion(), chatDTO.getSessionId());
        }
        finally {
            ModelOptionsHolder.clear();
        }
    }

    @PostMapping("/stop")
    public void stop(@RequestParam("sessionId") String sessionId) {
        AttachmentContextHolder.clear(sessionId);
        this.chatService.stop(sessionId);
    }

    @PostMapping("/text")
    public String chatText(@RequestBody String question) {
        return this.chatService.chatText(question);
    }

    @GetMapping("/templates")
    public TemplateVO getTemplates() {
        return TEMPLATE_VO;
    }

    /**
     * 返回可用的模型供应商、模型列表及默认配置
     */
    @GetMapping("/providers")
    public Map<String, Object> getProviders() {
        return Map.of(
                "defaultProvider", modelProviderProperties.getDefaultProvider(),
                "defaultTemperature", modelProviderProperties.getDefaultTemperature(),
                "providers", modelProviderProperties.getProviders()
        );
    }
}
