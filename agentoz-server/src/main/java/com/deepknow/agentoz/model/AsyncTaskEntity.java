package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import com.deepknow.agentoz.enums.AsyncTaskStatus;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异步任务实体
 *
 * <p>用于 AsyncCallAgent 的任务持久化</p>
 *
 * <h3>📊 核心字段</h3>
 * <ul>
 *   <li>taskId - 任务唯一标识（UUID）</li>
 *   <li>agentId - 被调用的 Agent ID</li>
 *   <li>conversationId - 所属会话 ID</li>
 *   <li>taskDescription - 任务描述</li>
 *   <li>status - 任务状态（见 {@link AsyncTaskStatus}）</li>
 *   <li>result - 任务执行结果</li>
 *   <li>priority - 优先级（high/normal/low）</li>
 * </ul>
 *
 * @see com.deepknow.agentoz.mcp.tool.AsyncCallAgentTool
 * @see AsyncTaskStatus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("async_tasks")
public class AsyncTaskEntity {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务唯一标识（UUID）
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 被调用的 Agent ID
     */
    @TableField("agent_id")
    private String agentId;

    /**
     * 被调用的 Agent 名称
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * 所属会话 ID
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 调用者 Agent ID（发起调用的 Agent）
     */
    @TableField("caller_agent_id")
    private String callerAgentId;

    /**
     * 任务描述
     */
    @TableField("task_description")
    private String taskDescription;

    /**
     * 任务状态
     */
    @TableField("status")
    private AsyncTaskStatus status;

    /**
     * 任务优先级（high/normal/low）
     */
    @TableField("priority")
    private String priority;

    /**
     * 任务执行结果（TEXT 类型，存储完整结果）
     */
    @TableField("result")
    private String result;

    /**
     * 错误信息（如果失败）
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 开始执行时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 队列位置（仅当 status=QUEUED 时有效）
     */
    @TableField(exist = false)
    private Integer queuePosition;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
