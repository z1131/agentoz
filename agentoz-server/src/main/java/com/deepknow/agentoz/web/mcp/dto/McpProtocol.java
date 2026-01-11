package com.deepknow.agentoz.web.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Protocol DTOs (JSON-RPC 2.0)
 */
public class McpProtocol {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JsonRpcRequest {
        private String jsonrpc; // "2.0"
        private Object id;
        private String method;
        private JsonNode params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        private String jsonrpc; // "2.0"
        private Object id;
        private Object result;
        private JsonRpcError error;

        public static JsonRpcResponse success(Object id, Object result) {
            return JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(result)
                    .build();
        }

        public static JsonRpcResponse error(Object id, int code, String message) {
            return JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .error(new JsonRpcError(code, message, null))
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JsonRpcError {
        private int code;
        private String message;
        private Object data;
    }

    // --- MCP Specific Payloads ---

    @Data
    @Builder
    public static class InitializeResult {
        private String protocolVersion;
        private ServerCapabilities capabilities;
        private ServerInfo serverInfo;
    }

    @Data
    @Builder
    public static class ServerCapabilities {
        private Map<String, Object> tools; // e.g. {}
    }

    @Data
    @Builder
    public static class ServerInfo {
        private String name;
        private String version;
    }

    @Data
    @Builder
    public static class ListToolsResult {
        private List<Tool> tools;
    }

    @Data
    @Builder
    public static class Tool {
        private String name;
        private String description;
        private JsonNode inputSchema;
    }

    @Data
    @Builder
    public static class CallToolResult {
        private List<ToolContent> content;
        private Boolean isError;
    }

    @Data
    @Builder
    public static class ToolContent {
        private String type; // "text"
        private String text;
    }
}
