package com.tianji.aigc.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentSource {

    private String attachmentId;
    private String attachmentName;
    private int chunkIndex;
    private double score;
    private String excerpt;
}
