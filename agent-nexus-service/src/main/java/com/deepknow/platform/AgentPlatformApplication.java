package com.deepknow.platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能体平台应用启动类
 *
 * <p>唯一部署的智能体微服务，提供：
 * - 智能体执行服务 (AgentService)
 * - 会话管理服务 (SessionService)
 * - 上下文管理服务 (ContextService)
 *
 * @author Agent Platform
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDubbo
@MapperScan("com.deepknow.platform.repository")
public class AgentPlatformApplication {
    private static final Logger log = LoggerFactory.getLogger(AgentPlatformApplication.class);


    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
