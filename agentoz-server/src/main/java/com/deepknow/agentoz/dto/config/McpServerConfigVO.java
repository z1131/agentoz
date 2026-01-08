package com.deepknow.agentoz.dto.config;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MCP服务器配置（对齐Proto的McpServerConfig）
 *
 * <p>对应Codex-Agent Proto定义:</p>
 * <pre>
 * message McpServerConfig {
 *   string command = 1;          // e.g., "npx", "docker"
 *   repeated string args = 2;    // e.g., ["-y", "@modelcontextprotocol/server-git"]
 *   map&lt;string, string&gt; env = 3; // 环境变量
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfigVO implements Serializable {
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
