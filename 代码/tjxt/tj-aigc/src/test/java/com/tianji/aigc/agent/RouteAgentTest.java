package com.tianji.aigc.agent;

import cn.hutool.core.lang.Assert;
import com.tianji.aigc.enums.AgentTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RouteAgentTest {

    @Resource
    private RouteAgent routeAgent;

    @Test
    public void testChat(){
        System.out.println(this.routeAgent.process("你好", "1"));
        System.out.println(this.routeAgent.process("最新有哪些课程", "1"));
        System.out.println(this.routeAgent.process("下单购买这个课程", "1"));
        System.out.println(this.routeAgent.process("这个课程是多少钱", "1"));
        System.out.println(this.routeAgent.process("java是什么", "1"));
        // Assert.equals(this.routeAgent.process("最新有哪些课程", "1"), AgentTypeEnum.RECOMMEND.getAgentName());
        // Assert.equals(this.routeAgent.process("下单购买这个课程", "1"), AgentTypeEnum.BUY.getAgentName());
        // Assert.equals(this.routeAgent.process("这个课程是多少钱", "1"), AgentTypeEnum.CONSULT.getAgentName());
        // Assert.equals(this.routeAgent.process("java是什么", "1"), AgentTypeEnum.KNOWLEDGE.getAgentName());
    }

}
