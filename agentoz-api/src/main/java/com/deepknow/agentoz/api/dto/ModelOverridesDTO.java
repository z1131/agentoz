package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型能力覆盖配置（对齐Proto的ModelOverrides）
 *
 * <p>对应Codex-Agent Proto定义:</p>
 * <pre>
 * message ModelOverrides {
 *   optional string shell_type = 1;                      // "Default", "Disabled", "ShellCommand"
 *   optional bool supports_parallel_tool_calls = 2;     // true/false
 *   optional string apply_patch_tool_type = 3;          // "Required", null
 *   optional uint64 context_window = 4;                  // 上下文窗口大小
 *   optional uint64 auto_compact_token_limit = 5;       // 自动压缩历史阈值
 * }
 * </pre>
 *
 * <h3>🔧 用途</h3>
 * <ul>
 *   <li><b>shell_type</b>: 控制Shell工具行为</li>
 *   <li><b>supports_parallel_tool_calls</b>: 是否支持并行工具调用（Qwen/Claude=true）</li>
 *   <li><b>context_window</b>: 覆盖模型的上下文窗口大小</li>
 *   <li><b>auto_compact_token_limit</b>: 自动压缩历史记录的Token阈值</li>
 * </ul>
 */
@Data
public class ModelOverridesDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Shell工具类型
     * 枚举值: "Default" (OpenAI), "Disabled" (国产模型), "ShellCommand"
     */
    private String shellType;

    /**
     * 是否支持并行工具调用
     * Qwen/Claude = true
     * OpenAI/DeepSeek = false
     */
    private Boolean supportsParallelToolCalls;

    /**
     * 补丁工具类型
     * 枚举值: "Required" (OpenAI), null (其他)
     */
    private String applyPatchToolType;

    /**
     * 上下文窗口大小
     * 示例: 128000, 200000
     */
    private Long contextWindow;

    /**
     * 自动压缩历史的Token阈值
     * 超过此值自动压缩历史记录
     */
    private Long autoCompactTokenLimit;
}
