package com.tianji.aigc.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentUploadVO {

    private String attachmentId;
    private String name;
    private String contentType;
    private long size;
    private String previewText;
    private int chunkCount;
}
