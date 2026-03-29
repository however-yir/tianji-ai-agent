package cn.itcast.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * @author zzj
 * @version 1.0
 */
public class CompletionsStreamingDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ALIYUN_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 ALIYUN_API_KEY");
        }

        //1. 创建客户端
        var client = OpenAIOkHttpClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                // .baseUrl("https://api.chatanywhere.tech/v1")
                // .apiKey(System.getenv("OPENAI_API_KEY")) // 不是必须的，你可以写死在代码中，前提：你的代码不共享给别人
                .build();

        //2. 构造请求参数
        var createParams = ChatCompletionCreateParams.builder()
                // .model(ChatModel.GPT_3_5_TURBO)
                .model("qwen-plus-2025-01-25")
                .addSystemMessage("你是一个java开发助手，名字叫作：小美")
                .addUserMessage("写一个java的入门示例代码")
                .build();

        //3. 通过客户端，发起请求
        try (var response = client.chat().completions().createStreaming(createParams)) {
            response.stream()
                    .flatMap(chunk -> chunk.choices().stream())
                    .flatMap(choice -> choice.delta().content().stream())
                    .forEach(System.out::print);
        }

    }
}
