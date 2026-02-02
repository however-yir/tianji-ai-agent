package cn.itcast.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatServiceTest {

    @Resource
    private ChatService chatService;

    @Test
    void chat() {
        System.out.println(this.chatService.chat("你是谁？", "123"));
        System.out.println("------------------------");
        System.out.println(this.chatService.chat("给我讲个笑话", "123"));
    }
}
