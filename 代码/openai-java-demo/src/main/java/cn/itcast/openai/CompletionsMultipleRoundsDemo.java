package cn.itcast.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CompletionsMultipleRoundsDemo {

    private static OpenAIClient client;

    public static void main(String[] args) {
        String apiKey = System.getenv("ALIYUN_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 ALIYUN_API_KEY");
        }

        // 创建客户端，指定 API Key 与 baseUrl，其中API KEY从系统环境变量中获取
        client = OpenAIOkHttpClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                .build();

        // 存放聊天记录
        var messageParamsList = new ArrayList<ChatCompletionMessageParam>();

        // 第一次对话
        chat("我叫花和尚，请记住我", messageParamsList);
        System.out.println("------------------------");
        // 第二次对话
        chat("我是谁？", messageParamsList);
    }

    public static void chat(String userMessage, List<ChatCompletionMessageParam> messageParamsList) {
        // 加入此次用户消息
        messageParamsList.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                .content(userMessage)
                .build()));

        // 构造聊天参数 无状态性
        var createParams = ChatCompletionCreateParams.builder()
                .model("qwen-plus-2025-01-25")// 指定模型
                // .addUserMessage(userMessage) // 添加用户消息
                .messages(messageParamsList) // 消息列表
                .build();

        // 调用接口，获取结果并打印
        client.chat().completions()
                .create(createParams)
                .choices()
                .stream()
                .flatMap(choice -> {
                    Optional<String> contentOptional = choice.message().content();
                    if(contentOptional.isPresent()){
                        // 大模型生成的内容消息
                        var assistantMessageParam = ChatCompletionAssistantMessageParam.builder()
                                .content(contentOptional.get())
                                .build();
                        messageParamsList.add(ChatCompletionMessageParam.ofAssistant(assistantMessageParam));
                    }
                    return contentOptional.stream();
                })
                .forEach(System.out::println);
    }

}
