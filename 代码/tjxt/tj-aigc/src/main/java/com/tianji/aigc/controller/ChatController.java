package com.tianji.aigc.controller;

import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.attachment.AttachmentContextHolder;
import com.tianji.aigc.dto.ChatDTO;
import com.tianji.aigc.service.AttachmentService;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.aigc.vo.TemplateVO;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private static final TemplateVO TEMPLATE_VO = new TemplateVO();
    @NoWrapper
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        AttachmentContext context = attachmentService.buildContext(chatDTO.getAttachmentIds(), chatDTO.getQuestion());
        AttachmentContextHolder.put(chatDTO.getSessionId(), context);
        return this.chatService.chat(chatDTO.getQuestion(), chatDTO.getSessionId());
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
}
