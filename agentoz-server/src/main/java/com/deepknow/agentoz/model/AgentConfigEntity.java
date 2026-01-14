package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.deepknow.agentoz.dto.config.ModelProviderInfoVO;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent配置实体 - 对齐 Codex Adapter 的 SessionConfig
 *
 * <p>这个实体存储了传递给 Codex-Agent 计算节点的所有配置参数，
 * 完全对应 Proto 定义中的 {@code SessionConfig} 消息。</p>
 *
 * <h3>🔄 与 AgentEntity 的关系</h3>
 * <ul>
 *   <li>一个 AgentConfig 可以被多个 Agent 共享（配置复用）</li>
 *   <li>AgentEntity 通过 {@code configId} 关联到此实体</li>
 *   <li>支持配置模板机制（预设的常用配置）</li>
 * </ul>
 *
 * <h3>📦 配置分类 (对齐 adapter.proto)</h3>
 * <ol>
 *   <li>模型配置 - model, model_provider, provider_info</li>
 *   <li>策略配置 - approval_policy, sandbox_policy</li>
 *   <li>指令配置 - instructions, developer_instructions</li>
 *   <li>MCP配置 - mcp_servers (JSON)</li>
 *   <li>工作目录 - cwd</li>
 * </ol>
 *
 * <h3>🎯 Proto 映射 (adapter.proto)</h3>
 * <pre>
 * Proto: SessionConfig              → Java: AgentConfigEntity
 *   ├─ string model                 →   ├─ String llmModel
 *   ├─ string model_provider        →   ├─ String modelProvider
 *   ├─ ModelProviderInfo provider_info → ├─ ModelProviderInfoVO providerInfo
 *   ├─ string instructions          →   ├─ String userInstructions
 *   ├─ string developer_instructions→   ├─ String developerInstructions
 *   ├─ ApprovalPolicy               →   ├─ String approvalPolicy
 *   ├─ SandboxPolicy                →   ├─ String sandboxPolicy
 *   ├─ string cwd                   →   ├─ String cwd
 *   └─ map&lt;string, McpServerDef&gt;   →   └─ String mcpConfigJson
 * </pre>
 *
 * @see AgentEntity
 * @see codex.agent.SessionConfig
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "agent_configs", autoResultMap = true)
public class AgentConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置唯一标识
     * 格式: "cfg-{timestamp}-{random}"
     */
    private String configId;

    /**
     * 配置名称（人类可读）
     * 示例: "Qwen-Max-高推理模式", "GPT-4o-代码助手"
     */
    private String configName;

    // ============================================================
    // 1. 模型配置 - Model Configuration (对齐 adapter.proto)
    // ============================================================

    /**
     * 模型名称
     * 对应 Proto: string model
     * 示例: "qwen-max", "gpt-4o", "deepseek-chat"
     */
    @TableField("llm_model")
    private String llmModel;

    /**
     * 模型提供商名称
     * 对应 Proto: string model_provider
     * 示例: "openai", "qwen", "deepseek"
     */
    private String modelProvider;

    /**
     * 模型提供商详细配置
     * 对应 Proto: ModelProviderInfo provider_info
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ModelProviderInfoVO providerInfo;

    /**
     * 工作目录（绝对路径）
     * 对应 Proto: string cwd
     * 示例: "/workspace/coder-agent", "/workspace/analyst"
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
     * 给 Agent 的业务级指令
     */
    private String userInstructions;

    // ============================================================
    // 4. MCP 配置 - MCP Server Configuration
    // ============================================================

    /**
     * MCP 服务器配置 (JSON 字符串格式)
     *
     * <p>存储 MCP 服务器配置，格式为 JSON 对象，key 为服务器名称，value 为 McpServerDef</p>
     *
     * <h3>📦 格式示例</h3>
     * <pre>
     * {
     *   "filesystem": {
     *     "server_type": "stdio",
     *     "command": "npx",
     *     "args": ["-y", "@modelcontextprotocol/server-filesystem", "/allowed/path"],
     *     "env": {}
     *   },
     *   "github": {
     *     "server_type": "streamable_http",
     *     "url": "https://api.github.com/mcp"
     *   }
     * }
     * </pre>
     *
     * <p>⚠️ 转换器会将此 JSON 解析为 {@code map<string, McpServerDef>}</p>
     */
    private String mcpConfigJson;

    // ============================================================
    // 5. 元数据与生命周期
    // ============================================================

    /**
     * 是否为预设模板
     * true - 系统预设配置，不可删除
     * false - 用户自定义配置
     */
    private Boolean isTemplate;

    /**
     * 配置标签（逗号分隔）
     * 示例: "coding,high-reasoning", "analysis,low-cost"
     */
    private String tags;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 扩展元数据（JSON格式）
     * 用于存储未预定义的扩展字段
     */
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;

    /**
     * 创建者用户ID
     */
    private String createdBy;
}
