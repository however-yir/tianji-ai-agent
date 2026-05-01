package cn.itcast.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class StructuredOutputTest {

    private ChatClient chatClient;
    @Resource
    private ChatModel chatModel;

    @BeforeEach
    public void before() {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    // 创建一个记录器，用于记录演员和电影列表
    record ActorsFilms(String actor, List<String> movies) { }

    @Test
    public void testBeanOut(){
        ActorsFilms actorsFilms = this.chatClient.prompt()
                .user("生成5部成龙的电影目录")
                .call()
                .entity(ActorsFilms.class);
        for (String movie : actorsFilms.movies()) {
            System.out.println(StrUtil.format("{}, {}", actorsFilms.actor(), movie));
        }
    }


    @Test
    public void testListBeanOut(){
        List<ActorsFilms> list = this.chatClient.prompt()
                .user("生成5部成龙和刘德华的电影目录")
                .call()
                .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
        for (ActorsFilms actorsFilms : list) {
            for (String movie : actorsFilms.movies()) {
                System.out.println(StrUtil.format("{}, {}", actorsFilms.actor(), movie));
            }
        }
    }

    @Test
    public void testMapOut(){
        Map<String,Object> map = this.chatClient.prompt()
                .user("生成一个名为'生肖表'的中国十二生肖列表，一个名为'星座表'的星座列表")
                .call()
                .entity(new ParameterizedTypeReference<Map<String,Object>>() {});
        System.out.println(map);
    }

    @Test
    public void testListOut(){
        List<String> list = this.chatClient.prompt()
                .user("生成一个中国十二生肖列表")
                .call()
                .entity(new ParameterizedTypeReference<>() {});
        System.out.println(list);
    }

    @Test
    public void testJsonOut() {
        String content = "请以 JSON 格式返回以下信息：生成一个名为'生肖表'的中国十二生肖列表，一个名为'星座表'的星座列表";
        Prompt prompt = new Prompt(content, DashScopeChatOptions.builder()
                .withResponseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.JSON_OBJECT) // 设置返回格式为JSON对象
                        .build())
                .build());

        String json = this.chatClient.prompt(prompt)
                .call()
                .content();
        System.out.println(json);
    }
}
