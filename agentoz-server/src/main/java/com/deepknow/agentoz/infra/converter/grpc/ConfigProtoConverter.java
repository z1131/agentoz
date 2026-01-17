package com.deepknow.agentoz.infra.converter.grpc;

import com.deepknow.agentoz.dto.config.ModelProviderInfoVO;
import codex.agent.*;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;

/**
 * 实体到 Proto 的转换器（对齐 adapter.proto）
 *
 * <p>负责将 AgentOZ 的实体类转换为 Codex Adapter 的 Proto 强类型定义。</p>
 *
 * <h3>🔄 转换映射 (adapter.proto)</h3>
 * <pre>
 * AgentConfigEntity              →  SessionConfig (Proto)
 *   ├─ llmModel                  →    string model
 *   ├─ modelProvider             →    string model_provider
 *   ├─ providerInfo              →    ModelProviderInfo provider_info
 *   ├─ baseInstructions          →    string instructions
 *   ├─ developerInstructions     →    string developer_instructions
 *   ├─ approvalPolicy (String)   →    ApprovalPolicy (Enum)
 *   ├─ sandboxPolicy (String)    →    SandboxPolicy (Enum)
 *   ├─ cwd                       →    string cwd
 *   └─ mcpConfigJson (JSON)      →    map&lt;string, McpServerDef&gt; mcp_servers
 * </pre>
 *
 * @see AgentConfigEntity
 * @see codex.agent.SessionConfig
 */
