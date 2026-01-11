package com.deepknow.agentoz.mcp.config;

import com.deepknow.agentoz.mcp.tool.CallAgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * MCP Server 配置类
 *
 * <p>按照官方 MCP Java SDK 文档标准实现：
 * https://modelcontextprotocol.io/sdk/java/mcp-server</p>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class McpServerConfig {

    /**
     * 创建 WebMvc Stateless Server Transport
     *
     * <p>提供基于 Spring WebMVC 的 Streamable-HTTP 无状态传输实现。</p>
     */
    @Bean
    public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            ObjectMapper objectMapper,
            McpServerProperties properties) {

        log.info(">>> 创建 WebMvcStatelessServerTransport");
        log.info("   - 端点: {}", properties.getHttpEndpoint());

        return WebMvcStatelessServerTransport.builder()
                .objectMapper(objectMapper)
                .messageEndpoint(properties.getHttpEndpoint())
                .build();
    }

    /**
     * 暴露 RouterFunction Bean
     *
     * <p>这是关键！必须将传输层的路由函数注册为 Spring Bean，
     * 否则 MCP 端点无法被访问。</p>
     */
    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcStatelessServerTransport transport) {

        log.info(">>> 注册 MCP 路由函数");

        RouterFunction<ServerResponse> routerFunction = transport.getRouterFunction();
        log.info("✅ MCP 路由函数注册成功");

        return routerFunction;
    }

    /**
     * 创建并初始化 MCP Server
     *
     * <p>注册工具到服务器，并配置服务器能力。</p>
     */
    @Bean
    public McpStatelessSyncServer mcpStatelessSyncServer(
            WebMvcStatelessServerTransport transport,
            CallAgentTool callAgentTool,
            McpServerProperties properties) {

        log.info("========================================");
        log.info(">>> 初始化 AgentOZ MCP Server");
        log.info("   - 名称: {}", properties.getServerName());
        log.info("   - 版本: {}", properties.getServerVersion());
        log.info("========================================");

        // 创建工具规范
        SyncToolSpecification callAgentToolSpec = new SyncToolSpecification(
                callAgentTool.getToolDefinition(),
                callAgentTool::execute
        );

        // 构建 MCP Server
        McpStatelessSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(callAgentToolSpec)
                .build();

        log.info("✅ MCP Server 初始化成功");
        log.info("   - 端点: {}", properties.getHttpEndpoint());
        log.info("   - 工具数: 1 (call_agent)");

        return mcpServer;
    }
}
