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
     *
     * <p>存储该会话的完整历史，包含：</p>
     * <ul>
     *   <li>所有用户输入消息 (MessageItem with role=user)</li>
     *   <li>所有 Agent 响应消息 (MessageItem with role=assistant)</li>
     *   <li>所有函数调用记录 (FunctionCallItem)</li>
     *   <li>所有函数返回结果 (FunctionCallOutputItem)</li>
     * </ul>
     *
     * <p>格式：JSON 数组，每个元素是一个 HistoryItem</p>
     * <pre>
     * [
     *   {"message": {"role": "user", "content": [{"text": "帮我查天气"}]}},
     *   {"function_call": {"call_id": "call_123", "name": "get_weather", "arguments": "{...}"}},
     *   {"function_call_output": {"call_id": "call_123", "output": "{...}"}},
     *   {"message": {"role": "assistant", "content": [{"text": "北京今天晴天"}]}}
     * ]
     * </pre>
     *
     * <p>更新策略：每次有新的用户输入或 Agent 返回时追加</p>
     */
    private String historyContext;

    /**
     * 历史格式版本
     *
     * <p>用于标识 historyContext 的数据格式版本，便于未来升级迁移</p>
     */
    private String historyFormat;

    /**
     * 历史消息总数
     *
     * <p>用于快速判断上下文长度，避免频繁解析 JSON</p>
     */
    private Integer messageCount;

    /**
     * 最后一条消息内容
     *
     * <p>用于会话列表展示，只保留最后一条消息的纯文本内容</p>
     */
    private String lastMessageContent;

    /**
     * 最后一条消息类型
     *
     * <p>可能的值: message, function_call, function_call_output</p>
     */
    private String lastMessageType;

    /**
     * 最后一条消息的时间戳
     */
    private LocalDateTime lastMessageAt;

    /**
     * 扩展元数据 (JSON)
     */
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
}
