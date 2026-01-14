package com.deepknow.agentoz.dto.config;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 模型提供商详细配置（对齐 adapter.proto 的 ModelProviderInfo）
 *
 * <h3>🎯 Proto 定义</h3>
 * <pre>
 * message ModelProviderInfo {
 *   string name = 1;
 *   optional string base_url = 2;
 *   optional string env_key = 3;
 *   optional string experimental_bearer_token = 4;
 *   WireApi wire_api = 5;
 *   map<string, string> http_headers = 6;
 *   map<string, string> query_params = 7;
 *   bool requires_openai_auth = 8;
 * }
 * </pre>
 *
 * <h3>📦 WireApi 枚举</h3>
 * <ul>
 *   <li>WIRE_API_CHAT (0) - 标准 Chat Completion API</li>
 *   <li>WIRE_API_RESPONSES (1) - OpenAI Responses API</li>
 *   <li>WIRE_API_RESPONSES_WEBSOCKET (2) - WebSocket 模式</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProviderInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 提供商名称
     * 示例: "qwen", "openai", "anthropic", "deepseek"
     */
    private String name;

    /**
     * API 基础 URL
     * 示例: "https://dashscope.aliyuncs.com/compatible-mode/v1"
     */
    private String baseUrl;

    /**
     * 环境变量 Key 名称（用于从 env_vars 中获取 API Key）
     * 示例: "QWEN_API_KEY", "OPENAI_API_KEY"
     */
    private String envKey;

    /**
     * 直接传递的 Bearer Token（优先级高于 envKey）
     * ⚠️ 敏感信息，建议通过 env_vars 传递
     */
    private String experimentalBearerToken;

    /**
     * Wire API 类型
     * 可选值: "chat", "responses", "responses_websocket"
     */
    private String wireApi;

    /**
     * 自定义 HTTP 请求头
     */
    private Map<String, String> httpHeaders;

    /**
     * 自定义查询参数
     */
    private Map<String, String> queryParams;

    /**
     * 是否需要 OpenAI 认证格式
     */
    private Boolean requiresOpenaiAuth;
}
