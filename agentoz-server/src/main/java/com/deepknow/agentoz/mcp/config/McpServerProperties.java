package com.deepknow.agentoz.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP Server 配置属性
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /**
     * 是否启用 MCP Server
     */
    private boolean enabled = true;

    /**
     * 服务器名称
     */
    private String serverName = "agentoz";

    /**
     * 服务器版本
     */
    private String serverVersion = "1.0.0";

    /**
     * Streamable-HTTP 端点路径
     */
    private String httpEndpoint = "/mcp/agent";

    /**
     * SSE 端点路径
     */
    private String sseEndpoint = "/mcp/agent/sse";
}
