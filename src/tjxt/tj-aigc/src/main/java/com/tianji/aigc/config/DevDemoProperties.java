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
     * 演示模式下的默认 token
     */
    private String token = "dev-demo-token";

    /**
     * 流式输出时的每段间隔，单位毫秒
     */
    private Long streamDelayMs = 45L;
}
