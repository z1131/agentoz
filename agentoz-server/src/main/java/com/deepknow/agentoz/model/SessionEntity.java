package com.deepknow.agentozoz.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sessions")
public class SessionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

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
