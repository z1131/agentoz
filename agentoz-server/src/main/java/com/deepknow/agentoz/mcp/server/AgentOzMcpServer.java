package com.deepknow.agentoz.mcp.server;

import com.deepknow.agentoz.mcp.config.McpServerProperties;
import com.deepknow.agentoz.mcp.tool.CallAgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AgentOZ MCP Server 核心类
 *
 * <p>基于 MCP Java SDK 实现的标准 MCP Server，支持 Streamable-HTTP 传输。
 * 提供 Agent 间相互调用的工具能力。</p>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentOzMcpServer {

    @Autowired
    private McpServerProperties properties;

    @Autowired
    private CallAgentTool callAgentTool;

    @Autowired
    private ObjectMapper objectMapper;

    private io.modelcontextprotocol.server.McpStatelessSyncServer mcpServer;

    /**
     * 初始化 MCP Server
     */
    @PostConstruct
    public void initialize() {
        if (!properties.isEnabled()) {
            log.info("MCP Server 已禁用，跳过初始化");
            return;
        }

        try {
            log.info(">>> 初始化 AgentOZ MCP Server: {} v{}",
                    properties.getServerName(),
                    properties.getServerVersion());

            // 创建传输层（使用 Spring WebMVC 专用传输）
            WebMvcStatelessServerTransport transport =
                    WebMvcStatelessServerTransport.builder()
                            .messageEndpoint(properties.getHttpEndpoint())
                            .objectMapper(objectMapper)  // ✅ 必须设置 ObjectMapper
                            .build();

            // 创建工具规范
            SyncToolSpecification callAgentToolSpec = new SyncToolSpecification(
                    callAgentTool.getToolDefinition(),
                    callAgentTool::execute
            );

            // 构建 MCP Server
            this.mcpServer = McpServer.sync(transport)
                    .serverInfo(properties.getServerName(), properties.getServerVersion())
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .build())
                    .tools(callAgentToolSpec)
                    .build();

            log.info("✅ MCP Server 初始化成功");
            log.info("   - 端点: {} (HTTP)", properties.getHttpEndpoint());

        } catch (Exception e) {
            log.error("❌ MCP Server 初始化失败", e);
            throw new RuntimeException("Failed to initialize MCP Server", e);
        }
    }

    /**
     * 获取 MCP Server 实例
     *
     * @return MCP Server 实例
     */
    public io.modelcontextprotocol.server.McpStatelessSyncServer getMcpServer() {
        if (mcpServer == null) {
            throw new IllegalStateException("MCP Server 未初始化");
        }
        return mcpServer;
    }

    /**
     * 销毁 MCP Server
     */
    @PreDestroy
    public void destroy() {
        if (mcpServer != null) {
            log.info("关闭 MCP Server...");
            mcpServer.close();
            log.info("MCP Server 已关闭");
        }
    }
}
