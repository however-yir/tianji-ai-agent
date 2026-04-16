package com.tianji.aigc.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDocument {

    private String attachmentId;
    private Long userId;
    private String fileName;
    private String contentType;
    private long size;
    private String extractedText;
    private List<AttachmentChunk> chunks;
    private LocalDateTime createdAt;
}
