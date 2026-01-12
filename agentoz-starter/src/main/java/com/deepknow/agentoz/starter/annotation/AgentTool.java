package com.deepknow.agentoz.starter.annotation;

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
     * 工具名称，默认为方法名
     */
    String name() default "";

    /**
     * 工具描述 (Prompt)，用于告诉 LLM 如何使用此工具
     */
    String description() default "";
}
