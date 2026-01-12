package com.deepknow.agentoz.starter.annotation;

import java.lang.annotation.*;

/**
 * 标记 Agent 工具的参数信息
 *
 * @author Agent Platform
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentParam {

    /**
     * 参数描述，用于生成 JSON Schema description
     */
    String value();

    /**
     * 参数名称，默认为形参名 (需要开启 -parameters 编译选项)
     */
    String name() default "";

    /**
     * 是否必填
     */
    boolean required() default true;
}
