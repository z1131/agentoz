package com.deepknow.agentoz.dto.config;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 模型提供商配置（对齐Proto的ProviderConfig）
 *
 * <p>对应Codex-Agent Proto定义:</p>
 * <pre>
 * message ProviderConfig {
 *   string name = 1;              // e.g., "openai", "qwen"
 *   optional string base_url = 2;  // e.g., "https://dashscope..."
 *   optional string api_key = 3;   // API密钥
 *   optional string wire_api = 4;  // "chat" | "responses"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfigVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 提供商名称
     * 示例: "qwen", "openai", "anthropic", "deepseek"
     */
    private String name;

    /**
     * API基础URL
     * 示例: "https://dashscope.aliyuncs.com"
     */
    private String baseUrl;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * Wire API协议（可选）
     * 枚举: "chat", "responses"
     */
    private String wireApi;
}
