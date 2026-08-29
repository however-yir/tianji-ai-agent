package com.tianji.aigc.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Getter
@Configuration
@Profile("!dev-demo")
@RequiredArgsConstructor
public class SystemPromptConfig {

    private final NacosConfigManager nacosConfigManager;
    private final AIProperties aiProperties;
    private final com.tianji.aigc.prompt.PromptRegistry promptRegistry;

    // 使用原子引用，保证线程安全；初始值为兜底提示词，仅在成功读取配置后覆盖
    private final AtomicReference<String> chatSystemMessage = new AtomicReference<>("");
    private final AtomicReference<String> routeAgentSystemMessage = new AtomicReference<>("");
    private final AtomicReference<String> recommendAgentSystemMessage = new AtomicReference<>("");
    private final AtomicReference<String> buyAgentSystemMessage = new AtomicReference<>("");
    private final AtomicReference<String> consultAgentSystemMessage = new AtomicReference<>("");
    private final AtomicReference<String> knowledgeAgentSystemMessage = new AtomicReference<>("");
    private final AtomicReference<String> textSystemMessage = new AtomicReference<>("");

    @PostConstruct // 初始化时加载配置
    public void init() {
        // Repository prompts are the versioned baseline; Nacos is a controlled override.
        chatSystemMessage.set(promptRegistry.active("chat").orElseThrow().content());
        routeAgentSystemMessage.set(promptRegistry.active("route").orElseThrow().content());
        recommendAgentSystemMessage.set(promptRegistry.active("recommend").orElseThrow().content());
        buyAgentSystemMessage.set(promptRegistry.active("buy").orElseThrow().content());
        consultAgentSystemMessage.set(promptRegistry.active("consult").orElseThrow().content());
        knowledgeAgentSystemMessage.set(promptRegistry.active("knowledge").orElseThrow().content());
        textSystemMessage.set(promptRegistry.active("text").orElseThrow().content());
        loadConfig(aiProperties.getSystem().getChat(), chatSystemMessage);
        loadConfig(aiProperties.getSystem().getRouteAgent(), routeAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getRecommendAgent(), recommendAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getBuyAgent(), buyAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getConsultAgent(), consultAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getKnowledgeAgent(), knowledgeAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getText(), textSystemMessage);
    }

    private void loadConfig(AIProperties.System.Chat chatConfig, AtomicReference<String> target) {
        try {
            String dataId = chatConfig.getDataId();
            String group = chatConfig.getGroup();
            long timeoutMs = chatConfig.getTimeoutMs();

            // 这里是关键点，读取nacos配置中心中的文件内容
            String config = nacosConfigManager.getConfigService().getConfig(dataId, group, timeoutMs);
            applyConfig(target, config, dataId);

            // 下面就是实现热更新
            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String info) {
                    applyConfig(target, info, dataId);
                }
            });
        }
        catch (Exception e) {
            log.error("加载配置失败", e);
        }
    }

    /**
     * 仅在配置内容非空时覆盖兜底提示词，防止 Nacos 缺失/推送空配置时把 systemMessage 置空。
     */
    private void applyConfig(AtomicReference<String> target, String config, String dataId) {
        if (StringUtils.hasText(config)) {
            target.set(config);
            log.info("读取系统提示词成功，dataId：{}，内容为：{}", dataId, config);
        }
        else {
            log.warn("读取系统提示词为空，dataId：{}，保留兜底提示词", dataId);
        }
    }

}
