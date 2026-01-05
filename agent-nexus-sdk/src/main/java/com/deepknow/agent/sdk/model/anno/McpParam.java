package com.deepknow.agent.sdk.model.anno;

import java.lang.annotation.*;

/**
 * 标记 MCP 工具的参数描述
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpParam {
    /**
     * 参数含义描述
     */
    String value();

    /**
     * 是否必填
     */
    boolean required() default true;
}
