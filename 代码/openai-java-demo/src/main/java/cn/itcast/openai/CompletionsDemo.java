package cn.itcast.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * @author zzj
 * @version 1.0
 */
public class CompletionsDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ALIYUN_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 ALIYUN_API_KEY");
        }

        //1. 创建客户端
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                // .baseUrl("https://api.chatanywhere.tech/v1")
                // .apiKey(System.getenv("OPENAI_API_KEY")) // 不是必须的，你可以写死在代码中，前提：你的代码不共享给别人
                .build();

        //2. 构造请求参数
        ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
                // .model(ChatModel.GPT_3_5_TURBO)
                .model("qwen-plus-2025-01-25")
                .addSystemMessage("你是一个java开发助手，名字叫作：小美")
                .addUserMessage("你是谁？")
                .build();

        //3. 通过客户端，发起请求
        client.chat().completions()
                .create(createParams)
                .choices()
                .stream()
                .flatMap(choice -> choice.message().content().stream())
                .forEach(System.out::println);
    }

}
