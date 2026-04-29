package com.tianji.aigc.attachment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentContextHolderTest {

    @AfterEach
    void tearDown() {
        AttachmentContextHolder.clear("session-1");
        AttachmentContextHolder.clear("session-empty");
    }

    @Test
    void shouldStorePeekAndTakeContextWithSources() {
        AttachmentContext context = AttachmentContext.builder()
                .attachmentIds(List.of("att-1"))
                .attachmentNames(List.of("brief.txt"))
                .sources(List.of(AttachmentSource.builder()
                        .attachmentId("att-1")
                        .attachmentName("brief.txt")
                        .chunkIndex(1)
                        .score(1.0)
                        .excerpt("课程推荐需要先识别用户目标")
                        .build()))
                .build();

        AttachmentContextHolder.put("session-1", context);

        assertThat(AttachmentContextHolder.peek("session-1")).isSameAs(context);
        assertThat(AttachmentContextHolder.take("session-1")).isSameAs(context);
        assertThat(AttachmentContextHolder.peek("session-1")).isNull();
    }

    @Test
    void shouldIgnoreContextWithoutSources() {
        AttachmentContext context = AttachmentContext.builder()
                .attachmentIds(List.of("att-1"))
                .attachmentNames(List.of("brief.txt"))
                .sources(List.of())
                .build();

        AttachmentContextHolder.put("session-empty", context);

        assertThat(AttachmentContextHolder.peek("session-empty")).isNull();
    }
}
