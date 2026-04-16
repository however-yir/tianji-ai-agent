package com.tianji.aigc.attachment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentContext {

    private List<String> attachmentIds;
    private List<String> attachmentNames;
    private List<AttachmentSource> sources;

    public boolean hasSources() {
        return CollUtil.isNotEmpty(sources);
    }

    public String toSystemPrompt() {
        if (!hasSources()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("以下是本轮对话可参考的附件资料，请优先依据这些内容回答。若资料不足以支撑结论，请明确说明。\n");
        for (AttachmentSource source : sources) {
            builder.append("- 附件《")
                    .append(source.getAttachmentName())
                    .append("》片段 ")
                    .append(source.getChunkIndex())
                    .append("：")
                    .append(StrUtil.blankToDefault(source.getExcerpt(), "暂无可引用片段"))
                    .append("\n");
        }
        builder.append("回答时请先给结论，再引用最相关的附件内容。\n");
        return builder.toString();
    }

    public Map<String, Object> toParamMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachmentIds", CollUtil.defaultIfEmpty(attachmentIds, List.of()));
        payload.put("attachmentNames", CollUtil.defaultIfEmpty(attachmentNames, List.of()));
        payload.put("attachmentCount", CollUtil.size(attachmentIds));
        payload.put("sources", CollUtil.defaultIfEmpty(sources, List.of()).stream()
                .map(source -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("attachmentId", source.getAttachmentId());
                    item.put("attachmentName", source.getAttachmentName());
                    item.put("chunkIndex", source.getChunkIndex());
                    item.put("score", source.getScore());
                    item.put("excerpt", source.getExcerpt());
                    return item;
                })
                .toList());
        return payload;
    }
}
