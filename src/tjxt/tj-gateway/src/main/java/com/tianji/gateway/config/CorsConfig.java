package com.tianji.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置 — 通过环境变量 CORS_ALLOWED_ORIGINS 驱动白名单。
 * allowCredentials=true 时禁止通配来源，防止浏览器静默拒绝。
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173}") String allowedOrigins,
            @Value("${CORS_ALLOWED_METHODS:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
            @Value("${CORS_ALLOWED_HEADERS:*}") String allowedHeaders,
            @Value("${CORS_MAX_AGE:3600}") long maxAge
    ) {
        CorsConfiguration config = new CorsConfiguration();

        List<String> originList = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        boolean hasWildcard = originList.contains("*");
        boolean allowCredentials = !hasWildcard;

        if (hasWildcard) {
            log.warn("CORS: 通配来源 '*' 与 allowCredentials 不兼容，已自动禁用凭据传递。"
                    + "请通过 CORS_ALLOWED_ORIGINS 环境变量设置显式白名单。");
        }

        originList.forEach(config::addAllowedOrigin);

        config.setAllowCredentials(allowCredentials);

        Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(config::addAllowedMethod);

        if ("*".equals(allowedHeaders)) {
            config.addAllowedHeader("*");
        } else {
            Arrays.stream(allowedHeaders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(config::addAllowedHeader);
        }

        config.setMaxAge(maxAge);

        log.info("CORS initialised: origins={} credentials={} maxAge={}", originList, allowCredentials, maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
