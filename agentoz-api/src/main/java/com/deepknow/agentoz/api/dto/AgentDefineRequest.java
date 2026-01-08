package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 定义/创建 Agent 请求（重构版）
 *
 * <p>基于新的双实体架构，支持配置复用和简化创建。</p>
 *
 * <h3>🔄 两种创建方式</h3>
 * <ul>
 *   <li><b>方式1: 复用已有配置</b> - 指定 {@code configId}</li>
 *   <li><b>方式2: 新建配置</b> - 提供 {@code AgentConfig}</li>
 * </ul>
 *
 * <h3>📊 使用示例</h3>
 * <pre>
 * // 方式1: 复用配置
 * AgentDefineRequest request = new AgentDefineRequest();
 * request.setConversationId("conv-123");
 * request.setAgentName("代码助手");
 * request.setAgentType("coder");
 * request.setConfigId("cfg-qwen-max"); // 复用已有配置
 *
 * // 方式2: 新建配置
 * AgentDefineRequest request = new AgentDefineRequest();
 * request.setConversationId("conv-123");
 * request.setAgentName("数据分析");
 * request.setAgentType("analyst");
 * request.setConfig(new AgentConfigDTO()); // 新建配置
 * </pre>
 */
@Data
public class AgentDefineRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 所属会话 ID（已重命名为conversationId）
     */
    private String conversationId;

    /**
     * Agent显示名称
     * 示例: "代码助手", "数据分析专家"
     */
    private String agentName;

    /**
     * Agent类型/角色
     * 示例: "coder", "analyst", "reviewer"
     */
    private String agentType;

    /**
     * Agent描述（可选）
     */
    private String description;

    // ============================================================
    // 配置指定（二选一）
    // ============================================================

    /**
     * 方式1: 复用已有配置ID
     *
     * <p>如果指定此字段，则忽略 {@code config} 字段，直接使用已有配置。</p>
     */
    private String configId;

    /**
     * 方式2: 新建配置
     *
     * <p>如果 {@code configId} 为空，则使用此配置创建新的 AgentConfigEntity。</p>
     */
    private AgentConfigDTO config;

    /**
     * 优先级（可选）
     * 范围: 1-10，数字越大优先级越高
     * 默认: 5
     */
    private Integer priority;
}
