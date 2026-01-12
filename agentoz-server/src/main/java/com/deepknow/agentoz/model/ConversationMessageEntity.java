package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息记录实体
 *
 * <p>存储单条会话消息，替代原有的 JSON 大字段存储。</p>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversation_messages")
public class ConversationMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话ID
     */
    private String conversationId;

    /**
     * 角色 (user / assistant / tool)
     */
    private String role;

    /**
     * 发送者名称 (如 "PaperSearcher", "user")
     */
    private String senderName;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 元数据 (JSON格式，存储工具调用详情等)
     */
    private String metaData;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
