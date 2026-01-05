package com.deepknow.agent.sdk.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * MCP 服务器连接配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig implements Serializable {
    
    /**
     * 连接类型：stdio, streamable_http
     */
    private String type;

    /**
     * 对于 http 类型，指定 MCP Server 的 SSE 接口地址
     */
    private String url;

    /**
     * 对于 stdio 类型，指定运行命令
     */
    private String command;

    /**
     * 环境变量 Key，用于存放认证 Token
     */
    private String bearerTokenEnvVar;

    /**
     * 自定义 HTTP Header
     */
    private Map<String, String> httpHeaders;
}
