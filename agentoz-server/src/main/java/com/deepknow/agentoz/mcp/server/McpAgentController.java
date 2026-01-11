package com.deepknow.agentoz.mcp.server;

import io.modelcontextprotocol.server.McpStatelessSyncServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

/**
 * MCP Agent Controller
 *
 * <p>提供 MCP Server 的健康检查和状态查询端点。</p>
 *
 * <p>实际的 JSON-RPC 消息处理由 MCP SDK 的
 * HttpServletStatelessServerTransport 自动处理，端点为 /mcp/agent/message</p>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/mcp/agent")
public class McpAgentController {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 健康检查端点
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public String health() {
        try {
            McpStatelessSyncServer server = getMcpServer();
            return "{\"status\":\"ok\",\"server\":\"agentoz-mcp\",\"version\":\"1.0.0\"}";
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取服务器信息
     *
     * @return 服务器信息
     */
    @GetMapping("/info")
    public String info() {
        try {
            McpStatelessSyncServer server = getMcpServer();
            var serverInfo = server.getServerInfo();
            var capabilities = server.getServerCapabilities();

            boolean hasTools = capabilities.tools() != null;

            return String.format(
                    "{\"name\":\"%s\",\"version\":\"%s\",\"tools\":%s}",
                    serverInfo.name(),
                    serverInfo.version(),
                    hasTools
            );
        } catch (Exception e) {
            log.error("获取服务器信息失败", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 从 Spring 容器中获取 MCP Server Bean
     */
    private McpStatelessSyncServer getMcpServer() {
        return applicationContext.getBean(McpStatelessSyncServer.class);
    }
}
