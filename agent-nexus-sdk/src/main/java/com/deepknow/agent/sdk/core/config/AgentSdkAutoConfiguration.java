package com.deepknow.agent.sdk.core.config;

import com.deepknow.agent.sdk.core.client.AgentClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDK 自动配置类
 *
 * @author Agent Platform
 * @version 1.0.0
 */
@org.springframework.context.annotation.Configuration
public class AgentSdkAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentSdkAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AgentClient agentClient() {
        log.info("初始化 Agent SDK 客户端");
        return AgentClient.getInstance();
    }
}
