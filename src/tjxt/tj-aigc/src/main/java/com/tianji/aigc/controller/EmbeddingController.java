package com.tianji.aigc.controller;

import cn.hutool.core.collection.CollStreamUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 向量库写入接口。
 * <p>
 * 安全说明：写入的是全局共享向量库，会影响所有用户的 RAG 检索结果；
 * 任意登录用户均可写入会造成知识库投毒，因此默认关闭（tj.aigc.embedding-write.enabled=false），
 * 仅运维/管理员在受控环境显式开启后使用。
 */
@Slf4j
@RestController
@Profile("!dev-demo")
@ConditionalOnProperty(prefix = "tj.aigc.embedding-write", name = "enabled", havingValue = "true")
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final VectorStore vectorStore;

    @PostMapping
    public void saveVectorStore(@RequestParam("messages") List<String> messages) {
        log.info("保存到向量数据库中，消息数据：{}", messages);
        //构建文档
        List<Document> documents = CollStreamUtil.toList(messages, message -> Document.builder()
                .text(message)
                .build());
        //存储到向量数据库中
        this.vectorStore.add(documents);
        log.info("保存到向量数据库成功, 数量：{}", messages.size());
    }

}
