package com.deepknow.agentozoz.model;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 用户消息实体
 *
 * 业务语义：
 * - 用户可见的消息（用于前端展示）
 * - 不等同于 Agent.context（包含工具调用等内部细节）
 */
@Data
@TableName("user_messages")
public class UserMessageEntity {
    private static final Logger log = LoggerFactory.getLogger(UserMessageEntity.class);


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息ID（业务主键）
     */
    @TableField("message_id")
    private String messageId;

    /**
     * 所属会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 关联的 Agent ID
     * 说明：标识这条消息是哪个 Agent 产生的
     */
    @TableField("agent_id")
    private String agentId;

    /**
     * 角色
     * 枚举：user, assistant, system
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 是否对用户可见
     */
    @TableField("is_visible")
    private Boolean isVisible;

    /**
     * 序号（在会话中的顺序）
     */
    @TableField("sequence")
    private Integer sequence;

    /**
     * Token 数
     */
    @TableField("tokens")
    private Integer tokens;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

}
