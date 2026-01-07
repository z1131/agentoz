package com.deepknow.agent.starter.core;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 静态上下文持有者
 * 允许在非 Spring 管理的对象 (如 new Agent()) 中获取 Spring Bean
 */
public class AgentContext implements ApplicationContextAware {

    private static ApplicationContext context;
    private static AgentService agentService;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        AgentContext.context = applicationContext;
    }

    /**
     * 获取 AgentService (Dubbo 引用)
     * 只有在 Spring 容器完全启动，且 Dubbo 服务已注入后才可用
     */
    public static AgentService getAgentService() {
        if (agentService == null) {
            if (context == null) {
                throw new IllegalStateException("AgentContext 尚未初始化，请确保在 Spring Boot 启动后调用");
            }
            // 尝试获取 Dubbo 注入的 AgentService
            // 注意：这需要在 AutoConfiguration 中预先 @DubboReference 注入一个 Bean，然后在这里拿
            // 为了简单，我们假设 AutoConfig 已经把它注册到了 Spring 容器中
            try {
                agentService = context.getBean(AgentService.class);
            } catch (Exception e) {
                throw new IllegalStateException("无法获取 AgentService Bean，请检查 Dubbo 连接配置", e);
            }
        }
        return agentService;
    }
}
