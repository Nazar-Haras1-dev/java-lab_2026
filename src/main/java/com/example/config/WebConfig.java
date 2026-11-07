package com.example.library.config;

import com.example.library.resolver.CurrentUsernameArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUsernameArgumentResolver currentUsernameResolver;

    public WebConfig(CurrentUsernameArgumentResolver currentUsernameResolver) {
        this.currentUsernameResolver = currentUsernameResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUsernameResolver);
    }
}