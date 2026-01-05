package com.deepknow.agent.sdk.model.anno;

import java.lang.annotation.*;

/**
 * 标记一个方法为 MCP 工具
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
    /**
     * 工具名称（AI 看到的名称），建议使用英文下划线格式
     */
    String name();

    /**
     * 工具功能详细描述，直接影响 AI 调用的准确度
     */
    String description();
}
