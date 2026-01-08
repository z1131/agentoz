package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MCP服务器配置（重构版 - 对齐Proto的McpServerConfig）
 *
 * <p>对应Codex-Agent Proto定义:</p>
 * <pre>
 * message McpServerConfig {
 *   string command = 1;          // e.g., "npx", "docker"
 *   repeated string args = 2;    // e.g., ["-y", "@modelcontextprotocol/server-git"]
 *   map&lt;string, string&gt; env = 3; // 环境变量
 * }
 * </pre>
 *
 * <h3>🔄 使用示例</h3>
 * <pre>
 * // 方式1: SSE连接（简化版）
 * McpServerConfig config = new McpServerConfig();
 * config.setCommand("sse");
 * config.setArgs(List.of("https://example.com/mcp"));
 *
 * // 方式2: npx命令
 * McpServerConfig config = new McpServerConfig();
 * config.setCommand("npx");
 * config.setArgs(List.of("-y", "@modelcontextprotocol/server-git"));
 * Map&lt;String, String&gt; env = new HashMap&lt;&gt;();
 * env.put("GIT_TOKEN", "xxx");
 * config.setEnv(env);
 * </pre>
 */
@Data
public class McpServerConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 启动命令
     * 示例: "npx", "docker", "sse"（简化模式）
     */
    private String command;

    /**
     * 命令参数列表
     * 示例: ["-y", "@modelcontextprotocol/server-git"]
     */
    private List<String> args;

    /**
     * 环境变量映射
     * 示例: {"GIT_TOKEN": "xxx", "PATH": "/usr/bin"}
     */
    private Map<String, String> env;
}
