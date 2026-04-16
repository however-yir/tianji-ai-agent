package com.tianji.aigc.config;

import com.tianji.common.utils.TokenContext;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("dev-demo")
@RequiredArgsConstructor
public class DevDemoUserContextConfiguration implements WebMvcConfigurer {

    private final DevDemoProperties devDemoProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(devDemoUserInterceptor()).order(100);
    }

    private HandlerInterceptor devDemoUserInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     Object handler) {
                if (UserContext.getUser() == null) {
                    UserContext.setUser(devDemoProperties.getUserId());
                }
                if (TokenContext.getToken() == null) {
                    TokenContext.setToken(devDemoProperties.getToken());
                }
                return true;
            }
        };
    }
}
