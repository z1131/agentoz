package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent业务实体（轻量级）
 *
 * <p>代表一个智能体实例，专注于业务属性和状态管理。
 * 计算配置通过 {@code configId} 关联到 {@link AgentConfigEntity}。</p>
 *
 * <h3>🔄 设计思想</h3>
 * <ul>
 *   <li><b>职责分离</b>: AgentEntity负责业务属性，AgentConfigEntity负责计算配置</li>
 *   <li><b>配置复用</b>: 多个Agent可以共享同一套配置（如同一套Qwen-Max配置）</li>
 *   <li><b>灵活扩展</b>: 配置变更不需要修改Agent表结构</li>
 * </ul>
 *
 * <h3>📊 核心字段</h3>
 * <ul>
 *   <li>agentId - Agent唯一标识</li>
 *   <li>conversationId - 所属会话ID（对齐Conversation）</li>
 *   <li>configId - 关联的配置ID（指向AgentConfigEntity）</li>
 *   <li>agentName - Agent显示名称</li>
 * </ul>
 *
 * <h3>🎯 与其他实体的关系</h3>
 * <pre>
 * ConversationEntity (会话)
 *   ├─ primaryAgentId → AgentEntity (主Agent)
 *   └─ 1:N → AgentEntity (参与会话的多个Agent)
 *                      ├─ configId → AgentConfigEntity (配置)
 *                      └─ state → 运行时状态
 * </pre>
 *
 * @see AgentConfigEntity
 * @see ConversationEntity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agents")
public class AgentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent唯一标识
     * 格式: "agent-{timestamp}-{random}"
     */
    private String agentId;

    /**
     * 所属会话ID（对齐ConversationEntity）
     */
    private String conversationId;

    /**
     * 关联的配置ID（指向AgentConfigEntity）
     * 通过此字段获取完整的计算配置
     */
    private String configId;

    // ============================================================
    // 业务属性 - Business Attributes
    // ============================================================

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
     * Agent描述
     */
    private String description;

    // ============================================================
    // 上下文管理 - Context Management
    // ============================================================

    /**
     * 全量历史记录（JSON格式）
     * 包含该Agent参与的所有对话历史
     */
    private String fullHistory;

    /**
     * 活跃上下文（JSON格式）
     * 当前对话窗口的上下文摘要
     */
    private String activeContext;

    // ============================================================
    // 状态与生命周期 - State & Lifecycle
    // ============================================================

    /**
     * Agent运行状态
     * 枚举: "IDLE", "RUNNING", "PAUSED", "ERROR", "TERMINATED"
     */
    private String state;

    /**
     * 优先级（用于多Agent调度）
     * 范围: 1-10，数字越大优先级越高
     */
    private Integer priority;

    /**
     * 扩展元数据（JSON格式）
     * 用于存储未预定义的扩展字段
     */
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;

    /**
     * 创建者用户ID
     */
    private String createdBy;
}