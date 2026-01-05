package com.deepknow.agent.sdk.model;

import com.deepknow.agent.sdk.model.mcp.McpServerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 智能体基因定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition implements Serializable {

    /**
     * Agent 名称（会话内唯一）
     */
    private String name;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * MCP 服务器配置映射
     * Key 为 Server 名称，Value 为连接配置
     */
    @Builder.Default
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();

    /**
     * 快捷添加 MCP Server
     */
    public AgentDefinition addMcpServer(String name, McpServerConfig config) {
        this.mcpServers.put(name, config);
        return this;
    }
}
