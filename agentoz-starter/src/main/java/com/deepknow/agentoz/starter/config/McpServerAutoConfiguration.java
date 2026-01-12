package com.deepknow.agentoz.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

@Slf4j
@Configuration
@ConditionalOnClass(McpServer.class)
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "agentoz.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpToolRegistry mcpToolRegistry(ObjectMapper objectMapper) {
        return new McpToolRegistry(objectMapper);
    }

    /**
     * 创建 WebMvc Stateless Server Transport
     */
    @Bean
    @ConditionalOnMissingBean
    public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            ObjectMapper objectMapper,
            McpProperties properties) {

        log.info("[AgentOZ Starter] 创建 MCP Transport, Endpoint: {}", properties.getHttpEndpoint());

        return WebMvcStatelessServerTransport.builder()
                .objectMapper(objectMapper)
                .messageEndpoint(properties.getHttpEndpoint())
                .build();
    }

    /**
     * 注册路由
     */
    @Bean
    @ConditionalOnMissingBean(name = "mcpRouterFunction")
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcStatelessServerTransport transport) {
        return transport.getRouterFunction();
    }

    /**
     * 创建 MCP Server
     */
    @Bean
    @ConditionalOnMissingBean
    public McpStatelessSyncServer mcpStatelessSyncServer(
            WebMvcStatelessServerTransport transport,
            McpToolRegistry registry,
            McpProperties properties) {

        log.info("[AgentOZ Starter] 初始化 MCP Server: {} (v{})",
                properties.getServerName(), properties.getServerVersion());

        // 1. 自动扫描
        List<SyncToolSpecification> detectedTools = registry.scanAndBuildTools();
        log.info("[AgentOZ Starter] 扫描到 {} 个工具", detectedTools.size());

        // 2. 构建 Server
        var serverBuilder = McpServer.sync(transport)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build());

        // 3. 注册工具
        for (SyncToolSpecification spec : detectedTools) {
            serverBuilder.tools(spec);
        }

        return serverBuilder.build();
    }
}
