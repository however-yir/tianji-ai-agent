package com.tianji.aigc.controller;

import com.tianji.aigc.config.ModelProviderProperties;
import com.tianji.aigc.dto.ChatDTO;
import com.tianji.aigc.service.AttachmentService;
import com.tianji.aigc.service.ChatService;
import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * API contract: blank question must fail fast with a 400-class exception instead of
 * reaching the model pipeline (which would NPE or call the provider with null input).
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerContractTest {

    @Mock
    private AttachmentService attachmentService;
    @Mock
    private ChatService chatService;
    @Mock
    private ModelProviderProperties modelProviderProperties;

    @Test
    void shouldRejectBlankQuestionWithBadRequest() {
        ChatController controller = new ChatController(attachmentService, chatService, modelProviderProperties);
        ChatDTO dto = new ChatDTO();
        dto.setQuestion("   ");

        assertThatThrownBy(() -> controller.chat(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("问题内容不能为空");
        verifyNoInteractions(chatService);
    }
}
