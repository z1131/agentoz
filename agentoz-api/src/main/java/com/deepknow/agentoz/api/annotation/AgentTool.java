package com.deepknow.agentoz.api.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 Agent 可用的工具 (MCP Tool)
 *
 * @author Agent Platform
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentTool {

    /**
     * 工具名称，默认使用方法名
     */
    String name() default "";

    /**
     * 工具描述，用于告诉 LLM 这个工具是干什么的
     */
    String description() default "";
}
