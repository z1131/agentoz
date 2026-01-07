package com.deepknow.agentozoz.model;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.deepknow.agentozoz.dto.codex.ModelProviderInfo;
import com.deepknow.agentozoz.dto.codex.McpServerConfig;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 实体模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "agents", autoResultMap = true)
public class AgentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentId;
    private String sessionId;
    private String agentName;
    private String agentType;

    // ==================== 强类型计算配置 ====================

    @TableField(typeHandler = JacksonTypeHandler.class)
    private ModelProviderInfo provider;

    private String model;

    /**
     * 开发者指令 (持久化存储)
     */
    private String developerInstructions;

    /**
     * 用户指令 (持久化存储)
     */
    private String userInstructions;

    /**
     * MCP 工具配置映射
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, McpServerConfig> mcpConfig;

    // ==================== 上下文双维度分离 ====================

    private String fullHistory;
    private String activeContext;

    // ==================== 状态与生命周期 ====================

    private String state;
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;
}