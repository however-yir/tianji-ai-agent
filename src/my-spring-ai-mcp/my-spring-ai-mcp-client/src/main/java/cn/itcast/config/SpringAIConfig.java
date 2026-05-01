package cn.itcast.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAIConfig {

    private static final String SYSTEM_PROMPT = """
            你是一个全能助手，可以帮我解决各种问题。
            """;

    /**
     * 创建并返回一个ChatClient的Spring Bean实例。
     *
     * @param builder 用于构建ChatClient实例的构建者对象
     * @return 构建好的ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ToolCallbackProvider tools
    ) {
        return builder
                .defaultSystem(SYSTEM_PROMPT) // 设置默认的系统角色
                .defaultTools(tools)
                .build();
    }

}
