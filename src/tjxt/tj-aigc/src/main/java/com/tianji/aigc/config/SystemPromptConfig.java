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

    /**
     * Nacos 配置缺失/不可用时的兜底提示词，避免 systemMessage() 返回 null 导致请求链路 NPE。
     */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是天机学堂的AI学习助手，请用中文、友好且专业地回答用户的问题；无法确定的内容请如实说明。";

    private final NacosConfigManager nacosConfigManager;
    private final AIProperties aiProperties;

    // 使用原子引用，保证线程安全；初始值为兜底提示词，仅在成功读取配置后覆盖
    private final AtomicReference<String> chatSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);
    private final AtomicReference<String> routeAgentSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);
    private final AtomicReference<String> recommendAgentSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);
    private final AtomicReference<String> buyAgentSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);
    private final AtomicReference<String> consultAgentSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);
    private final AtomicReference<String> knowledgeAgentSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);
    private final AtomicReference<String> textSystemMessage = new AtomicReference<>(DEFAULT_SYSTEM_PROMPT);

    @PostConstruct // 初始化时加载配置
    public void init() {
        // 读取配置文件
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
