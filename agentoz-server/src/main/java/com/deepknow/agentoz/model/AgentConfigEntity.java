package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.deepknow.agentoz.dto.config.McpServerConfigVO;
import com.deepknow.agentoz.dto.config.ModelOverridesVO;
import com.deepknow.agentoz.dto.config.ProviderConfigVO;
import com.deepknow.agentoz.dto.config.SessionSourceVO;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent配置实体 - 完整对齐Codex-Agent的SessionConfig
 *
 * <p>这个实体存储了传递给Codex-Agent计算节点的所有配置参数，
 * 完全对应Proto定义中的 {@code SessionConfig} 消息。</p>
 *
 * <h3>🔄 与AgentEntity的关系</h3>
 * <ul>
 *   <li>一个AgentConfig可以被多个Agent共享（配置复用）</li>
 *   <li>AgentEntity通过 {@code configId} 关联到此实体</li>
 *   <li>支持配置模板机制（预设的常用配置）</li>
 * </ul>
 *
 * <h3>📦 配置分类</h3>
 * <ol>
 *   <li>基础环境 - provider, model, cwd</li>
 *   <li>策略配置 - approval_policy, sandbox_policy</li>
 *   <li>指令配置 - developer/user/base_instructions</li>
 *   <li>推理配置 - reasoning_effort, reasoning_summary</li>
 *   <li>高级配置 - mcp_servers, model_overrides</li>
 * </ol>
 *
 * <h3>🎯 Proto映射</h3>
 * <pre>
 * Proto: SessionConfig           → Java: AgentConfigEntity
 *   ├─ ProviderConfig provider   →   ├─ ModelProviderInfo provider
 *   ├─ string model              →   ├─ String model
 *   ├─ string cwd                →   ├─ String cwd
 *   ├─ ApprovalPolicy ...        →   ├─ String approvalPolicy (枚举名称)
 *   ├─ SandboxPolicy ...         →   ├─ String sandboxPolicy (枚举名称)
 *   └─ map&lt;string, McpServerConfig&gt; mcp_servers
 *                                →   └─ Map&lt;String, McpServerConfig&gt; mcpServers
 * </pre>
 *
 * @see AgentEntity
 * @see com.deepknow.agentoz.infra.adapter.grpc.AgentProtos.SessionConfig
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
    // 1. 基础环境配置 - Basic Environment
    // ============================================================

    /**
     * 模型提供商配置
     * 对应Proto: ProviderConfig provider
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ProviderConfigVO provider;

    /**
     * 模型名称
     * 对应Proto: string model
     * 示例: "qwen-max", "gpt-4o", "deepseek-chat"
     */
    private String model;

    /**
     * 工作目录（绝对路径）
     * 对应Proto: string cwd
     * 示例: "/workspace/coder-agent", "/workspace/analyst"
     */
    private String cwd;

    // ============================================================
    // 2. 策略配置 - Policy Configuration
    // ============================================================

    /**
     * 审批策略
     * 对应Proto: ApprovalPolicy approval_policy
     * 枚举值: "AUTO_APPROVE", "MANUAL_APPROVE", "BLOCK_ALL"
     */
    private String approvalPolicy;

    /**
     * 沙箱策略
     * 对应Proto: SandboxPolicy sandbox_policy
     * 枚举值: "READ_ONLY", "SANDBOXED", "INSECURE"
     */
    private String sandboxPolicy;

    // ============================================================
    // 3. 指令配置 - Instructions Configuration
    // ============================================================

    /**
     * 开发者指令（最高优先级）
     * 对应Proto: string developer_instructions
     * 用于底层控制逻辑，普通用户不可见
     */
    private String developerInstructions;

    /**
     * 用户指令
     * 对应Proto: string user_instructions
     * 给Agent的业务级指令
     */
    private String userInstructions;

    /**
     * 基础指令模板
     * 对应Proto: string base_instructions
     * 覆盖默认行为模板
     */
    private String baseInstructions;

    // ============================================================
    // 4. 推理配置 - Reasoning Configuration
    // ============================================================

    /**
     * 推理强度
     * 对应Proto: ReasoningEffort model_reasoning_effort
     * 枚举值: "REASONING_NONE", "MINIMAL", "LOW", "MEDIUM", "HIGH"
     */
    private String reasoningEffort;

    /**
     * 推理摘要模式
     * 对应Proto: ReasoningSummary model_reasoning_summary
     * 枚举值: "AUTO", "CONCISE", "DETAILED", "REASONING_SUMMARY_NONE"
     */
    private String reasoningSummary;

    /**
     * 压缩提示词覆盖
     * 对应Proto: string compact_prompt
     */
    private String compactPrompt;

    // ============================================================
    // 5. 高级配置 - Advanced Configuration
    // ============================================================

    /**
     * 模型能力覆盖配置
     * 对应Proto: ModelOverrides model_overrides
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ModelOverridesVO modelOverrides;

    /**
     * MCP服务器配置映射
     * 对应Proto: map<string, McpServerConfig> mcp_servers
     * key: 服务器名称 (如 "git", "filesystem")
     * value: MCP服务器配置
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, McpServerConfigVO> mcpServers;

    /**
     * 会话来源标识
     * 对应Proto: SessionSource session_source
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SessionSourceVO sessionSource;

    // ============================================================
    // 6. 元数据与生命周期
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
