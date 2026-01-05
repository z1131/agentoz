package com.deepknow.agent.sdk.core.config;

import com.deepknow.agent.sdk.core.client.AgentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * SDK 自动配置类
 *
 * @author Agent Platform
 * @version 1.0.0
 */
@Slf4j
@org.springframework.context.annotation.Configuration
public class AgentSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentClient agentClient() {
        log.info("初始化 Agent SDK 客户端");
        return AgentClient.getInstance();
    }
}
