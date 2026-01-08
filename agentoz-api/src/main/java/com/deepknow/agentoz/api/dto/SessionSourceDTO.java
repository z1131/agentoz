package com.deepknow.agentoz.api.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 会话来源标识（对齐Proto的SessionSource）
 *
 * <p>对应Codex-Agent Proto定义:</p>
 * <pre>
 * message SessionSource {
 *   string source_type = 1;      // "API", "IDE", "CLI"
 *   optional string integration_name = 2;
 *   optional string integration_version = 3;
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSourceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 来源类型
     * 枚举值: "API", "IDE", "CLI"
     */
    private String sourceType;

    /**
     * 集成名称（可选）
     * 示例: "VSCode Plugin", "JetBrains Plugin"
     */
    private String integrationName;

    /**
     * 集成版本（可选）
     * 示例: "1.0.0"
     */
    private String integrationVersion;
}
