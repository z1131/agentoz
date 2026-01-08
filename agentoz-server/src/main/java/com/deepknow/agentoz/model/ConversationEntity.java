package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体 (已重命名为 Conversation)
 *
 * <p>代表一次完整的用户对话会话，一个会话可以有多个Agent参与协作。</p>
 *
 * <h3>📊 核心字段</h3>
 * <ul>
 *   <li>conversationId - 会话唯一标识（对齐Codex-Agent的conversation_id）</li>
 *   <li>primaryAgentId - 主智能体ID</li>
 *   <li>fullHistoryContext - 会话级历史上下文（包含所有Agent的协作记录）</li>
 * </ul>
 *
 * @see AgentEntity
 * @see AgentConfigEntity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversations")
public class ConversationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话唯一标识（对齐Codex-Agent的conversation_id）
     */
    private String conversationId;

    private String userId;

    /**
     * 业务线/应用编码
     */
    private String businessCode;

    private String title;

    /**
     * 该会话关联的主智能体ID
     */
    private String primaryAgentId;

    /**
     * 状态: ACTIVE, CLOSED
     */
    private String status;

    /**
     * 会话级的全量历史上下文 (JSON)
     * 用于记录整个会话的演进过程（可能包含多个 Agent 的协作）
     */
    private String fullHistoryContext;

    /**
     * 扩展元数据 (JSON)
     */
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
}
