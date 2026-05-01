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
        // 系统提示词在每次请求中加入，chatHistory 只保存用户和助手的多轮消息。
    }

    public String chat(String userInput) {
        try {
            // 添加用户消息，保留多轮对话上下文。
            chatHistory.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                    .content(userInput)
                    .build()));

            // 构建请求。系统提示词负责稳定课程顾问角色，chatHistory 负责保存上下文。
            ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
                    .model("qwq-plus-latest")
                    .addSystemMessage(SYSTEM_PROMPT)
                    .messages(chatHistory)
                    .build();

            // 获取模型响应。
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

            // 教学样例中的业务处理：用本地方法模拟下单结果，正式项目应迁移到 Tool Calling。
            return processBusinessLogic(userInput, response);
        } catch (Exception e) {
            return "系统繁忙，请稍后再试";
        }
    }

    private String processBusinessLogic(String userInput, String response) {
        if (userInput.contains("立即下单") || userInput.contains("下单") || response.contains("支付链接")) {
            return generateOrder(response);
        }
        return response;
    }

    private String generateOrder(String response) {
        // 教学留白：这里故意保留本地模拟订单，真实业务请参考 tj-aigc 的 OrderTools.prePlaceOrder。
        String courseName = "JAVA开发零基础入门";
        String price = "0.01";
        String orderUrl = "https://pay.tianji.com/order/20240501";

        return String.format("""
                [模拟订单]
                课程名称：%s
                价格：￥%s
                支付链接：<a href="%s">%s</a>
                说明：这是 openai-java-demo 的教学模拟订单，真实预下单链路见 tj-aigc/OrderTools。
                """, courseName, price, orderUrl, orderUrl);
    }

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

