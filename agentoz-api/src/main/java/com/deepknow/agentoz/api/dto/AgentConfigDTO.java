package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Agent配置（对齐 Codex adapter.proto SessionConfig）
 *
 * <p>这个DTO包含所有传递给Codex-Agent计算节点的配置参数。</p>
 *
 * <h3>🔄 与Proto映射 (adapter.proto)</h3>
 * <pre>
 * AgentConfigDTO (API层)      SessionConfig (Proto)
 *   ├─ provider               →   ModelProviderInfo provider_info
 *   ├─ llmModel               →   string model
 *   ├─ cwd                    →   string cwd
 *   ├─ approvalPolicy         →   ApprovalPolicy (ALWAYS/NEVER/UNLESS_TRUSTED)
 *   ├─ sandboxPolicy          →   SandboxPolicy (WORKSPACE_WRITE/READ_ONLY/DANGER_FULL_ACCESS)
 *   ├─ developerInstructions  →   string developer_instructions
 *   ├─ userInstructions       →   string instructions
 *   └─ mcpConfigJson          →   map&lt;string, McpServerDef&gt; mcp_servers
 * </pre>
 *
 * @see codex.agent.SessionConfig
 */
@Data
public class AgentConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // ============================================================
    // 1. 模型配置 - Model Configuration
    // ============================================================

    /**
     * 模型提供商配置
     * 对应 Proto: ModelProviderInfo provider_info
     */
    private ProviderConfigDTO provider;

    /**
     * 模型名称
     * 对应 Proto: string model
     * 示例: "qwen-max", "gpt-4o", "deepseek-chat"
     */
    private String llmModel;

    /**
     * 工作目录（绝对路径）
     * 对应 Proto: string cwd
     * 示例: "/workspace/coder-agent"
     */
    private String cwd;

    // ============================================================
    // 2. 策略配置 - Policy Configuration
    // ============================================================

    /**
     * 审批策略
     * 对应 Proto: ApprovalPolicy approval_policy
     * 枚举值: "ALWAYS", "NEVER", "UNLESS_TRUSTED"
     */
    private String approvalPolicy;

    /**
     * 沙箱策略
     * 对应 Proto: SandboxPolicy sandbox_policy
     * 枚举值: "WORKSPACE_WRITE", "READ_ONLY", "DANGER_FULL_ACCESS"
     */
    private String sandboxPolicy;

    // ============================================================
    // 3. 指令配置 - Instructions Configuration
    // ============================================================

    /**
     * 开发者指令（最高优先级）
     * 对应 Proto: string developer_instructions
     * 用于底层控制逻辑，普通用户不可见
     */
    private String developerInstructions;

    /**
     * 用户指令
     * 对应 Proto: string instructions
     * 给Agent的业务级指令
     */
    private String userInstructions;

    // ============================================================
    // 4. MCP 配置 - MCP Server Configuration
    // ============================================================

    /**
     * MCP服务器配置映射
     * key: 服务器名称 (如 "git", "filesystem")
     * value: MCP服务器配置
     */
    private Map<String, McpServerConfigDTO> mcpServers;

    /**
     * MCP服务器配置 (JSON 字符串格式)
     * 对应 Proto: map&lt;string, McpServerDef&gt; mcp_servers
     * <p>直接透传业务侧配置的原始 JSON，避免手动组装对象。</p>
     * 优先级高于 mcpServers 字段。
     * <p>格式示例: { "filesystem": { "server_type": "stdio", "command": "npx", "args": ["..."], "env": {...} } }</p>
     */
    private String mcpConfigJson;

    // ============================================================
    // 5. 配置元数据
    // ============================================================

    /**
     * 配置名称（人类可读）
     * 示例: "Qwen-Max-代码助手"
     */
    private String configName;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 配置标签（逗号分隔）
     * 示例: "coding,analysis"
     */
    private String tags;
}
