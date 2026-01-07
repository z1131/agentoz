package com.deepknow.agent.dto.codex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 对应 Rust 侧的 ModelProviderInfo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProviderInfo {

    /**
     * 供应商类型: openai, anthropic, google, aliyun, ollama
     */
    @JsonProperty("type")
    private String type;

    /**
     * 透传的 API Key
     */
    @JsonProperty("experimental_bearer_token")
    private String experimentalBearerToken;

    /**
     * 自定义 API Base URL
     */
    @JsonProperty("base_url")
    private String baseUrl;
}
