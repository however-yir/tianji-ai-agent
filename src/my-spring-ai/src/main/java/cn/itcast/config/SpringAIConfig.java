package cn.itcast.config;

import cn.itcast.constants.Constant;
import cn.itcast.tools.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SpringAIConfig {

    /**
     * 构造一个ChatClient对象，放到Spring容器中
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 Advisor simpleLoggerAdvisor,
                                 Advisor promptChatMemoryAdvisor,
                                 Advisor safeGuardAdvisor,
                                 WeatherTools weatherTools
    ) {
        return chatClientBuilder
                // .defaultSystem(Constant.SYSTEM_ROLE) //默认设置
                // .defaultAdvisors(simpleLoggerAdvisor, promptChatMemoryAdvisor, safeGuardAdvisor) // 默认的增强器
                // .defaultTools(weatherTools) //注册默认的工具
                // .defaultTools("getToutiaoNewsFunction")
                .build();
    }

    /**
     * 基于内存实现的聊天记录存储器
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * 构造一个会话记忆增强器，放到Spring容器中
     */
    // @Bean
    // public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
    //     return new MessageChatMemoryAdvisor(chatMemory);
    // }

    @Bean
    public Advisor promptChatMemoryAdvisor(ChatMemory chatMemory) {
        return new PromptChatMemoryAdvisor(chatMemory);
    }

    /**
     * 日志增强，打印请求和响应日志信息
     */
    @Bean
    public Advisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public Advisor safeGuardAdvisor() {
        // 敏感词列表（示例数据，建议实际使用时从配置文件或数据库读取）
        List<String> sensitiveWords = List.of("敏感词1", "敏感词2");
        // 创建安全防护Advisor，参数依次为：敏感词库、违规提示语、advisor处理优先级，数字越小越优先
        return new SafeGuardAdvisor(
                sensitiveWords,
                "敏感词提示：请勿输入敏感词！",
                Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER
        );
    }

    /**
     * 创建并返回一个VectorStore的Spring Bean实例。
     *
     * @param embeddingModel 向量模型
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
