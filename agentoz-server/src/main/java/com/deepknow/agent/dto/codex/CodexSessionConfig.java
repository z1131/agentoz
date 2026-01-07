package com.deepknow.agent.dto.codex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.HashMap;

/**
 * 对应 Rust 侧的 SessionConfigDto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexSessionConfig {

    @JsonProperty("provider")
    private ModelProviderInfo provider;

    @JsonProperty("model")
    private String model;

    @JsonProperty("model_reasoning_effort")
    private String modelReasoningEffort;

    @JsonProperty("model_reasoning_summary")
    private String modelReasoningSummary;

    @JsonProperty("developer_instructions")
    private String developerInstructions;

    @JsonProperty("user_instructions")
    private String userInstructions;

    @JsonProperty("base_instructions")
    private String baseInstructions;

    @JsonProperty("compact_prompt")
    private String compactPrompt;

    @JsonProperty("approval_policy")
    private String approvalPolicy;

    @JsonProperty("sandbox_policy")
    private SandboxPolicy sandboxPolicy;

    @JsonProperty("cwd")
    private String cwd;

    @JsonProperty("session_source")
    private String sessionSource;

    @JsonProperty("mcp_servers")
    @Builder.Default
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();
}
