package com.deepknow.agentoz.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "agentoz.mcp")
public class McpProperties {
    /**
     * 是否启用 MCP Server 功能
     */
    private boolean enabled = true;

    /**
     * MCP 服务器名称
     */
    private String serverName = "agentoz-mcp-server";

    /**
     * MCP 服务器版本
     */
    private String serverVersion = "1.0.0";

    /**
     * HTTP 暴露端点
     */
    private String httpEndpoint = "/mcp/message";
}
