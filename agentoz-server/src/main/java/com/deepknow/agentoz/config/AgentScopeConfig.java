package com.deepknow.agentoz.config;

import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentScopeConfig {

    @Value("${agentscope.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${agentscope.dashscope.model-name:qwen-max}")
    private String defaultModelName;

    @Bean
    public DashScopeChatModel defaultChatModel() {
        return DashScopeChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(defaultModelName)
                .build();
    }
}
