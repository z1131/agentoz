package com.deepknow.agent.sdk.model.anno;

import java.lang.annotation.*;

/**
 * 标记一个类为 MCP 工具提供者
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpService {
    /**
     * 业务领域名称，如果不填则使用类名
     */
    String value() default "";
    
    /**
     * 服务描述
     */
    String description() default "";
}
