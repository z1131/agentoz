package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent配置（重构版 - 完全对齐Codex-Agent的SessionConfig）
 *
 * <p>这个DTO包含所有传递给Codex-Agent计算节点的配置参数。</p>
 *
 * <h3>🔄 与Proto映射</h3>
 * <pre>
 * AgentConfigDTO (API层)      SessionConfig (Proto)
 *   ├─ provider               →   ProviderConfig
 *   ├─ model                  →   string model
 *   ├─ cwd                    →   string cwd
 *   ├─ approvalPolicy         →   ApprovalPolicy (enum)
 *   ├─ sandboxPolicy          →   SandboxPolicy (enum)
 *   ├─ developerInstructions  →   string developer_instructions
 *   ├─ userInstructions       →   string user_instructions
 *   ├─ baseInstructions       →   string base_instructions
 *   ├─ reasoningEffort        →   ReasoningEffort (enum)
 *   ├─ reasoningSummary       →   ReasoningSummary (enum)
 *   ├─ mcpServers             →   map&lt;string, McpServerConfig&gt;
 *   └─ ... 更多字段
 * </pre>
 *
 * <h3>🎯 设计原则</h3>
 * <ul>
 *   <li><b>强类型</b>: 枚举使用String存储，便于传输</li>
 *   <li><b>完整对齐</b>: 包含Proto定义的所有字段</li>
 *   <li><b>可选字段</b>: 使用包装类支持null值</li>
 * </ul>
 */
@Data
public class AgentConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // ============================================================
    // 1. 基础环境配置 - Basic Environment
    // ============================================================

    /**
     * 模型提供商配置
     */
    private ProviderConfigDTO provider;

    /**
     * 模型名称
     * 示例: "qwen-max", "gpt-4o", "deepseek-chat"
     */
    private String model;

    /**
     * 工作目录（绝对路径）
     * 示例: "/workspace/coder-agent"
     */
    private String cwd;

    // ============================================================
    // 2. 策略配置 - Policy Configuration
    // ============================================================

    /**
     * 审批策略
     * 枚举值: "AUTO_APPROVE", "MANUAL_APPROVE", "BLOCK_ALL"
     */
    private String approvalPolicy;

    /**
     * 沙箱策略
     * 枚举值: "READ_ONLY", "SANDBOXED", "INSECURE"
     */
    private String sandboxPolicy;

    // ============================================================
    // 3. 指令配置 - Instructions Configuration
    // ============================================================

    /**
     * 开发者指令（最高优先级）
     * 用于底层控制逻辑，普通用户不可见
     */
    private String developerInstructions;

    /**
     * 用户指令
     * 给Agent的业务级指令
     */
    private String userInstructions;

    /**
     * 基础指令模板
     * 覆盖默认行为模板
     */
    private String baseInstructions;

    // ============================================================
    // 4. 推理配置 - Reasoning Configuration
    // ============================================================

    /**
     * 推理强度
     * 枚举值: "REASONING_NONE", "MINIMAL", "LOW", "MEDIUM", "HIGH"
     */
    private String reasoningEffort;

    /**
     * 推理摘要模式
     * 枚举值: "AUTO", "CONCISE", "DETAILED", "REASONING_SUMMARY_NONE"
     */
    private String reasoningSummary;

    /**
     * 压缩提示词覆盖
     */
    private String compactPrompt;

    // ============================================================
    // 5. 高级配置 - Advanced Configuration
    // ============================================================

    /**
     * 模型能力覆盖配置
     * 包含: shell_type, supports_parallel_tool_calls, context_window等
     */
    private ModelOverridesDTO modelOverrides;

    /**
     * MCP服务器配置映射
     * key: 服务器名称 (如 "git", "filesystem")
     * value: MCP服务器配置
     */
    private Map<String, McpServerConfigDTO> mcpServers;

    /**
     * 会话来源标识
     * 包含: source_type ("API", "IDE", "CLI"), integration_name等
     */
    private SessionSourceDTO sessionSource;

    // ============================================================
    // 6. 配置元数据
    // ============================================================

    /**
     * 配置名称（人类可读）
     * 示例: "Qwen-Max-高推理模式"
     */
    private String configName;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 配置标签（逗号分隔）
     * 示例: "coding,high-reasoning"
     */
    private String tags;
}
