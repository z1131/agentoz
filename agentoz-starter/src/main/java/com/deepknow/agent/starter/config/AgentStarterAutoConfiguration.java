package com.deepknow.agent.starter.config;

import com.deepknow.agent.api.service.AgentService;
import com.deepknow.agent.starter.core.AgentContext;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Starter 自动配置
 */
@Configuration
public class AgentStarterAutoConfiguration {

    /**
     * 这里的 check=false 非常重要
     * 允许客户端启动时服务端还没准备好，防止启动报错
     */
    @DubboReference(check = false)
    private AgentService agentService;

    /**
     * 注册静态上下文持有者，并注入 Dubbo 服务
     * 这样 AgentContext.getAgentService() 就能拿到了
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentContext agentContext() {
        // 由于 AgentContext 实现了 ApplicationContextAware，Spring 会自动注入 context
        // 但我们需要手动把 agentService 塞进去吗？
        // AgentContext 的 getAgentService 方法是去 context.getBean(AgentService.class)
        // 所以我们得确保 agentService 是个 Bean
        return new AgentContext();
    }
    


    /**
     * 注册工具扫描器
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentToolScanner agentToolScanner() {
        return new AgentToolScanner();
    }
}
