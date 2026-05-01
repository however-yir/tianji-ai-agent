package com.tianji.aigc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDTO {

    /**
     * 用户的问题
     */
    private String question;
    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 已上传附件id列表
     */
    private List<String> attachmentIds;
}
