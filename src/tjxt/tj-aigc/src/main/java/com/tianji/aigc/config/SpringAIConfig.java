package com.tianji.aigc.config;

import com.tianji.aigc.memory.RedisChatMemory;
import com.tianji.common.constants.Constant;
import com.tianji.common.utils.WebUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@Profile("!dev-demo")
public class SpringAIConfig {

    /**
     * 创建并配置自定义重试监听器Bean
     * <p>
     * 实现说明：
     * 1. 创建匿名RetryListener实现，在重试操作期间管理Web属性
     * 2. 将监听器注册到提供的RetryTemplate实例
     *
     * @param retryTemplate Spring Retry模板对象，用于注册重试监听器
     * @return RetryListener 已注册到模板的重试监听器实例，将由Spring容器管理
     */
    @Bean
    public RetryListener customizeRetryTemplate(RetryTemplate retryTemplate) {
        // 创建自定义重试监听器，实现以下核心功能：
        // - 重试开始时设置上下文标识
        // - 重试结束后清理上下文标识
        RetryListener retryListener = new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
                WebUtils.setAttribute(Constant.SPRING_AI_ATTR, Constant.SPRING_AI_FLAG);
                return true;
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                WebUtils.removeAttribute(Constant.SPRING_AI_ATTR);
            }
        };

        // 将监听器注册到重试模板
        retryTemplate.registerListener(retryListener);
        return retryListener;
    }

    /**
     * 配置 ChatClient。
     * <p>
     * 通过 ObjectProvider 按名称懒取 builder：local profile 默认关闭 openai（spring.ai.openai.chat.enabled=false）
     * 时 openAiChatClientBuilder 不存在，直接按限定符注入会导致整个上下文启动失败。
     * 这里回退到另一个已启用的供应商 builder，保证至少一个 provider 开启即可启动；
     * 两个都未启用则快速失败并给出明确原因。
     */
    @Bean
    public ChatClient dashScopeChatClient(@Qualifier("dashScopeChatClientBuilder") ObjectProvider<ChatClient.Builder> dashScopeBuilder,
                                          @Qualifier("openAiChatClientBuilder") ObjectProvider<ChatClient.Builder> openAiBuilder,
                                          Advisor loggerAdvisor // 日志记录器
    ) {
        return resolveBuilder(dashScopeBuilder, openAiBuilder)
                .defaultAdvisors(loggerAdvisor)
                .build();
    }

    @Bean
    public ChatClient openAiChatClient(@Qualifier("openAiChatClientBuilder") ObjectProvider<ChatClient.Builder> openAiBuilder,
                                       @Qualifier("dashScopeChatClientBuilder") ObjectProvider<ChatClient.Builder> dashScopeBuilder,
                                       Advisor loggerAdvisor  // 日志记录器
    ) {
        return resolveBuilder(openAiBuilder, dashScopeBuilder)
                .defaultAdvisors(loggerAdvisor)
                .build();
    }

    private static ChatClient.Builder resolveBuilder(ObjectProvider<ChatClient.Builder> primary,
                                                     ObjectProvider<ChatClient.Builder> fallback) {
        ChatClient.Builder builder = primary.getIfAvailable();
        if (builder == null) {
            builder = fallback.getIfAvailable();
        }
        if (builder == null) {
            throw new IllegalStateException(
                    "未找到可用的 ChatClient.Builder：请至少开启 spring.ai.dashscope.chat.enabled 或 spring.ai.openai.chat.enabled");
        }
        return builder;
    }

    /**
     * 日志记录器
     */
    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public RedisChatMemory redisChatMemory(StringRedisTemplate stringRedisTemplate) {
        return new RedisChatMemory(stringRedisTemplate);
    }

    @Bean
    public Advisor messageChatMemoryAdvisor(RedisChatMemory redisChatMemory) {
        return new MessageChatMemoryAdvisor(redisChatMemory);
    }
}
