package cn.itcast.openai;


import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import java.util.ArrayList;
import java.util.List;

public class AICourseAssistant {


    private final OpenAIClient client;
    private final List<ChatCompletionMessageParam> chatHistory = new ArrayList<>();

    // 系统提示词（Prompt Engineering）
    private static final String SYSTEM_PROMPT = """
            你是一个专业的课程推荐助手，请按以下步骤工作：
            1. 收集用户信息：年龄、学历、编程基础、兴趣方向
            2. 根据信息推荐最合适的3门课程
            3. 当用户说"立即下单"时生成订单
                    
            回复格式要求：
            - 信息收集阶段用"请告诉我您的xx"
            - 推荐时显示课程名称、价格、适用人群
            - 订单包含课程名称、价格、支付链接
            """;

    public AICourseAssistant(String apiKey) {
        // 配置客户端（使用课程中提到的代理地址）
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                .build();
        // 初始化系统消息
        // ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
        //         .model("qwq-plus-latest")
        //         .addSystemMessage(SYSTEM_PROMPT)
        //         .build();
    }

    public String chat(String userInput) {
        try {
            // TODO 添加用户消息
            chatHistory.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                    .content(userInput)
                    .build()));

            // TODO 构建请求
            ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
                    .model("qwq-plus-latest")
                    .messages(chatHistory)
                    .build();

            // TODO 获取响应
            //String assistantResponse = "";
            var response = client.chat().completions()
                    .create(createParams)
                    .choices()
                    .get(0)
                    .message()
                    .content()
                    .orElse("没有收到回复");

            // 将助手回复添加到历史记录
            chatHistory.add(ChatCompletionMessageParam.ofAssistant(
                    ChatCompletionAssistantMessageParam.builder()
                            .content(response)
                            .build()
            ));

            // TODO 业务逻辑处理
            return processBusinessLogic(response);
        } catch (Exception e) {
            return "系统繁忙，请稍后再试";
        }
    }

    private String processBusinessLogic(String response) {
        if (response.contains("立即下单")) {
            return generateOrder(response);
        }
        return response;
    }

    private String generateOrder(String response) {
        // TODO 模拟订单生成逻辑
        // 模拟从上下文中提取课程信息
        String courseName = "JAVA开发零基础入门";
        String price = "0.01";
        String orderUrl = "https://pay.tianji.com/order/20240501";

        return String.format("""
                [模拟订单]
                课程名称：%s
                价格：￥%s
                支付链接：<a href="%s">%s</a>
                """, courseName, price, orderUrl, orderUrl);
    }
    // return """
    //         [模拟订单]
    //         课程名称：JAVA开发零基础入门
    //         价格：￥0.01
    //         支付链接：https://pay.tianji.com/order/20240501
    //         """;

    public static void main(String[] args) {
        // 从环境变量获取API Key（遵循课程中的安全规范）
        String apiKey = System.getenv("AI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("ALIYUN_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 AI_API_KEY 或 ALIYUN_API_KEY");
        }
        AICourseAssistant assistant = new AICourseAssistant(apiKey);

        // 测试对话流程
        String[] testDialogue = {
                "你好，我想学习编程",
                "20岁，本科学历，没有编程基础",
                "对JAVA感兴趣",
                "立即下单"
        };

        for (String input : testDialogue) {
            System.out.println("用户：" + input);
            String response = assistant.chat(input);
            System.out.println("助手：" + response + "\n");
        }
    }

}

