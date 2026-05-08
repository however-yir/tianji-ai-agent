package com.tianji.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Data
@Configuration
@Profile("dev-demo")
@ConfigurationProperties(prefix = "tj.ai.dev-demo")
public class DevDemoProperties {

    /**
     * dev-demo 模式下使用的固定用户
     */
    private Long userId = 10001L;

    /**
     * 演示模式下的默认 token。
     * 必须通过环境变量 AIGC_DEMO_TOKEN 注入，禁止使用硬编码默认值。
     */
    private String token;

    public String getToken() {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "AIGC_DEMO_TOKEN 未配置，请通过环境变量或 tj.ai.dev-demo.token 注入演示 token。");
        }
        return token;
    }

    /**
     * 流式输出时的每段间隔，单位毫秒
     */
    private Long streamDelayMs = 45L;
}
