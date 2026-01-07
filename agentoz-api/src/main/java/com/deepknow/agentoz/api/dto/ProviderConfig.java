package com.deepknow.agentoz.api.dto;

import java.io.Serializable;

/**
 * 模型供应商配置
 */
public class ProviderConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 供应商类型: openai, anthropic, aliyun, ollama, google
     */
    private String type;

    /**
     * 访问令牌 (API Key)
     */
    private String apiKey;

    /**
     * 自定义 API 基准地址
     */
    private String baseUrl;

    public ProviderConfig() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
