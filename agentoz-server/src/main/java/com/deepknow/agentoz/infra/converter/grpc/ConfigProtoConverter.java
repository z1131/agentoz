package com.deepknow.agentoz.infra.converter.grpc;

import com.deepknow.agentoz.api.dto.McpServerConfigDTO;
import com.deepknow.agentoz.dto.config.McpServerConfigVO;
import com.deepknow.agentoz.dto.config.ModelOverridesVO;
import com.deepknow.agentoz.dto.config.ProviderConfigVO;
import com.deepknow.agentoz.dto.config.SessionSourceVO;
import codex.agent.*;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 实体到Proto的转换器
 *
 * <p>负责将AgentOZ的实体类转换为Codex-Agent的Proto强类型定义。</p>
 *
 * <h3>🔄 转换映射</h3>
 * <pre>
 * AgentConfigEntity          →  SessionConfig (Proto)
 *   ├─ provider              →    ProviderConfig
 *   ├─ approvalPolicy (String) → ApprovalPolicy (Enum)
 *   ├─ reasoningEffort (String) → ReasoningEffort (Enum)
 *   └─ mcpServers (Map)       →    map&lt;string, McpServerConfig&gt;
 * </pre>
 *
 * @see AgentConfigEntity
 * @see codex.agent.SessionConfig
 */