@Slf4j
public class ConfigProtoConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 AgentConfigEntity 转换为 SessionConfig (Proto)
     *
     * @param entity Agent 配置实体
     * @return SessionConfig Proto 实例
     */
    public static SessionConfig toSessionConfig(AgentConfigEntity entity) {
        if (entity == null) {
            log.warn("AgentConfigEntity 为 null,返回空的 SessionConfig");
            return SessionConfig.getDefaultInstance();
        }

        // DEBUG: 打印原始实体数据
        log.info("[DEBUG] AgentConfigEntity: llmModel={}, modelProvider={}, providerInfo={}", 
            entity.getLlmModel(), entity.getModelProvider(), entity.getProviderInfo());

        SessionConfig.Builder builder = SessionConfig.newBuilder();

        // 1. 模型配置
        if (entity.getLlmModel() != null) {
            builder.setModel(entity.getLlmModel());
        }
        if (entity.getModelProvider() != null) {
            builder.setModelProvider(entity.getModelProvider());
        }
        if (entity.getProviderInfo() != null) {
            builder.setProviderInfo(toModelProviderInfo(entity.getProviderInfo()));
        }

        // 2. 指令配置
        if (entity.getBaseInstructions() != null) {
            builder.setBaseInstructions(entity.getBaseInstructions());
        }
        if (entity.getDeveloperInstructions() != null) {
            builder.setDeveloperInstructions(entity.getDeveloperInstructions());
        }

        // 3. 策略配置 (枚举转换)
        if (entity.getApprovalPolicy() != null) {
            builder.setApprovalPolicy(parseApprovalPolicy(entity.getApprovalPolicy()));
        }
        if (entity.getSandboxPolicy() != null) {
            builder.setSandboxPolicy(parseSandboxPolicy(entity.getSandboxPolicy()));
        }

        // 4. 工作目录
        if (entity.getCwd() != null) {
            builder.setCwd(entity.getCwd());
        }

        // 5. MCP 服务器配置 (JSON → map<string, McpServerDef>)
        if (entity.getMcpConfigJson() != null && !entity.getMcpConfigJson().isEmpty()) {
            try {
                parseMcpServers(entity.getMcpConfigJson(), builder);
                log.info("解析 MCP 配置成功: length={}", entity.getMcpConfigJson().length());
            } catch (Exception e) {
                log.error("解析 MCP 配置失败: {}", e.getMessage(), e);
            }
        }

        SessionConfig config = builder.build();
        log.debug("AgentConfigEntity 转换为 SessionConfig: model={}, provider={}, approvalPolicy={}",
                config.getModel(), config.getModelProvider(), config.getApprovalPolicy());

        return config;
    }

    /**
     * 转换 ModelProviderInfo
     */
    private static ModelProviderInfo toModelProviderInfo(ModelProviderInfoVO vo) {
        if (vo == null) {
            return ModelProviderInfo.getDefaultInstance();
        }

        ModelProviderInfo.Builder builder = ModelProviderInfo.newBuilder();

        if (vo.getName() != null) {
            builder.setName(vo.getName());
        }
        if (vo.getBaseUrl() != null) {
            builder.setBaseUrl(vo.getBaseUrl());
        }
        if (vo.getEnvKey() != null) {
            builder.setEnvKey(vo.getEnvKey());
        }
        if (vo.getExperimentalBearerToken() != null) {
            builder.setExperimentalBearerToken(vo.getExperimentalBearerToken());
        }
        if (vo.getWireApi() != null) {
            builder.setWireApi(parseWireApi(vo.getWireApi()));
        }
        if (vo.getHttpHeaders() != null) {
            builder.putAllHttpHeaders(vo.getHttpHeaders());
        }
        if (vo.getQueryParams() != null) {
            builder.putAllQueryParams(vo.getQueryParams());
        }
        if (vo.getRequiresOpenaiAuth() != null) {
            builder.setRequiresOpenaiAuth(vo.getRequiresOpenaiAuth());
        }

        return builder.build();
    }

    /**
     * 解析 MCP 服务器配置 JSON 并填充到 builder
     *
     * <p>支持的 JSON 格式：</p>
     * <pre>
     * {
     *   "server_name": {
     *     "server_type": "stdio" | "streamable_http",
     *     "command": "...",
     *     "args": ["..."],
     *     "env": {},
     *     "url": "..."
     *   }
     * }
     * </pre>
     */
    private static void parseMcpServers(String mcpJson, SessionConfig.Builder builder) throws Exception {
        JsonNode root = objectMapper.readTree(mcpJson);

        // 如果 JSON 包含 "mcp_servers" 字段，则使用该字段
        JsonNode serversNode = root.has("mcp_servers") ? root.get("mcp_servers") : root;

        if (!serversNode.isObject()) {
            log.warn("MCP 配置不是有效的 JSON 对象");
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = serversNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String serverName = entry.getKey();
            JsonNode serverConfig = entry.getValue();

            McpServerDef.Builder defBuilder = McpServerDef.newBuilder();

            // server_type
            if (serverConfig.has("server_type")) {
                defBuilder.setServerType(serverConfig.get("server_type").asText());
            } else if (serverConfig.has("type")) {
                // 兼容旧格式
                defBuilder.setServerType(serverConfig.get("type").asText());
            }

            // command (stdio 模式)
            if (serverConfig.has("command")) {
                defBuilder.setCommand(serverConfig.get("command").asText());
            }

            // args (stdio 模式)
            if (serverConfig.has("args") && serverConfig.get("args").isArray()) {
                for (JsonNode arg : serverConfig.get("args")) {
                    defBuilder.addArgs(arg.asText());
                }
            }

            // env (stdio 模式)
            if (serverConfig.has("env") && serverConfig.get("env").isObject()) {
                Iterator<Map.Entry<String, JsonNode>> envFields = serverConfig.get("env").fields();
                while (envFields.hasNext()) {
                    Map.Entry<String, JsonNode> envEntry = envFields.next();
                    
                    defBuilder.putEnv(envEntry.getKey(), envEntry.getValue().asText());
                }
            }

            // url (streamable_http 模式)
            if (serverConfig.has("url")) {
                defBuilder.setUrl(serverConfig.get("url").asText());
            }

            // http_headers (streamable_http 模式)
            JsonNode headersNode = serverConfig.get("http_headers");

            if (headersNode != null && headersNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> headerFields = headersNode.fields();
                while (headerFields.hasNext()) {
                    Map.Entry<String, JsonNode> headerEntry = headerFields.next();
                    // 确保 value 是字符串
                    String val = headerEntry.getValue().isTextual() ?
                         headerEntry.getValue().asText() : headerEntry.getValue().toString();

                    // ✓ 直接设置到 http_headers 字段 (Proto 已添加此字段)
                    defBuilder.putHttpHeaders(headerEntry.getKey(), val);
                    log.debug("添加 HTTP 请求头: {}={***}", headerEntry.getKey());
                }
            }

            builder.putMcpServers(serverName, defBuilder.build());
            log.debug("解析 MCP 服务器: name={}, type={}", serverName, defBuilder.getServerType());
        }
    }

    // ============================================================
    // 枚举转换方法 (对齐 adapter.proto)
    // ============================================================

    /**
     * 解析 WireApi 类型
     */
    private static WireApi parseWireApi(String wireApi) {
        if (wireApi == null || wireApi.isEmpty()) {
            return WireApi.WIRE_API_CHAT;
        }

        return switch (wireApi.toLowerCase()) {
            case "chat" -> WireApi.WIRE_API_CHAT;
            case "responses" -> WireApi.WIRE_API_RESPONSES;
            case "responses_websocket" -> WireApi.WIRE_API_RESPONSES_WEBSOCKET;
            default -> {
                log.warn("未知的 WireApi 类型: {}, 使用默认值 WIRE_API_CHAT", wireApi);
                yield WireApi.WIRE_API_CHAT;
            }
        };
    }

    /**
     * 解析审批策略 (String → Enum)
     *
     * <p>adapter.proto 枚举值：</p>
     * <ul>
     *   <li>APPROVAL_POLICY_UNSPECIFIED (0)</li>
     *   <li>ALWAYS (1) - 总是需要审批</li>
     *   <li>NEVER (2) - 从不需要审批</li>
     *   <li>UNLESS_TRUSTED (3) - 除非受信任</li>
     * </ul>
     */
    private static ApprovalPolicy parseApprovalPolicy(String policy) {
        if (policy == null || policy.isEmpty()) {
            return ApprovalPolicy.NEVER; // 默认自动执行
        }

        return switch (policy.toUpperCase()) {
            case "ALWAYS", "MANUAL_APPROVE", "MANUAL" -> ApprovalPolicy.ALWAYS;
            case "NEVER", "AUTO_APPROVE", "AUTO" -> ApprovalPolicy.NEVER;
            case "UNLESS_TRUSTED" -> ApprovalPolicy.UNLESS_TRUSTED;
            default -> {
                log.warn("未知的审批策略: {}, 使用默认值 NEVER", policy);
                yield ApprovalPolicy.NEVER;
            }
        };
    }

    /**
     * 解析沙箱策略 (String → Enum)
     *
     * <p>adapter.proto 枚举值：</p>
     * <ul>
     *   <li>SANDBOX_POLICY_UNSPECIFIED (0)</li>
     *   <li>WORKSPACE_WRITE (1) - 仅允许写入工作区</li>
     *   <li>READ_ONLY (2) - 只读模式</li>
     *   <li>DANGER_FULL_ACCESS (3) - 完全访问权限</li>
     * </ul>
     */
    private static SandboxPolicy parseSandboxPolicy(String policy) {
        if (policy == null || policy.isEmpty()) {
            return SandboxPolicy.WORKSPACE_WRITE;
        }

        return switch (policy.toUpperCase()) {
            case "READ_ONLY" -> SandboxPolicy.READ_ONLY;
            case "WORKSPACE_WRITE", "SANDBOXED", "SANDBOX" -> SandboxPolicy.WORKSPACE_WRITE;
            case "DANGER_FULL_ACCESS", "INSECURE", "FULL_ACCESS" -> SandboxPolicy.DANGER_FULL_ACCESS;
            default -> {
                log.warn("未知的沙箱策略: {}, 使用默认值 WORKSPACE_WRITE", policy);
                yield SandboxPolicy.WORKSPACE_WRITE;
            }
        };
    }

    // ============================================================
    // 配置优化 - Configuration Optimization  
    // ============================================================

    /**
     * 创建仅模型配置（不含指令，避免codex使用默认人设）
     *
     * <h3>🎯 关键问题</h3>
     * <p>如果第二轮对话时仍然传递指令配置，codex-agent会：</p>
     * <ol>
     *   <li>从history_rollout恢复历史（包含指令）</li>
     *   <li>再添加新的initial_context（包含传入的指令）← 重复！</li>
     *   <li>由于TurnContext重建，base_instructions_override为空</li>
     *   <li>LLM调用时降级使用 model.base_instructions（"codex cli"默认人设）</li>
     * </ol>
     *
     * <h3>✅ 解决方案</h3>
     * <p>有历史记录时，不传指令配置：</p>
     * <ul>
     *   <li>codex-agent只从rollout恢复，不添加新的initial_context</li>
     *   <li>TurnContext中保留rollout的base_instructions_override</li>
     *   <li>LLM调用时使用rollout中的指令，不会降级到默认人设</li>
     * </ul>
     *
     * @param entity Agent配置实体
     * @return SessionConfig Proto实例（仅模型配置，不含指令）
     */
    public static SessionConfig toModelOnlyConfig(AgentConfigEntity entity) {
        if (entity == null) {
            log.warn("AgentConfigEntity 为 null,返回空的 SessionConfig");
            return SessionConfig.getDefaultInstance();
        }

        SessionConfig.Builder builder = SessionConfig.newBuilder();

        // 1. 模型配置（必须保留，让codex知道这不是默认配置）
        if (entity.getLlmModel() != null) {
            builder.setModel(entity.getLlmModel());
        }
        if (entity.getModelProvider() != null) {
            builder.setModelProvider(entity.getModelProvider());
        }
        if (entity.getProviderInfo() != null) {
            builder.setProviderInfo(toModelProviderInfo(entity.getProviderInfo()));
        }

        // 2. 指令配置（清除 - 让codex从rollout恢复）
        // 不设置 baseInstructions 和 developerInstructions

        // 3. 策略配置（保留）
        if (entity.getApprovalPolicy() != null) {
            builder.setApprovalPolicy(parseApprovalPolicy(entity.getApprovalPolicy()));
        }
        if (entity.getSandboxPolicy() != null) {
            builder.setSandboxPolicy(parseSandboxPolicy(entity.getSandboxPolicy()));
        }

        // 4. 工作目录（保留）
        if (entity.getCwd() != null) {
            builder.setCwd(entity.getCwd());
        }

        // 5. MCP 服务器配置（保留 - 可能动态变化）
        if (entity.getMcpConfigJson() != null && !entity.getMcpConfigJson().isEmpty()) {
            try {
                parseMcpServers(entity.getMcpConfigJson(), builder);
            } catch (Exception e) {
                log.error("解析 MCP 配置失败: {}", e.getMessage(), e);
            }
        }

        SessionConfig config = builder.build();
        log.info("AgentConfigEntity 转换为 SessionConfig（仅模型模式）: model={}, provider={}, instructions=跳过",
                config.getModel(), config.getModelProvider());

        return config;
    }
}
