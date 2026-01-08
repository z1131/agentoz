package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 创建会话请求（已重命名为Conversation）
 *
 * <p>用于创建一个新的对话会话。</p>
 */
@Data
public class ConversationCreateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 业务线/应用编码
     */
    private String businessCode;

    /**
     * 会话标题 (可选)
     */
    private String title;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;

    /**
     * 主智能体定义 (必填)
     * 会话创建时自动初始化的核心智能体
     */
    private AgentDefineRequest primaryAgent;

    /**
     * 辅助智能体列表 (选填)
     */
    private List<AgentDefineRequest> subAgents;
}