@Slf4j
public class ConfigProtoConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将AgentConfigEntity转换为SessionConfig (Proto)
     *
     * @param entity Agent配置实体
     * @return SessionConfig Proto实例
     */
    public static SessionConfig toSessionConfig(AgentConfigEntity entity) {
        if (entity == null) {
            log.warn("AgentConfigEntity 为 null,返回空的 SessionConfig");
            return SessionConfig.getDefaultInstance();
        }

        SessionConfig.Builder builder = SessionConfig.newBuilder();

        // 1. 基础环境配置
        if (entity.getProvider() != null) {
            builder.setProvider(toProviderConfig(entity.getProvider()));
        }
        if (entity.getLlmModel() != null) {
            builder.setModel(entity.getLlmModel());
        }
        if (entity.getCwd() != null) {
            builder.setCwd(entity.getCwd());
        }

        // 2. 策略配置 (枚举转换)
        if (entity.getApprovalPolicy() != null) {
            builder.setApprovalPolicy(parseApprovalPolicy(entity.getApprovalPolicy()));
        }
        if (entity.getSandboxPolicy() != null) {
            builder.setSandboxPolicy(parseSandboxPolicy(entity.getSandboxPolicy()));
        }

        // 3. 指令配置
        if (entity.getDeveloperInstructions() != null) {
            builder.setDeveloperInstructions(entity.getDeveloperInstructions());
        }
        if (entity.getUserInstructions() != null) {
            builder.setUserInstructions(entity.getUserInstructions());
        }
        if (entity.getBaseInstructions() != null) {
            builder.setBaseInstructions(entity.getBaseInstructions());
        }

        // 4. 推理配置 (枚举转换)
        if (entity.getReasoningEffort() != null) {
            builder.setModelReasoningEffort(parseReasoningEffort(entity.getReasoningEffort()));
        }
        if (entity.getReasoningSummary() != null) {
            builder.setModelReasoningSummary(parseReasoningSummary(entity.getReasoningSummary()));
        }

        // 5. 提示词优化
        if (entity.getCompactPrompt() != null) {
            builder.setCompactPrompt(entity.getCompactPrompt());
        }

        // 6. 模型能力覆盖
        if (entity.getModelOverrides() != null) {
            builder.setModelOverrides(toModelOverrides(entity.getModelOverrides()));
        }

        // 7. MCP服务器配置 (优先使用 mcpConfigJson)
        if (entity.getMcpConfigJson() != null && !entity.getMcpConfigJson().isEmpty()) {
            try {
                JsonNode rootNode = objectMapper.readTree(entity.getMcpConfigJson());
                // 兼容逻辑：如果 JSON 包含 "mcpServers" 根节点，则进入该节点解析
                JsonNode configNode = rootNode.has("mcpServers") ? rootNode.get("mcpServers") : rootNode;
                
                Map<String, McpServerConfigDTO> mcpDtoMap = objectMapper.convertValue(
                        configNode,
                        new TypeReference<Map<String, McpServerConfigDTO>>() {}
                );
                
                mcpDtoMap.forEach((name, config) -> {
                    McpServerConfig mcpProto = toMcpServerFromDto(config);
                    builder.putMcpServers(name, mcpProto);
                });
                log.info("从JSON解析MCP配置成功: count={}", mcpDtoMap.size());
            } catch (Exception e) {
                log.error("解析MCP JSON配置失败: {}", entity.getMcpConfigJson(), e);
            }
        } 
        
        // 回退逻辑：如果 map 为空且 mcpServers 字段有值，则使用旧字段
        if (builder.getMcpServersCount() == 0 && entity.getMcpServers() != null && !entity.getMcpServers().isEmpty()) {
            entity.getMcpServers().forEach((name, config) -> {
                McpServerConfig mcpProto = toMcpServerConfig(config);
                builder.putMcpServers(name, mcpProto);
            });
        }

        // 8. 会话来源
        if (entity.getSessionSource() != null) {
            builder.setSessionSource(toSessionSource(entity.getSessionSource()));
        }

        SessionConfig config = builder.build();
        log.debug("AgentConfigEntity 转换为 SessionConfig: model={}, approvalPolicy={}",
                config.getModel(), config.getApprovalPolicy());

        return config;
    }

    /**
     * 转换ProviderConfig
     */
    private static ProviderConfig toProviderConfig(ProviderConfigVO apiProvider) {
        if (apiProvider == null) {
            return ProviderConfig.getDefaultInstance();
        }

        ProviderConfig.Builder builder = ProviderConfig.newBuilder()
                .setName(apiProvider.getName());

        if (apiProvider.getBaseUrl() != null) {
            builder.setBaseUrl(apiProvider.getBaseUrl());
        }
        if (apiProvider.getApiKey() != null) {
            builder.setApiKey(apiProvider.getApiKey());
        }
        if (apiProvider.getWireApi() != null) {
            builder.setWireApi(apiProvider.getWireApi());
        }

        return builder.build();
    }

    /**
     * 转换McpServerConfig (从 VO)
     */
    private static McpServerConfig toMcpServerConfig(McpServerConfigVO apiConfig) {
        if (apiConfig == null) {
            return McpServerConfig.getDefaultInstance();
        }

        McpServerConfig.Builder builder = McpServerConfig.newBuilder()
                .setCommand(apiConfig.getCommand());

        if (apiConfig.getArgs() != null) {
            builder.addAllArgs(apiConfig.getArgs());
        }
        if (apiConfig.getEnv() != null) {
            builder.putAllEnv(apiConfig.getEnv());
        }

        return builder.build();
    }

    /**
     * 转换McpServerConfig (从 DTO)
     */
    private static McpServerConfig toMcpServerFromDto(McpServerConfigDTO apiConfig) {
        if (apiConfig == null) {
            return McpServerConfig.getDefaultInstance();
        }

        McpServerConfig.Builder builder = McpServerConfig.newBuilder()
                .setCommand(apiConfig.getCommand());

        if (apiConfig.getArgs() != null) {
            builder.addAllArgs(apiConfig.getArgs());
        }
        if (apiConfig.getEnv() != null) {
            builder.putAllEnv(apiConfig.getEnv());
        }

        return builder.build();
    }

    /**
     * 转换ModelOverrides
     */
    private static ModelOverrides toModelOverrides(ModelOverridesVO apiModelOverrides) {
        if (apiModelOverrides == null) {
            return ModelOverrides.getDefaultInstance();
        }

        ModelOverrides.Builder builder = ModelOverrides.newBuilder();

        if (apiModelOverrides.getShellType() != null) {
            builder.setShellType(apiModelOverrides.getShellType());
        }
        if (apiModelOverrides.getSupportsParallelToolCalls() != null) {
            builder.setSupportsParallelToolCalls(apiModelOverrides.getSupportsParallelToolCalls());
        }
        if (apiModelOverrides.getApplyPatchToolType() != null) {
            builder.setApplyPatchToolType(apiModelOverrides.getApplyPatchToolType());
        }
        if (apiModelOverrides.getContextWindow() != null) {
            builder.setContextWindow(apiModelOverrides.getContextWindow());
        }
        if (apiModelOverrides.getAutoCompactTokenLimit() != null) {
            builder.setAutoCompactTokenLimit(apiModelOverrides.getAutoCompactTokenLimit());
        }

        return builder.build();
    }

    /**
     * 转换SessionSource
     */
    private static SessionSource toSessionSource(SessionSourceVO apiSessionSource) {
        if (apiSessionSource == null) {
            return SessionSource.getDefaultInstance();
        }

        SessionSource.Builder builder = SessionSource.newBuilder()
                .setSourceType(apiSessionSource.getSourceType());

        if (apiSessionSource.getIntegrationName() != null) {
            builder.setIntegrationName(apiSessionSource.getIntegrationName());
        }
        if (apiSessionSource.getIntegrationVersion() != null) {
            builder.setIntegrationVersion(apiSessionSource.getIntegrationVersion());
        }

        return builder.build();
    }

    // ============================================================
    // 枚举转换方法
    // ============================================================

    /**
     * 解析审批策略 (String → Enum)
     */
    private static ApprovalPolicy parseApprovalPolicy(String policy) {
        if (policy == null || policy.isEmpty()) {
            return ApprovalPolicy.AUTO_APPROVE;
        }

        return switch (policy.toUpperCase()) {
            case "AUTO_APPROVE", "AUTO" -> ApprovalPolicy.AUTO_APPROVE;
            case "MANUAL_APPROVE", "MANUAL" -> ApprovalPolicy.MANUAL_APPROVE;
            case "BLOCK_ALL", "BLOCKED" -> ApprovalPolicy.BLOCK_ALL;
            default -> {
                log.warn("未知的审批策略: {}, 使用默认值 AUTO_APPROVE", policy);
                yield ApprovalPolicy.AUTO_APPROVE;
            }
        };
    }

    /**
     * 解析沙箱策略 (String → Enum)
     */
    private static SandboxPolicy parseSandboxPolicy(String policy) {
        if (policy == null || policy.isEmpty()) {
            return SandboxPolicy.SANDBOXED;
        }

        return switch (policy.toUpperCase()) {
            case "READ_ONLY" -> SandboxPolicy.READ_ONLY;
            case "SANDBOXED", "SANDBOX" -> SandboxPolicy.SANDBOXED;
            case "INSECURE" -> SandboxPolicy.INSECURE;
            default -> {
                log.warn("未知的沙箱策略: {}, 使用默认值 SANDBOXED", policy);
                yield SandboxPolicy.SANDBOXED;
            }
        };
    }

    /**
     * 解析推理强度 (String → Enum)
     */
    private static ReasoningEffort parseReasoningEffort(String effort) {
        if (effort == null || effort.isEmpty()) {
            return ReasoningEffort.REASONING_NONE;
        }

        return switch (effort.toUpperCase()) {
            case "REASONING_NONE", "NONE" -> ReasoningEffort.REASONING_NONE;
            case "MINIMAL" -> ReasoningEffort.MINIMAL;
            case "LOW" -> ReasoningEffort.LOW;
            case "MEDIUM" -> ReasoningEffort.MEDIUM;
            case "HIGH" -> ReasoningEffort.HIGH;
            default -> {
                log.warn("未知的推理强度: {}, 使用默认值 REASONING_NONE", effort);
                yield ReasoningEffort.REASONING_NONE;
            }
        };
    }

    /**
     * 解析推理摘要模式 (String → Enum)
     */
    private static ReasoningSummary parseReasoningSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return ReasoningSummary.AUTO;
        }

        return switch (summary.toUpperCase()) {
            case "AUTO" -> ReasoningSummary.AUTO;
            case "CONCISE" -> ReasoningSummary.CONCISE;
            case "DETAILED" -> ReasoningSummary.DETAILED;
            case "REASONING_SUMMARY_NONE", "NONE" -> ReasoningSummary.REASONING_SUMMARY_NONE;
            default -> {
                log.warn("未知的推理摘要模式: {}, 使用默认值 AUTO", summary);
                yield ReasoningSummary.AUTO;
            }
        };
    }
}