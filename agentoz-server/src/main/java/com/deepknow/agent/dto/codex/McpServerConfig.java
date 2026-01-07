package com.deepknow.agent.dto.codex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {

    @JsonProperty("command")
    private String command;

    @JsonProperty("args")
    private List<String> args;

    @JsonProperty("env")
    private Map<String, String> env;
}
