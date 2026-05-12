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

    /**
     * 模型供应商：dashscope / openai
     */
    private String provider;

    /**
     * 模型名称，如 qwen-plus / gpt-4o
     */
    private String model;

    /**
     * 温度参数，范围 0.0 ~ 2.0，控制输出随机性
     */
    private Double temperature;
}
